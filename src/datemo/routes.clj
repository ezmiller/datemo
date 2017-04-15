(ns datemo.routes
  (:use compojure.core
        hiccup.core
        datemo.arb
        datemo.db)
  (:require [clojure.edn :as edn]
            [compojure.route :as route]
            [ring.middleware.json :refer :all]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.cors :refer [wrap-cors]]
            [datomic.api :as d]))

(require '[clojure.pprint :refer [pprint]])

(defn str->uuid [uuid-str]
  (java.util.UUID/fromString uuid-str))

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
  (let [uuid (str->uuid uuid-str)
        doc-tx (try (d/pull (d/db conn) '[*] [:arb/id uuid])
                    (catch Exception e (.getMessage e)))
        doc-html (tx->html doc-tx)]
    {:status 302
     :headers {"Content-Type" "application/hal+json; charset=utf-8"}
     :body {:_links {:self (apply str "/documents/" (str uuid-str))}
            :_embedded {:id uuid-str
                        :html doc-html}}}))

(defn remove-arb-root [tx-doc]
  (-> (mapv #(retract-entity (:db/id %)) (:arb/value tx-doc))
      (into (mapv #(retract-entity (:db/id %)) (:arb/metadata tx-doc)))))

(defn put-doc [uuid-str doc-string]
  (def entity-spec [:arb/id (str->uuid uuid-str)])
  (let [found (d/pull (db-now) '[*] entity-spec)
        update (-> (html->tx doc-string) (into {:arb/id (str->uuid uuid-str)}))]
    (if (nil? found)
      {:status 404}
      (let [retractions (remove-arb-root found)
            retract-tx (d/transact conn retractions)
            update-tx (d/transact conn [update])
            db-after (:db-after @update-tx)
            doc-tx (d/pull db-after '[*] entity-spec)
            doc-html (tx->html doc-tx)]
        {:status 202
         :body {:_links {:self (apply str "/documents/" uuid-str)}
                :_embedded {:id uuid-str
                            :html doc-html}}}))))

(defn save-arb-tx
  "Transact and return entity id and reference to db in new state."
  [tx]
  (let [tempid (:db/id tx)
        post-tx @(d/transact conn [tx])
        db-after (:db-after post-tx)]
    db-after))

(defn post-doc [doc-string]
 (let [id (d/squuid)
       tx (-> (html->tx doc-string) (into {:arb/id id}) (edn->clj))
       db-after (save-arb-tx tx)
       tx-from-db (d/pull db-after '[*] [:arb/id id])
       html (-> tx-from-db (tx->arb) (arb->hiccup) (html))]
   {:status 201
    :headers {"Content-Type" "application/hal+json; charset=utf-8"}
    :body {:_links {:self (apply str "/documents/" (str id))}
           :_embedded {:id id
                       :html html}}}))

(defroutes app-routes
  (GET "/" [] {:body {:_links {:documents {:href "/docs"}}}})
  (POST "/documents" [:as {body :body}] (post-doc (body :doc-string)))
  (PUT "/documents/:uuid-str" [uuid-str :as {body :body}]
       (put-doc uuid-str (body :doc-string)))
  (GET "/documents/:uuid-str" [uuid-str] (get-doc uuid-str))
  (route/not-found "Not found"))

(defn wrap-with-logger [handler]
  (fn [request]
    (prn (str "Request: " (:request-method request) " " (:uri request)))
    (handler request)))

(def app
  (-> app-routes
      (wrap-json-response)
      ;; (wrap-with-logger)
      (wrap-json-body {:keywords? true})
      (wrap-cors :access-control-allow-origin [#"http://localhost:8080"]
                 :access-control-allow-methods [:get :put :post :delete])
      (wrap-defaults api-defaults)))

