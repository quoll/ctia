(ns ctia.test-properties
  (:require [clojure.test :refer :all]
            [ctia.properties :refer [read-env-vars]]))

(deftest test-environ
  (println (read-env-vars)))

