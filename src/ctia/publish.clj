(ns ctia.publish
  (:require [ctia.events :as e]
            [ctia.stores.redis.store :as redis]
            [clojure.core.async :as a  :refer [>!!]]
            [schema.core :as s :refer [=>]]))

(def publish-channel (e/new-event-channel))

(defn async-listener
  [event]
  (>!! (:chan publish-channel) event))

(defn init!
  "Initializes publishing. Right now, this means Redis."
  []
  (redis/set-listener-fn! async-listener)
  (e/register-listener redis/publish-fn))

(s/defn event-subscribe
  "Registers an event subscription function with Redis"
  [f :- (=> s/Any e/Event)]
  (letfn [(event-fn [[msg-type channel-name event]]
            (when (= "message" msg-type)
              (f event)))]
    (e/register-listener publish-channel
                         event-fn
                         (constantly true)
                         redis/close!)))
