(ns cia.events-test
  (:require [cia.events :as c :refer :all]
            [clojure.test :as t :refer :all]
            [clojure.core.async :refer [poll! chan tap <!!]]))

(deftest test-send-event
  "Tests the basic action of sending an event"
  (let [{b :chan-buf c :chan m :mult :as ec} (new-event-channel)
        output (chan)]
    (tap m output)
    (send-create-event ec "tester" {} "TestModelType" {:data 1})
    (is (= 0 (count b))) ;; the output channel buffers 1
    (send-create-event ec "tester" {} "TestModelType" {:data 2})
    (is (= 1 (count b)))
    (is (thrown? AssertionError
                 (send-event ec {:http-params {}})))
    (is (= 1 (count b)))
    (send-event ec {:owner "tester" :http-params {} :data 3})
    (is (= 2 (count b)))
    (is (= 1 (-> (<!! output) :model :data)))
    (is (= 2 (-> (<!! output) :model :data)))
    (is (= 3 (-> (<!! output) :data)))))

(deftest test-central-events
  "Tests the basic action of sending an event to the central channel"
  (init!)
  (let [{b :chan-buf c :chan m :mult} @central-channel
        output (chan)]
    (tap m output)
    (send-create-event "tester" {} "TestModelType" {:data 1})
    (is (= 0 (count b))) ;; the output channel buffers 1
    (send-create-event "tester" {} "TestModelType" {:data 2})
    (is (= 1 (count b)))
    (is (thrown? AssertionError
                 (send-event {:http-params {}})))
    (is (= 1 (count b)))
    (send-event {:owner "tester" :http-params {} :data 3})
    (is (= 2 (count b)))
    (is (= 1 (-> (<!! output) :model :data)))
    (is (= 2 (-> (<!! output) :model :data)))
    (is (= 3 (-> (<!! output) :data)))))

(deftest test-updated-model
  "Tests the update-model function"
  (init!)
  (let [{b :chan-buf c :chan m :mult} @central-channel
        output (chan)]
    (tap m output)
    (send-updated-model "tester" {"User-Agent" "clojure"} [["f1" "delete" "x"]["f2" "assert" "y"]])
    (is (= "f1" (-> (<!! output) :fields ffirst)))))

(deftest test-deleted-model
  "Tests the deleted-model function"
  (init!)
  (let [{b :chan-buf c :chan m :mult} @central-channel
        output (chan)]
    (tap m output)
    (send-deleted-model "tester" {"User-Agent" "clojure"} 42)
    (is (= 42 (-> (<!! output) :id)))))

(deftest test-verdict-change
  "Tests the verdict change function"
  (init!)
  (let [{b :chan-buf c :chan m :mult} @central-channel
        output (chan)]
    (tap m output)
    (send-verdict-change "tester" {"User-Agent" "clojure"} 7 {:disposition 2})
    (let [v (<!! output)]
      (is (= 7 (-> v :judgement_id)))
      (is (= 2 (-> v :verdict :disposition))))))

(deftest test-recents
  "Tests that the sliding window works, and is repeatable"
  (binding [*event-buffer-size* 3]
    (init!)
    (send-create-event "tester" {} "TestModelType" {:data 1})
    (send-create-event "tester" {} "TestModelType" {:data 2})
    (send-create-event "tester" {} "TestModelType" {:data 3})
    (is (= [1 2 3] (map (comp :data :model) (recent-events))))
    (send-create-event "tester" {} "TestModelType" {:data 4})
    (send-create-event "tester" {} "TestModelType" {:data 5})
    (send-create-event "tester" {} "TestModelType" {:data 6})
    (send-create-event "tester" {} "TestModelType" {:data 7})
    (Thread/sleep 100)
    (is (= [5 6 7] (map (comp :data :model) (recent-events))))))
