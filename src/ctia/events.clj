(ns ctia.events
  (:require [ctia.events.schemas :as es]
            [ctia.schemas.judgement :as sj]
            [ctia.lib.time :as time]
            [ctia.schemas.common :as c]
            [ctia.schemas.verdict :as v]
            [ctia.store :refer [create-judgement delete-judgement]]
            [clojure.core.async :as a :refer [go-loop <!]]
            [schema.core :as s])
  (:import [clojure.core.async Mult]
           [clojure.core.async.impl.protocols Channel]
           [clojure.core.async.impl.buffers FixedBuffer]
           [java.util Map]
           [ctia.store IJudgementStore]))

(def shutdown-max-wait-ms (* 1000 60 60))
(def ^:dynamic *event-buffer-size* 1000)

(defonce central-channel (atom nil))

(def ModelEvent (assoc es/ModelEventBase s/Any s/Any))

(s/defschema EventChannel
  "This structure holds a channel, its associated buffer, and a multichan"
  {:chan-buf FixedBuffer
   :chan Channel
   :mult Mult
   :recent Channel})

(s/defn new-event-channel :- EventChannel []
  (let [b (a/buffer *event-buffer-size*)
        c (a/chan b)
        p (a/mult c)
        r (a/chan (a/sliding-buffer *event-buffer-size*))]
    (a/tap p r)
    {:chan-buf b
     :chan c
     :mult p
     :recent r}))

(defn init! []
  (reset! central-channel (new-event-channel)))

(s/defn shutdown-channel :- Long
  "Shuts down a provided event channel."
  [max-wait-ms :- Long
   {:keys [:chan-buf :chan :mult]} :- EventChannel]
  (let [ch (a/chan (a/dropping-buffer 1))]
    (a/tap mult ch)
    (a/close! chan)
    (loop [timeout (a/timeout max-wait-ms)]
      (let [[val _] (a/alts!! [ch timeout] :priority true)]
        (if (some? val)
          (recur timeout)
          (count chan-buf))))))

(s/defn shutdown! :- Long
  "Close the event channel, waiting up to max-wait-ms for the buffer
   to flush.  Returns the number of items in the buffer after
   shutdown (zero on a successful flush).
   Closes the central channel by default."
  ([]
   (shutdown! shutdown-max-wait-ms))
  ([max-wait-ms :- Long]
   (shutdown-channel max-wait-ms @central-channel)))

(s/defn send-event
  "Send an event to a channel. Use the central channel by default"
  ([event]
   (send-event @central-channel event))
  ([{ch :chan} :- EventChannel
    {:keys [owner timestamp http-params] :as event}]
   (assert owner "Events cannot be registered without user info")
   (let [event (if timestamp event (assoc event :timestamp (time/now)))]
     (a/>!! ch event))))

(s/defn send-create-event
  "Builds a creation event and sends it to the provided channel. Use the central channel by default."
  ([owner :- s/Str
    http-params :- c/HttpParams  ; maybe { s/Key s/Any }
    new-model :- {s/Any s/Any}]
   (send-create-event @central-channel owner http-params new-model))
  ([echan :- EventChannel
    owner :- s/Str
    http-params :- c/HttpParams
    new-model :- {s/Any s/Any}]
   (send-event echan {:type es/CreateEventType
                      :owner owner
                      :timestamp (time/now)
                      :http-params http-params
                      :id (or (:id new-model) (gensym "event"))
                      :model new-model})))

(s/defn send-updated-model
  "Builds an updated model event and sends it to the provided channel. Use the central channel by default."
  ([owner :- s/Str
    http-params :- c/HttpParams
    triples :- [es/UpdateTriple]]
   (send-updated-model @central-channel owner http-params triples))
  ([echan :- EventChannel
    owner :- s/Str
    http-params :- c/HttpParams
    triples :- [es/UpdateTriple]]
   (send-event echan {:type es/UpdateEventType
                      :owner owner
                      :timestamp (time/now)
                      :http-params http-params
                      :fields triples})))

(s/defn send-deleted-model
  "Builds a delete event and sends it to the provided channel. Use the central channel by default."
  ([owner :- s/Str
    http-params :- c/HttpParams
    id :- s/Str]
   (send-deleted-model @central-channel owner http-params id))
  ([echan :- EventChannel
    owner :- s/Str
    http-params :- c/HttpParams
    id :- s/Str]
   (send-event echan {:type es/DeleteEventType
                      :owner owner
                      :timestamp (time/now)
                      :id id})))

(s/defn send-verdict-change
  "Builds a verdict change event and sends it to the provided channel. Use the central channel by default."
  ([owner :- s/Str
    http-params :- c/HttpParams
    id :- s/Str
    verdict :- v/Verdict]
   (send-verdict-change @central-channel owner http-params id verdict))
  ([echan :- EventChannel
    owner :- s/Str
    http-params :- c/HttpParams
    id :- s/Str
    verdict :- v/Verdict]
   (send-event echan {:type es/VerdictChangeEventType
                      :owner owner
                      :timestamp (time/now)
                      :judgement_id id
                      :verdict verdict})))

(s/defn ^:private drain :- [ModelEvent]
  "Extract elements from a channel into a lazy-seq.
   Reading the seq reads from the channel."
  [c :- Channel]
  (if-let [x (a/poll! c)]
    (cons x (lazy-seq (drain c)))))

(s/defn recent-events :- [ModelEvent]
  "Returns up to the requested number of the  most recent events.
   Defaults to attempting to get *event-buffer-size* events."
  ([] (recent-events *event-buffer-size*))
  ([n :- Long] (take n (drain (:recent @central-channel)))))



(s/defn register-judgement-store :- Channel
  "creates a GO loop to direct events to a store"
  [store :- IJudgementStore
   {m :mult :as ec} :- EventChannel
   login]
  (go-loop []
    (when-let [event (<! m)]
      (let [{t :type} event]
        (condp = t
          es/CreateEventType (when (= "judgement" (:type (:model event)))
                               (create-judgement store (:model event)))
          es/DeleteEventType (delete-judgement store (:id event))
          nil)
        (recur)))))
  
