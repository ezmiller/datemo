(ns datemo.init
  (require
    [environ.core :refer [env]]
    [datemo.db :as db]))

(require '[clojure.pprint :refer [pprint]])

(defn init []
  (if (= "true" (env :testing))
    (db/init-db true)
    (db/init-db)))
