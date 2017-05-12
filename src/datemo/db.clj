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

(defn scratch-conn
  "Create a connection to an anonymous, in-memory database. Returns
   a tuple containing a connection object and the db-uri."
  []
  (let [uri (str "datomic:mem://" (d/squuid))]
    ;; (println (str "Scratch db: " uri))
    (d/delete-database uri)
    (d/create-database uri)
    [(d/connect uri) uri]))

(defn connect
  ([] (scratch-conn))
  ([db-uri]
   (println (str "Connecting to db: " db-uri))
   [(d/connect db-uri) db-uri]))

;; Initailize!

(def db-atom (atom {:conn nil, :db-uri ""}))

(defn init-db
  ([] (init-db false))
  ([use-scratch-db]
    (let [[conn db-uri] (if (= true use-scratch-db)
                          (connect)
                          (connect (env :database-uri)))]
      (swap! db-atom assoc :conn conn :db-uri db-uri))))

(if (= "true" (env :testing))
  (init-db true)
  (init-db))

;; Helper functions

(defn get-conn []
  (:conn @db-atom))

(defn get-db-uri []
  (:db-uri @db-atom))

(defn db-now []
  (d/db (:conn @db-atom)))

(defn install-schema
  "Attempts to transact a schema. If sucessful, returns tx promise;
   otherwise, error."
  [schema-tx conn]
  (let [tx (d/transact conn schema-tx)]
    (try @tx (catch Exception e (.getMessage e)))))

(defn transact-or-error [tx]
  (try [@(d/transact (get-conn) tx) nil]
       (catch Exception e
         [nil (.getMessage e)])))

(defn retract-entity [entity-spec]
  [:db.fn/retractEntity entity-spec])

(defn retract-value [entity-spec attribute value]
  [:db/retract entity-spec attribute value])

(defn add-entity [partition attr-val-map]
  ([attr-val-map]
   (add-entity :db.part/user attr-val-map))
  ([partition attr-val-map]
   (into {:db/id (d/tempid partition)} attr-val-map)))

(defn add-value [entity-spec attribute value]
  [:db/add entity-spec attribute value])

(defn get-eid [entity-spec]
  (:db/id (d/entity (db-now) entity-spec)))

;: Pull

(defn pull-entity [entity-spec]
  (d/pull (db-now) '[*] entity-spec))

(defn pull-many-entities [entity-spec-coll]
  (d/pull (db-now) '[*] entity-spec-coll))

(defn pull-value [entity-spec attribute]
  (d/pull (db-now) [attribute] entity-spec))


;; Querys

(defn q-or-error
  ([query](q-or-error query (db-now)))
  ([query & inputs]
  (try [(apply d/q query inputs) nil]
       (catch Exception e
         [nil (.getMessage e)]))))

