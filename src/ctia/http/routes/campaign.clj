(ns ctia.http.routes.campaign
  (:require [schema.core :as s]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ctia.flows.crud :as flows]
            [ctia.schemas.campaign :refer [NewCampaign StoredCampaign realize-campaign]]
            [ctia.store :refer :all]))

(defroutes campaign-routes
  (context "/campaign" []
    :tags ["Campaign"]
    (POST "/" []
      :return StoredCampaign
      :body [campaign NewCampaign {:description "a new campaign"}]
      :summary "Adds a new Campaign"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:create-campaign :admin}
      :login login
      (ok (flows/create-flow :realize-fn realize-campaign
                             :store-fn #(create-campaign @campaign-store %)
                             :object-type :campaign
                             :login login
                             :object campaign)))
    (PUT "/:id" []
      :return StoredCampaign
      :body [campaign NewCampaign {:description "an updated campaign"}]
      :summary "Updates a Campaign"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:create-campaign :admin}
      :login login
      (ok (flows/update-flow :get-fn #(read-campaign @campaign-store %)
                             :realize-fn realize-campaign
                             :update-fn #(update-campaign @campaign-store (:id %) %)
                             :object-type :campaign
                             :id id
                             :login login
                             :object campaign)))
    (GET "/:id" []
      :return (s/maybe StoredCampaign)
      :summary "Gets a Campaign by ID"
      :path-params [id :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:read-campaign :admin}
      (if-let [d (read-campaign @campaign-store id)]
        (ok d)
        (not-found)))
    (DELETE "/:id" []
      :no-doc true
      :path-params [id :- s/Str]
      :summary "Deletes a Campaign"
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:delete-campaign :admin}
      (if (flows/delete-flow :get-fn #(read-campaign @campaign-store %)
                             :delete-fn #(delete-campaign @campaign-store %)
                             :object-type :campaign
                             :id id)
        (no-content)
        (not-found)))))
