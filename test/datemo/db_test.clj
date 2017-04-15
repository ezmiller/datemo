(ns datemo.db-test
  (:use clojure.test
        datemo.db)
  (:require [datomic.api :as d]))

(require '[clojure.pprint :refer [pprint]])

(deftest db-test
  (testing "testing transact-or-error"
    (let [successful (transact-or-error
                       [{:arb/id (d/squuid)}])
          failed (transact-or-error
                   [{:nonexistent/tag "value"}])]
      (is (= false
             (nil? (first successful))))
      (is (= true
             (nil? (last successful))))
      (is (= true
             (nil? (first failed))))
      (is (= false
             (nil? (last failed)))))))
