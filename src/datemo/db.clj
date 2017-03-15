(ns datemo.db
  (:import datomic.Util)
  (:require
    [datomic.api :as d]
    [clojure.java.io :as io]
    [environ.core :refer [env]]
    [clojure.pprint :as pp :refer (pprint)]))

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

(defn load-schema
  "Given a path string to a schema edn file, loads and returns."
  [path-str]
  (-> (io/resource path-str)
      (read-all)
      (first)))

(defn install-schema
  "Attempts to transact a schema. If sucessful, returns tx promise;
   otherwise, error."
  [schema-tx conn]
  (let [tx (d/transact conn schema-tx)]
    (try @tx (catch Exception e (.getMessage e)))))

(defn scratch-conn
  "Create a connection to an anonymous, in-memory database."
  []
  (let [uri (str "datomic:mem://" (d/squuid))]
    (d/delete-database uri)
    (d/create-database uri)
    (d/connect uri)))

(defn setup-test-db []
  (def conn (scratch-conn))
  (-> (load-schema "schemas/arb.edn")
      (install-schema conn))
  conn)

(defn connect
  "Returns a connection object. If no db-uri provided, will return
   a connection to an anonymous in-memory database."
  ([] (setup-test-db))
  ([db-uri]
    (try
      (d/connect db-uri)
      (catch Exception e (.getMessage e)))))

(def conn (connect))

(defn db-now [] (d/db conn))

