(ns ctia.stores.redis.judgement
  (:require [ctia.schemas.common :as c]
            [ctia.schemas.judgement
             :refer [NewJudgement StoredJudgement realize-judgement]]
            [ctia.schemas.relationships :as rel]
            [ctia.schemas.verdict :refer [Verdict]]
            [ctia.store :refer [IJudgementStore list-judgements]]
            [taoensso.carmine :as c]
            [schema.core :as s]))

(def server1-conn {:pool {} :spec {:host "127.0.0.1" :port 6379}})
(defmacro wcar [& body] `(c/wcar server1-conn ~@body))

(defn create-judgement
  [this login {:keys [id] :as new-judgement}]
  (wcar (c/set id new-judgement)))

(defn read-judgement
  [this id]
  (wcar (c/get id)))

(defn delete-judgement [this id]
  (wcar (c/del id)))

(defn list-judgements [this filter-map]
  )

(defn calculate-verdict
  [this observable]
  )

(defn list-judgements-by-observable [this observable]
  )

(defn add-indicator-to-judgement [this judgement-id indicator-relationship])
  )
