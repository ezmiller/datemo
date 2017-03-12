(ns datemo.routes
  (:use compojure.core
        hiccup.core
        datemo.arb)
  (:require [clojure.edn :as edn]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [datomic.api :as d]))

(require '[clojure.pprint :refer [pprint]])

(def db-uri "datomic:dev://localhost:4334/datemo")
(def conn (d/connect db-uri))
(def db (d/db conn))

(defn save-arb-tx [tx]
  "Transact and return entity id and reference to db in new state."
  [conn tx]
  (let [tempid (:db/id tx)
        post-tx @(d/transact conn [tx])
        db-after (:db-after post-tx)
        eid (d/resolve-tempid db (:tempids post-tx) tempid)]
    [eid db-after]))

(defn pull-arb-by-eid [eid]
  (d/pull (d/db conn) '[*] eid))

;; I tried to use :db.part/arb for the tempid below, but it
;; caused an error, invalid id. 
(defn add-tempid [arb-tx]
  (into arb-tx {:db/id (d/tempid :db.part/user)}))

;; Note: You get an error if the {:readers *data-reader*} bit is not added.
;; This seems to relate to the need for data-readers to understand certain
;; tags; in this case: :db/id. Datomic installs some data readers for us.
;; But we need to do this to get them picked up. See here for a bit more
;; info: https://clojure.org/reference/reader#_tagged_literals.
(defn edn->clj [edn]
  (edn/read-string {:readers *data-readers*} (prn-str edn)))

(defn get-doc [eid]
  (let [doc-tx (d/pull (d/db conn) '[*] eid)
        doc-html (tx->html doc-tx)]))

(defn post-doc [doc-string]
 (let [tx (-> (html->tx doc-string) (add-tempid) (edn->clj))
       [eid db-after] (save-arb-tx tx)
       tx-from-db (d/pull db-after '[*] eid)
       html (-> tx-from-db (tx->arb) (arb->hiccup) (html))]
   {:status 201
    :headers {"Content-Type" "application/hal+json; charset=utf-8"}
    :body {:_links {:self (apply str "/documents/" (str eid))}
           :_embedded {:id eid
                       :html html}}}))

(defroutes app-routes
  (GET "/" [] {:body {:_links {:documents {:href "/docs"}}}})
  (POST "/documents" [doc-string] (post-doc doc-string))
  (route/not-found "Not found"))

(def app
  (-> app-routes
      (wrap-json-response)
      (wrap-defaults api-defaults)))

