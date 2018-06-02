(ns datemo.db-test
  (:use clojure.test
        datemo.db))

(require '[clojure.pprint :refer [pprint]])

(defn setup-db [f]
  (let [dbname (str "test-" (str (java.util.UUID/randomUUID)))]
    (init-client)
    (create-db dbname)
    (connect dbname)
    (-> (load-schema "schemas/arb.edn")
        (install-schema (get-conn)))
    (f)
    (destroy-db dbname)))

(use-fixtures :each setup-db)

(deftest db-test
  (testing "testing transact-or-error"
    (let [successful (transact-or-error
                       [{:arb/id (java.util.UUID/randomUUID)}])
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
