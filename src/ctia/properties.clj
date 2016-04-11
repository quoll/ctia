(ns ^{:doc "Properties (aka configuration) of the application.
            Properties are stored in property files.  There is a
            default file which can be overridden by placing an
            alternative properties file on the classpath, or by
            setting system properties."}
    ctia.properties
  (:refer-clojure :exclude [load])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ctia.lib.map :as map]
            [schema.coerce :as c]
            [schema.core :as s]
            [clj-time.coerce :as coerce])
  (:import java.util.Properties))

(def files
  "Property file names, they will be merged, with last one winning"
  ["ctia-default.properties"
   "ctia.properties"
   ])

(defonce properties
  (atom {}))

(s/defschema PropertiesSchema
  "This is the schema used for value type coercion.  It is also used
   for validating the properties that are read in so that required
   properties must be present.  Only the following properties may be
   set.  This is also used for selecting system properties to merge
   with the properties file."
  {(s/required-key "auth.service.type") s/Keyword
   (s/optional-key "auth.service.threatgrid.url") s/Str
   (s/optional-key "ctia.store.default") s/Keyword
   (s/optional-key "ctia.store.sql.db.classname") s/Str
   (s/optional-key "ctia.store.sql.db.subprotocol") s/Str
   (s/optional-key "ctia.store.sql.db.subname") s/Str
   (s/optional-key "ctia.store.sql.db.delimiters") s/Str
   (s/optional-key "ctia.store.es.uri") s/Str
   (s/optional-key "ctia.store.es.host") s/Str
   (s/optional-key "ctia.store.es.port") s/Int
   (s/optional-key "ctia.store.es.clustername") s/Str
   (s/optional-key "ctia.store.es.indexname") s/Str
   (s/optional-key "ctia.producer.es.uri") s/Str
   (s/optional-key "ctia.producer.es.host") s/Str
   (s/optional-key "ctia.producer.es.port") s/Int
   (s/optional-key "ctia.producer.es.clustername") s/Str
   (s/optional-key "ctia.producer.es.indexname") s/Str})

(def configurable-properties
  "String keys from PropertiesSchema, used to select system properties."
  (map #(or (:k %) %) (keys PropertiesSchema)))

(def coerce-properties
  "Fn used to coerce property values using PropertiesSchema"
  (c/coercer! PropertiesSchema
              c/string-coercion-matcher))

(defn- read-property-file []
  (->> files
       (keep (fn [file]
               (when-let [rdr (some-> file io/resource io/reader)]
                 (with-open [rdr rdr]
                   (doto (Properties.)
                     (.load rdr))))))
       concat
       (into {})))

(defn- transform [properties]
  (reduce (fn [accum [k v]]
            (let [parts (->> (str/split k #"\.")
                             (map keyword))]
              parts
              (cond
                (empty? parts) accum
                (= 1 (count parts)) (assoc accum (first parts) v)
                :else (map/rmerge accum
                                  (assoc-in {} parts v)))))
          {}
          properties))

(defn- map-keys
  "Apply a map to all keys in a map, returning a map with the new keys"
  [f m]
  (into {} (map (fn [[k v]] [(f k) v]) m)))

(defn- read-env-vars
  "Read the known variables from the system environment.
  Environment vars use all uppercase, and _ for . characters"
  []
  (let [env-name #(-> % (str/replace #"\." "_") str/upper-case)
        prop-name #(-> % str/lower-case (str/replace #"_" "."))
        env-names (set (map env-name configurable-properties))]
    (->> (System/getenv)
         (filter #(env-names (key %)))
         (map-keys prop-name)
         (into {}))))

(defn init!
  "Read a properties file, merge it with system properties, coerce and
   validate it, transform it into a nested map with keyword keys, and
   then store it in memory."
  []
  (->> (merge (read-property-file)
              (select-keys (System/getProperties)
                           configurable-properties))
       coerce-properties
       transform
       (#(merge % (read-env-vars)))
       (reset! properties)))
