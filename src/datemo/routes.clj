(ns datemo.routes
  (:use compojure.core)
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]))

(defroutes app-routes
  (GET "/" [] {:body {:_links {:documents {:href "/docs"}}}})
  (route/not-found "Not found"))

(def app
  (-> app-routes
      (wrap-json-response)
      (wrap-defaults api-defaults)))

