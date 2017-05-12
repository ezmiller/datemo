(ns datemo.db-test
  (:use clojure.test
        datemo.db)
  (:require [datomic.api :as d]))

(require '[clojure.pprint :refer [pprint]])

(defn setup-db [f]
  (init-db true)
  (-> (load-schema "schemas/arb.edn")
      (install-schema (get-conn)))
  (f))

(use-fixtures :each setup-db)

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
