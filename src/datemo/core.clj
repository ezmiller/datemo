(ns datemo.core
  (:import datomic.Util)
  (:require
    [datomic.api :as d]
    [clojure.java.io :as io]
    [clojure.pprint :as pp :refer (pprint)]))

(def db-uri "datomic:dev://localhost:4334/datemo");
(def conn (d/connect db-uri))
(def db (d/db conn))

(defn read-all
  "Read all forms in f, where f is any resource that can
   be opened by io/reader"
  [f]
  (Util/readAll (io/reader f)))

(defn transact-all
  "Load and run all transactions from f, where f is any
   resource that can be opened by io/reader."
  [conn f]
  (loop [n 0
         [tx & more] (read-all f)]
    (if tx
      (recur (+ n (count (:tx-data  @(d/transact conn tx))))
             more)
      {:datoms n})))

;; (def schema-tx (-> (io/resource "schemas/arb.edn")
;;                    (read-all)
;;                    (first)))
;; (pprint (d/transact conn schema-tx))
