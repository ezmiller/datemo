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
    (println (str "Scratch db: " uri))
    (d/delete-database uri)
    (d/create-database uri)
    (d/connect uri)))

(defn connect
  "Returns a connection object. If no db-uri provided, will return
   a connection to an anonymous in-memory database."
  ([]
   (scratch-conn))
  ([db-uri]
    (println (str "Connecting to db: " db-uri))
    (try
      (d/connect db-uri)
      (catch Exception e (.getMessage e)))))

;; Initialize the db connection.
(def conn (if (= "true" (env :use-scratch-db))
            (connect)
            (connect (env :database-uri))))

(defn db-now [] (d/db conn))

;; Transacts but w/out need for surrounding list
;; (defn transact [conn & tx-specs]
;;   (d/transact conn tx-specs))

(defn transact-or-error [tx]
  (try [@(d/transact conn tx) nil]
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


;: Pull

(defn pull-entity [entity-spec]
  (d/pull (db-now) '[*] entity-spec))

(defn pull-value [entity-spec attribute]
  (d/pull (db-now) [attribute] entity-spec))
