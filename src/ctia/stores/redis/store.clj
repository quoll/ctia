(ns ctia.stores.redis.store
  "Central setup for redis"
  (:require [taoensso.carmine :as c]
            [ctia.properties :as p]
            [ctia.events :as e]
            [schema.core :as s :refer [=>]])
  (:import [java.net URL]))

(defonce redis-url
  (if-let [u (get p/properties "ctia.store.redis.uri")]
    (URL. u)))

(def default-port 6379)

(defonce port
  (if redis-url
    (.getPort redis-url)
    (get p/properties "ctia.store.redis.port" default-port)))

(def default-host "127.0.0.1")

(defonce host
  (if redis-url
    (.getHost redis-url)
    (get p/properties "ctia.store.redis.host" default-host)))

(defonce server1-conn {:pool {}
                       :spec {:host host :port port}})

(defmacro wcar [& body] `(c/wcar server1-conn ~@body))

(def event-channel-name "event")

(s/defn publish-fn
  "Callback function that publishes events"
  [event :- e/Event]
  (wcar (c/publish event-channel-name event)))

(def pubsub-listener (atom nil))

(defn close! [] (when @pubsub-listener
                  (c/close-listener @pubsub-listener)))

(defn set-listener-fn!
  [listener-fn]
  (close!)
  (reset! pubsub-listener
          (c/with-new-pubsub-listener (:spec server1-conn)
            {event-channel-name listener-fn}
            (c/subscribe event-channel-name))))
