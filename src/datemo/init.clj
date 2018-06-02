(ns datemo.init
  (require
    [datemo.db :as db]))

(require '[clojure.pprint :refer [pprint]])

(defn init []
  (println (apply str "Initializing datemo version: " (:datemo-version env)))
  (db/init-db cfg))
