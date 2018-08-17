(ns datemo.init
  (:require
    [environ.core :refer [env]]
    [datemo.db :as db]))

(require '[clojure.pprint :refer [pprint]])

(defn prep-db [name]
  {:pre [(not (nil? name))]}
  (println "Database `" name "` exists?: " (db/db-exists name))
  (if (db/db-exists name)
    (db/connect name)
    (do
      (println "Database `" name "` does not exist, will set it up...")
      (db/create-db name)
      (db/connect name)
      (-> (db/load-schema "schemas/arb.edn")
          (db/install-schema (db/get-conn))))))

(defn init []
  (println (apply str "Initializing datemo version: " (:datemo-version env)))
  (let [initialized? (db/init-client)]
    (if initialized?
      (if (not (:testing env)) (prep-db (:db-name env)))
      (throw (Exception. "Failed to initalize!")))))
