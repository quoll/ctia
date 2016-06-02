(ns ctia.http.routes.bulk
  (:require [compojure.api.sweet :refer :all]
            [clojure.tools.logging :as log]
            [ctia.domain.entities :as ent]
            [ctia.flows.crud :as flows]
            [ctia.lib.keyword :refer [singular]]
            [ctia.schemas.bulk :refer [BulkRefs NewBulk StoredBulk]]
            [ctia.store :refer :all]
            [ctim.schemas.common :as c]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(defn realize
  "return the realize function provided an entity key name"
  [k]
  (condp = k
    :actor          ent/realize-actor
    :campaign       ent/realize-campaign
    :coa            ent/realize-coa
    :exploit-target ent/realize-exploit-target
    :feedback       ent/realize-feedback
    :incident       ent/realize-incident
    :indicator      ent/realize-indicator
    :judgement      ent/realize-judgement
    :sighting       ent/realize-sighting
    :ttp            ent/realize-ttp))

(defn create-fn
  "return the create function provided an entity key name"
  [k]
  (condp = k
    :actor          #(create-actor @actor-store %)
    :campaign       #(create-campaign @campaign-store %)
    :coa            #(create-coa @coa-store %)
    :exploit-target #(create-exploit-target @exploit-target-store %)
    :feedback       #(create-feedback @feedback-store %)
    :incident       #(create-incident @incident-store %)
    :indicator      #(create-indicator @indicator-store %)
    :judgement      #(create-judgement @judgement-store %)
    :sighting       #(create-sighting @sighting-store %)
    :ttp            #(create-ttp @ttp-store %)))

(defn read-fn
  "return the create function provided an entity key name"
  [k]
  (condp = k
    :actor          #(read-actor @actor-store %)
    :campaign       #(read-campaign @campaign-store %)
    :coa            #(read-coa @coa-store %)
    :exploit-target #(read-exploit-target @exploit-target-store %)
    :feedback       #(read-feedback @feedback-store %)
    :incident       #(read-incident @incident-store %)
    :indicator      #(read-indicator @indicator-store %)
    :judgement      #(read-judgement @judgement-store %)
    :sighting       #(read-sighting @sighting-store %)
    :ttp            #(read-ttp @ttp-store %)))

(defn create-entities
  "Create many entities provided their type and returns a list of ids"
  [entities entity-type login]
  (->> entities
       (map #(try (flows/create-flow
                   :entity-type entity-type
                   :realize-fn (realize entity-type)
                   :store-fn (create-fn entity-type)
                   :identity login
                   :entity %)
                  (catch Exception e
                    (do (log/error (pr-str e))
                        nil))))
       (map :id)))

(defn read-entities
  "Retrieve many entities of the same type provided their ids and common type"
  [ids entity-type]
  (let [read-entity (read-fn entity-type)]
    (->> ids
         (map (fn [id] (try (read-entity id)
                            (catch Exception e
                              (do (log/error (pr-str e))
                                  nil))))))))

(defn gen-bulk-from-fn
  "Kind of fmap but adapted for bulk

  ~~~~.clojure
  (gen-bulk-from-fn f {k [v1 ... vn]} args)
  ===> {k (map #(apply f % (singular k) args) [v1 ... vn])}
  ~~~~
  "
  [func bulk & args]
  (reduce (fn [acc entity-type]
            (assoc acc
                   entity-type
                   (apply func
                          (get bulk entity-type)
                          (singular entity-type)
                          args)))
          {}
          (keys bulk)))

(defroutes bulk-routes
  (context "/bulk" []
    :tags ["Bulk"]
    (POST "/" []
      :return BulkRefs
      :body [bulk NewBulk {:description "a new Bulk object"}]
      :header-params [api_key :- (s/maybe s/Str)]
      :summary "Adds a lot of new entities in only one HTTP call"
      :capabilities #{:create-actor
                      :create-campaign
                      :create-coa
                      :create-exploit-target
                      :create-feedback
                      :create-incident
                      :create-indicator
                      :create-judgement
                      :create-sighting
                      :create-ttp}
      :identity login
      (ok (gen-bulk-from-fn create-entities bulk login)))
    (GET "/" []
      :return (s/maybe StoredBulk)
      :summary "Gets many entities at once"
      :query-params [{actors          :- [c/Reference] []}
                     {campaigns       :- [c/Reference] []}
                     {coas            :- [c/Reference] []}
                     {exploit-targets :- [c/Reference] []}
                     {feedbacks       :- [c/Reference] []}
                     {incidents       :- [c/Reference] []}
                     {indicators      :- [c/Reference] []}
                     {judgements      :- [c/Reference] []}
                     {sightings       :- [c/Reference] []}
                     {ttps            :- [c/Reference] []}]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:read-actor
                      :read-campaign
                      :read-coa
                      :read-exploit-target
                      :read-feedback
                      :read-incident
                      :read-indicator
                      :read-judgement
                      :read-sighting
                      :read-ttp}
      (let [bulk (into {} (remove (comp empty? second)
                                  {:actors          actors
                                   :campaigns       campaigns
                                   :coas            coas
                                   :exploit-targets exploit-targets
                                   :feedbacks       feedbacks
                                   :incidents       incidents
                                   :indicators      indicators
                                   :judgements      judgements
                                   :sightings       sightings
                                   :ttps            ttps}))]
        (ok (gen-bulk-from-fn read-entities bulk))))))