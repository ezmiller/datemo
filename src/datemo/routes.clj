(ns datemo.routes
  (:use compojure.core)
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]))

(defroutes app-routes
  (route/not-found "Not found"))

(def app
  (-> app-routes
      (wrap-defaults api-defaults)))

