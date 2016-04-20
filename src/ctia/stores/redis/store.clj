(ns ctia.stores.redis.store
  "Central setup for redis."
  (:require [taoensso.carmine :as c]
            [ctia.properties :as p]
            [ctia.events :as e]
            [clojure.tools.logging :as log]
            [clojure.core.memoize :as memo]
            [schema.core :as s :refer [=>]])
  (:import [java.net URI]))

(def default-port "The default port to use for Redis when not configured" 6379)

(def default-host "The default address to connect to Redis at" "127.0.0.1")

(defn- host-port*
  "Reads a host/port pair from a properties map"
  [props]
  (let [redis (get-in props [:ctia :store :redis])
        redis-url (if-let [u (:uri redis)] (URI. u))]
        (if redis-url
          [(.getHost redis-url) (.getPort redis-url)]
          [(:host redis) (:port redis)])))

;; cache property configurations to a modest level (rarely need more than 1)
(def host-port (memo/fifo host-port* :fifo/threshold 8))

(defn server-connection
  "Build the server config"
  []
  (let [[host port] (host-port @p/properties)]
    (when (false? (and host port))
      (log/warn "Redis has been de-configured"))
    {:pool {}
     :spec {:host (or host default-host) :port (or default-port)}}))

(defn enabled?
  "Returns true when Redis is currently configured"
  []
  (get-in @p/properties [:ctia :store :redis :enabled]))

(defmacro wcar
  "Provides the context for executing Redis commands, using the configured server."
  [& body] `(let [server-conn# (server-connection)]
              (c/wcar server-conn# ~@body)))

(def event-channel-name "The name of the channel for pub/sub on Redis" "event")

(s/defn publish-fn
  "Callback function that publishes events to Redis."
  [event :- e/Event]
  (when (enabled?)
    (wcar (c/publish event-channel-name event))))

(def pubsub-listener "Central listener for subscribing to Redis." (atom nil))

(defn close!
  "Closes the central Redis subscription listener"
  []
  (when @pubsub-listener
    (c/with-open-listener
      (c/unsubscribe))
    (c/close-listener @pubsub-listener)))

(defn set-listener-fn!
  "Sets the function to be called when events are published into Redis.
   Closes and replaces the previous listening function, if there was one."
  [listener-fn]
  (when (enabled?)
    (close!)
    (reset! pubsub-listener
            (c/with-new-pubsub-listener (:spec (server-connection))
              {event-channel-name listener-fn}
              (c/subscribe event-channel-name)))))
