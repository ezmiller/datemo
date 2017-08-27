(ns datemo.init
  (require
    [environ.core :refer [env]]
    [datemo.db :as db]))

(require '[clojure.pprint :refer [pprint]])

(defn init []
  (println (apply str "Initializing datemo version: " (:datemo-version env)))
  (if (= "true" (env :testing))
    (db/init-db true)
    (db/init-db)))
