(ns datemo.routes
  (:use compojure.core
        hiccup.core
        datemo.arb
        datemo.db)
  (:require [clojure.edn :as edn]
            [compojure.route :as route]
            [ring.middleware.json :refer :all]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [datomic.api :as d]))

(require '[clojure.pprint :refer [pprint]])

(defn save-arb-tx
  "Transact and return entity id and reference to db in new state."
  [tx]
  (let [tempid (:db/id tx)
        post-tx @(d/transact conn [tx])
        db-after (:db-after post-tx)
        eid (d/resolve-tempid (db-now) (:tempids post-tx) tempid)]
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

(defn get-doc
  "Given a uuid string id, responds with the document if found."
  [uuid-str]
  (let [uuid (java.util.UUID/fromString uuid-str)
        doc-tx (try (d/pull (d/db conn) '[*] [:arb/id uuid])
                    (catch Exception e (.getMessage e)))
        doc-html (tx->html doc-tx)]
    {:status 302
     :headers {"Content-Type" "application/hal+json; charset=utf-8"}
     :body {:_links {:self (apply str "/documents/" (str uuid-str))}
            :_embedded {:id uuid-str
                        :html doc-html}}}))

(defn update-doc [eid doc-string]
  [eid doc-string])

(defn post-doc [doc-string]
 (let [tx (-> (html->tx doc-string) (add-tempid) (edn->clj))
       [eid db-after] (save-arb-tx tx)
       tx-from-db (d/pull db-after '[*] eid)
       html (-> tx-from-db (tx->arb) (arb->hiccup) (html))]
   (pprint (type eid))
   {:status 201
    :headers {"Content-Type" "application/hal+json; charset=utf-8"}
    :body {:_links {:self (apply str "/documents/" (str eid))}
           :_embedded {:id eid
                       :html html}}}))

(defroutes app-routes
  (GET "/" [] {:body {:_links {:documents {:href "/docs"}}}})
  (POST "/documents" [doc-string] (post-doc doc-string))
  (GET "/documents/:uuid-str" [uuid-str] (get-doc uuid-str))
  (route/not-found "Not found"))

(defn wrap-with-logger [handler]
  (fn [request]
    (prn (str "Request: " (:request-method request) " " (:uri request)))
    (handler request)))

(def app
  (-> app-routes
      (wrap-json-response)
      (wrap-with-logger)
      (wrap-defaults api-defaults)))

