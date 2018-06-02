(ns datemo.db
  (:require
    [datomic.client.api :as d]
    [clojure.java.io :as io]
    [environ.core :refer [env]]
    [clojure.pprint :as pp :refer (pprint)]))

(def resource io/resource)

(def not-nil? (complement nil?))

(defn- read-one
  [r]
  (try
    (read r)
    (catch java.lang.RuntimeException e
      (if (= "EOF while reading" (.getMessage e))
        ::EOF
        (throw e)))))

(defn read-all
  "Reads a sequence of top-level objects in file"
  ;; Modified from Clojure Cookbook, L Vanderhart & R. Neufeld
  [src]
  (with-open [r (java.io.PushbackReader. (clojure.java.io/reader src))]
    (binding [*read-eval* false]
      (doall (take-while #(not= ::EOF %) (repeatedly #(read-one r)))))))

(defn load-schema
  "Given a path string to a schema edn file, loads and returns."
  [path-str]
  (-> (io/resource path-str)
      (read-all)
      (first)))

;; Initialize!

(def db-atom (atom {:conn nil, :client nil, :cfg {}}))

(defn read-cfg []
   (read-string (slurp (resource "db-config.edn"))))

(defn connect
  ([dbname] (connect dbname (:client @db-atom)))
  ([dbname client]
    (println (str "Connecting to datomic cloud db: " dbname))
    (let [conn (d/connect client {:db-name dbname})]
      (swap! db-atom assoc :conn conn))))

(defn init-client []
  (let [cfg (read-cfg)
        client (d/client cfg)]
    (println (str "Connecting datomic cloud client to: " (:endpoint cfg)))
    (swap! db-atom assoc :client client :cfg cfg)))

;; Helper functions

(defn get-conn []
  (:conn @db-atom))

(defn get-client []
  (:client @db-atom))

(defn get-cfg []
  (:cfg @db-atom))

(defn create-db [name]
  (println "Creating database:" name)
  (d/create-database (get-client) {:db-name name}))

(defn destroy-db [name]
  (println "Deleting database:" name)
  (d/create-database (get-client) {:db-name name}))

(defn db-now []
  (d/db (:conn @db-atom)))

(defn hist-db-now []
  (d/db (:conn @db-atom)))

(defn install-schema
  "Attempts to transact a schema. If sucessful, returns tx promise;
   otherwise, error."
  [schema-tx conn]
  (let [tx (d/transact conn {:tx-data schema-tx})]
    (try tx (catch Exception e (.getMessage e)))))

(defn transact-or-error [tx]
  (try [(d/transact (get-conn) {:tx-data tx}) nil]
       (catch Exception e
         [nil (.getMessage e)])))

(defn retract-entity [entity-spec]
  [:db/retractEntity entity-spec])

(defn retract-value [entity-spec attribute value]
  [:db/retract entity-spec attribute value])

(defn add-value [entity-spec attribute value]
  [:db/add entity-spec attribute value])


;: Pull

(defn pull-entity [entity-spec]
  (d/pull (db-now) '[*] entity-spec))

(defn pull-many-entities [entity-spec-coll]
  (d/pull (db-now) '[*] entity-spec-coll))

(defn pull-value [entity-spec attribute]
  (d/pull (db-now) [attribute] entity-spec))


;; Querys

(defn q-or-error
  ([query] (q-or-error query (db-now)))
  ([query & args]
   (try [(d/q {:query query :args args}) nil]
        (catch Exception e
          [nil (.getMessage e)]))))

(defn get-arb-eid
  ([arb-id] (get-arb-eid arb-id (db-now)))
  ([arb-id args]
   (let [[r error] (q-or-error `[:find ?e :where [?e :arb/id ~arb-id]] args)]
     (if (not-nil? error)
       nil
       (first (first r))))))
