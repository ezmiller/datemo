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

(defn parse-int [s]
   (Integer. (re-find  #"\d+" s )))

(defn str->uuid [uuid-str]
  (java.util.UUID/fromString uuid-str))

(defn get-in-metadata [k metadata]
  (k (first (filter #(k %) metadata))))

(defn get-title [metadata]
  (get-in-metadata :metadata/title metadata))

;; Note: You get an error if the {:readers *data-reader*} bit is not added.
;; This seems to relate to the need for data-readers to understand certain
;; tags; in this case: :db/id. Datomic installs some data readers for us.
;; But we need to do this to get them picked up. See here for a bit more
;; info: https://clojure.org/reference/reader#_tagged_literals.
(defn edn->clj [edn]
  (edn/read-string {:readers *data-readers*} (prn-str edn)))

(defn pagination-self-link-params [page doctype perpage]
  (let [params (transient {})
        stringify #(apply str (name (key %)) "=" (str (val %)))]
    (if (> page 1) (assoc! params :page page))
    (if (not (nil? doctype)) (assoc! params :doctype doctype))
    (if (not (= 20 perpage)) (assoc! params :perpage perpage))
    (->> (mapv stringify (persistent! params))
         (clojure.string/join "&"))))

(defn pagination-link [path page doctype perpage]
  (let [param-str (pagination-self-link-params page doctype perpage)]
    (if (= 0 (count param-str))
      path
      (apply str path "?" param-str))))

(defn pagination-links [path page doctype perpage total]
  (let [links (transient {:self {:href (pagination-link path page doctype perpage)}})
        next-link {:href (pagination-link path (inc page) doctype perpage)}
        prev-link {:href (pagination-link path (dec page) doctype perpage)}]
    (if (< (* page perpage) total) (assoc! links :next next-link))
    (if (not= page 1) (assoc! links :previous prev-link))
    (persistent! links)))

(defn get-doc-coll-data [coll]
  (mapv #(hash-map :_links {:self {:href (apply str "/documents/" (str (:arb/id %)))}}
                   :id (:arb/id %)
                   :title (or (get-title (:arb/metadata %)) "Untitled")
                   :html (tx->html %)) coll))

;; Better would be to do this using a paramterized query.
;; E.g. [:find (pull ?e [*]) :in [$ ?doctype] :where [?e :arb/doctype ?doctype]]
(defn latest-query
  ([] '[:find (pull ?e [*]) :where [?e :arb/doctype]])
  ([doctype]
   (let [doctype-val (keyword "doctype" doctype)]
    [:find '(pull ?e [*])
      :where ['?e ':arb/doctype doctype-val]])))

(defn latest [req]
  (let [page (->> (or (get-in req [:params :page]) "1") (parse-int))
        perpage (->> (or (get-in req [:params :perpage]) "20") (parse-int))
        offset (* perpage (dec page))
        doctype (get-in req [:params :doctype])
        query (if (nil? doctype) (latest-query) (latest-query doctype))
        [docs error] (q-or-error query)]
    (cond
      (not (nil? error)) {:status 500}
      (>= offset (count docs)) {:status 404}
      :else (let [total (count docs)
                  paged (->> (reverse docs) (drop offset) (take perpage))]
              {:status 200
               :headers {"Content-Type" "application/hal+json; charset=utf-8"}
               :body {:_links (pagination-links "/latest" page doctype perpage total)
                      :_embedded (get-doc-coll-data (mapv #(first %) paged))}}))))

(defn get-doc
  "Given a uuid string id, responds with the document if found."
  [uuid-str]
  (let [uuid (str->uuid uuid-str)
        doc (try (d/pull (db-now) '[*] [:arb/id uuid])
                    (catch Exception e (.getMessage e)))
        title (or (get-title (:arb/metadata doc)) "Untitled")
        doc-html (tx->html doc)]
    {:status 200
     :headers {"Content-Type" "application/hal+json; charset=utf-8"}
     :body {:_links {:self {:href (apply str "/documents/" (str uuid-str))}}
            :_embedded {:id uuid-str
                        :title title
                        :html doc-html}}}))

(defn remove-arb-root [tx-doc]
  (-> (mapv #(retract-entity (:db/id %)) (:arb/value tx-doc))
      (into (mapv #(retract-entity (:db/id %)) (:arb/metadata tx-doc)))))

(defn put-doc [uuid-str doc-string title doctype]
  (def entity-spec [:arb/id (str->uuid uuid-str)])
  (let [found (d/pull (db-now) '[*] entity-spec)
        update (-> (html->tx
                     (md-to-html-string doc-string)
                     {:metadata/title title})
                   (into {:arb/id (str->uuid uuid-str)})
                   (into {:arb/doctype (keyword "doctype" doctype)}))]
    (if (nil? found)
      {:status 404}
      (let [retractions (remove-arb-root found)
            retract-tx (d/transact (get-conn) retractions)
            update-tx (d/transact (get-conn) [update])
            db-after (:db-after @update-tx)
            doc (d/pull db-after '[*] entity-spec)
            new-doctype (d/pull db-after '[:db/ident] (:db/id (:arb/doctype doc)))
            doc-html (tx->html doc)]
        {:status 202
         :body {:_links {:self (apply str "/documents/" uuid-str)}
                :_embedded {:id uuid-str
                            :title (get-title (:arb/metadata doc))
                            :doctype (name (:db/ident new-doctype))
                            :html doc-html}}}))))

(defn post-doc [doc-string doctype title]
 (let [id (d/squuid)
       tx (-> (html->tx
                (md-to-html-string doc-string)
                {:metadata/title (or title "Untitled")})
              (into {:arb/doctype (keyword "doctype" doctype)})
              (into {:arb/id id}) (edn->clj))
       [tx-result tx-error] (db/transact-or-error [tx])]
   ;; (pprint {:tx tx :tx-result tx-result :tx-error tx-error})
   (if (nil? tx-error)
     (let [db-after (:db-after tx-result)
           new-doc (d/pull db-after '[*] [:arb/id id])
           title (get-title (:arb/metadata new-doc))
           html (-> new-doc (tx->arb) (arb->hiccup) (html))]
       ;; (pprint {:new-doc new-doc})
       {:status 201
        :headers {"Content-Type" "application/hal+json; charset=utf-8"}
        :body {:_links {:self {:href (apply str "/documents/" (str id))}}
               :_embedded {:id id
                           :title title
                           :doctype doctype
                           :html html}}})
     {:status 500
      :body {:error (apply str "Error posting: " tx-error)}})))

(defroutes app-routes
  (GET "/" [] {:body {:_links {:documents {:href "/docs"}}}})
  (POST "/documents" [:as {body :body}]
        (post-doc (body :doc-string) (body :doctype) (body :title)))
  (PUT "/documents/:uuid-str" [uuid-str :as {body :body}]
       (put-doc uuid-str (body :doc-string) (body :title) (body :doctype)))
  (GET "/documents/:uuid-str" [uuid-str] (get-doc uuid-str))
  (GET "/latest" [:as request] (latest request))
  (route/not-found "Not found"))

(defn wrap-with-debugger [handler]
  (fn [request]
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

