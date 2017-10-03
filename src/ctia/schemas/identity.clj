(ns ctia.schemas.identity
  (:require [ctia.auth :refer [all-capabilities]]
            [ctim.schemas.common :as c]
            [schema.core :as s]
            [schema-tools.core :as st]))

(def Capability
  (apply s/enum all-capabilities))

(def Role s/Str)

(def Login s/Str)

(def Group s/Str)

(s/defschema Identity
  {:role Role
   :groups [Group]
   :capabilities #{Capability}
   :login s/Str})
