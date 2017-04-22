(ns datemo.routes
  (:use markdown.core
        compojure.core
        hiccup.core
        datemo.arb
        datemo.db)
  (:require [clojure.edn :as edn]
            [compojure.route :as route]
            [ring.middleware.json :refer :all]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.cors :refer [wrap-cors]]
            [datomic.api :as d]
            [datemo.db :as db]))

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
        doc-tx (try (d/pull (db-now) '[*] [:arb/id uuid])
                    (catch Exception e (.getMessage e)))
        doc-html (tx->html doc-tx)]
    {:status 200
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
            retract-tx (d/transact (get-conn) retractions)
            update-tx (d/transact (get-conn) [update])
            db-after (:db-after @update-tx)
            doc-tx (d/pull db-after '[*] entity-spec)
            doc-html (tx->html doc-tx)]
        {:status 202
         :body {:_links {:self (apply str "/documents/" uuid-str)}
                :_embedded {:id uuid-str
                            :html doc-html}}}))))

(defn post-doc [doc-string doctype]
 (let [id (d/squuid)
       tx (-> (html->tx (md-to-html-string doc-string))
              (into {:arb/doctype (keyword "doctype" doctype)})
              (into {:arb/id id}) (edn->clj))
       [tx-result tx-error] (db/transact-or-error [tx])]
   (if (nil? tx-error)
     (let [db-after (:db-after tx-result)
           new-doc (d/pull db-after '[*] [:arb/id id])
           html (-> new-doc (tx->arb) (arb->hiccup) (html))]
       {:status 201
        :headers {"Content-Type" "application/hal+json; charset=utf-8"}
        :body {:_links {:self (apply str "/documents/" (str id))}
               :_embedded {:id id
                           :doctype doctype
                           :html html}}})
     {:status 500
      :body {:error "Failed to post the document."}})))

(defroutes app-routes
  (GET "/" [] {:body {:_links {:documents {:href "/docs"}}}})
  (POST "/documents" [:as {body :body}]
        (post-doc (body :doc-string) (body :doctype)))
  (PUT "/documents/:uuid-str" [uuid-str :as {body :body}]
       (put-doc uuid-str (body :doc-string)))
  (GET "/documents/:uuid-str" [uuid-str] (get-doc uuid-str))
  (route/not-found "Not found"))

(defn wrap-with-debugger [handler]
  (fn [request]
    ;; (pprint request)
    (prn (str "Request: " (:request-method request) " " (:uri request)))
    (handler request)))

(def app
  (-> app-routes
      (wrap-json-response)
      ;; (wrap-with-debugger)
      (wrap-json-body {:keywords? true})
      (wrap-cors :access-control-allow-origin [#"http://localhost:8080"]
                 :access-control-allow-methods [:get :put :post :delete])
      (wrap-defaults api-defaults)))

