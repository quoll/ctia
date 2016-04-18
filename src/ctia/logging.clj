(ns ctia.logging
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as a :refer [go-loop <! chan tap close!]]
            [ctia.events :as e]
            [schema.core :as s])
  (:import [clojure.core.async.impl.protocols Channel]))

(defn init!
  "Sets up logging of all events"
  []
  (e/register-listener #(log/info "event:" %)))
