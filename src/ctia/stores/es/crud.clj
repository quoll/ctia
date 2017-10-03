(ns ctia.stores.es.crud
  (:require [clj-momo.lib.es
             [document
              :refer [bulk-create-doc
                      delete-doc
                      get-doc
                      search-docs
                      update-doc]]
             [schemas :refer [ESConnState]]]
            [ctia.lib.pagination :refer [list-response-schema]]
            [ctia.domain.access-control
             :refer [allow-read? allow-write?]]
            [ctia.stores.es.query
             :refer [find-restriction-query-part]]
            [ring.swagger.coerce :as sc]
            [schema
             [coerce :as c]
             [core :as s]]))

(defn coerce-to-fn
  [Model]
  (c/coercer! Model sc/json-schema-coercion-matcher))

(defn ensure-document-id
  "Returns a document ID.  if id is a object ID, it extract the
  document ID, if it's a document ID already, it will just return
  that."
  [id]
  (let [[orig docid] (re-matches #".*?([^/]+)\z" id) ]
    docid))

(defn handle-create
  "Generate an ES create handler using some mapping and schema"
  [mapping Model]
  (let [coerce! (coerce-to-fn (s/maybe Model))]
    (s/fn :- (s/maybe [Model])
      [state :- ESConnState
       models :- [Model]
       ident]
      (->> (bulk-create-doc (:conn state)
                            (map #(assoc %
                                         :_id (:id %)
                                         :_index (:index state)
                                         :_type (name mapping))
                                 models)
                            (get-in state [:props :refresh] false))
           (map #(dissoc % :_id :_index :_type))
           (map coerce!)))))

(defn handle-update
  "Generate an ES update handler using some mapping and schema"
  [mapping Model]
  (let [coerce! (coerce-to-fn (s/maybe Model))]
    (s/fn :- (s/maybe Model)
      [state :- ESConnState
       id :- s/Str
       realized :- Model
       ident]
      (let [current-doc
            (get-doc (:conn state)
                     (:index state)
                     (name mapping)
                     (ensure-document-id id))]

        (if (allow-write? current-doc ident)
          (coerce! (update-doc (:conn state)
                               (:index state)
                               (name mapping)
                               (ensure-document-id id)
                               realized
                               (get-in state [:props :refresh] false)))
          (throw (ex-info "You are not allowed to update this document"
                          {:type :access-control-error})))))))

(defn handle-read
  "Generate an ES read handler using some mapping and schema"
  [mapping Model]
  (let [coerce! (coerce-to-fn (s/maybe Model))]
    (s/fn :- (s/maybe Model)
      [state :- ESConnState
       id :- s/Str
       ident]
      (if-let [doc (coerce! (get-doc (:conn state)
                                     (:index state)
                                     (name mapping)
                                     (ensure-document-id id)))]
        (if (allow-read? doc ident)
          doc
          (throw (ex-info "You are not allowed to read this document"
                          {:type :access-control-error})))))))

(defn access-control-filter-list
  "Given an ident, keep only documents it is allowed to read"
  [docs ident]
  (filter #(allow-read? % ident) docs))

(defn handle-delete
  "Generate an ES delete handler using some mapping and schema"
  [mapping Model]
  (s/fn :- s/Bool
    [state :- ESConnState
     id :- s/Str
     ident]
    (if-let [doc (get-doc (:conn state)
                          (:index state)
                          (name mapping)
                          (ensure-document-id id))]
      (if (allow-write? doc ident)
        (delete-doc (:conn state)
                    (:index state)
                    (name mapping)
                    (ensure-document-id id)
                    (get-in state [:props :refresh] false))

        (throw (ex-info "You are not allowed to delete this document"
                        {:type :access-control-error}))))))

(defn handle-find
  "Generate an ES find/list handler using some mapping and schema"
  [mapping Model]
  (let [response-schema (list-response-schema Model)
        coerce! (coerce-to-fn response-schema)]
    (s/fn :- response-schema
      [state :- ESConnState
       filter-map :- {s/Any s/Any}
       ident
       params]
      (update
       (coerce! (search-docs (:conn state)
                             (:index state)
                             (name mapping)
                             (find-restriction-query-part ident)
                             filter-map
                             params))
       :data access-control-filter-list ident))))

(defn handle-query-string-search
  "Generate an ES query string handler using some mapping and schema"
  [mapping Model]
  (let [response-schema (list-response-schema Model)
        coerce! (coerce-to-fn response-schema)]
    (s/fn :- response-schema
      [state :- ESConnState
       query :- s/Str
       filter-map :- (s/maybe {s/Any s/Any})
       ident
       params]
      (update
       (coerce! (search-docs (:conn state)
                             (:index state)
                             (name mapping)
                             {:bool {:must [(find-restriction-query-part ident)
                                            {:query_string {:query query}}]}}
                             filter-map
                             params))
       :data access-control-filter-list ident))))
