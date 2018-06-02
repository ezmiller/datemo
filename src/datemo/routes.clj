(ns datemo.routes
  (:use markdown.core
        compojure.core
        hiccup.core
        datemo.arb
        datemo.db)
  (:require [clojure.edn :as edn]
            [clojure.string :as s]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [compojure.route :as route]
            [ring.middleware.json :refer :all]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.cors :refer [wrap-cors]]
            [datomic.client.api :as d]
            [datemo.db :as db]))

(require '[clojure.pprint :refer [pprint]])

(defn has-error [error]
  (not (nil? error)))

(defn parse-int [s]
   (Integer. (re-find  #"\d+" s )))

(defn str->uuid [uuid-str]
  (java.util.UUID/fromString uuid-str))

(defn is-empty-metadata [entity]
  (= :tag/none (:db/ident (pull-entity (:db/id entity)))))

(defn get-title [metadata]
  (get-in-metadata :metadata/title metadata))

(defn get-doctype [metadata]
  (-> (get-in-metadata :metadata/doctype metadata)
      (:db/id)
      (db/pull-entity)
      (:db/ident)
      (name)))

(defn get-tags
  ([metadata] (get-tags metadata true))
  ([metadata return-strings?]
    (def tags (get-in-metadata :metadata/tags metadata))
    (if (or (nil? tags) (is-empty-metadata (first tags)))
      []
      (mapv #(if return-strings?
               (name (:tag/name %))
               (:tag/name %))
            tags))))

(defn gen-tags-meta [tags]
  (if (empty? tags)
    {:metadata/tags [:tag/none]}
    {:metadata/tags (mapv #(array-map :tag/name (keyword (s/trim %))) tags)}))

(defn get-updated-at
  ([arb-id] (get-updated-at arb-id (db-now)))
  ([arb-id db]
   (let [eid (get-arb-eid arb-id)
        [result error] (q-or-error '[:find (max ?t)
                                     :in $ ?e
                                     :where
                                     [?e _ _ ?tx]
                                     [?tx :db/txInstant ?t]] (d/history db) eid)]
    (if (has-error error)
      nil
      (->> result (first) (first) (c/to-string))))))

(defn get-created-at
  ([arb-id] (get-created-at arb-id (db-now)))
  ([arb-id db]
    (let [[result error] (q-or-error '[:find ?t
                                       :in $ ?id
                                       :where
                                       [?e :arb/id ?id ?tx]
                                       [?tx :db/txInstant ?t]] (d/history db) arb-id)]
      (if (has-error error)
        nil
        (->> result (first) (first) (c/to-string))))))

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
                   :tags (or (get-tags (:arb/metadata %)) [])
                   :doctype (get-doctype (:arb/metadata %))
                   :created-at (get-created-at (:arb/id %))
                   :updated-at (get-updated-at (:arb/id %))
                   :html (tx->html %)) coll))

(defn latest-query [filter-doctype]
  (cond
    (true? filter-doctype)
      '[:find (pull ?doc [*])
        :in $ ?doctype
        :where [?doc :arb/metadata ?meta]
               [?meta :metadata/doctype ?doctype]]
    :else
      '[:find (pull ?doc [*])
        :where [?doc :arb/metadata ?meta]
               [?meta :metadata/doctype]]))

(defn filter-by-tags [res tags]
  (filterv
    (fn [r]
      (let [metadata (as-> (first r) x (:arb/metadata x) )
            tagset (get-tags metadata false)
            tags-to-find (mapv #(keyword %) (if (vector? tags) tags [tags]))
            matched (filterv #(>= (.indexOf tagset %) 0) tags-to-find)]
        (> (count matched) 0)))
    res))

(defn latest [req]
  (let [page (->> (or (get-in req [:params :page]) "1") (parse-int))
        perpage (->> (or (get-in req [:params :perpage]) "20") (parse-int))
        offset (* perpage (dec page))
        doctype (get-in req [:params :doctype])
        tags (get-in req [:params :tags])
        query (latest-query (not (nil? doctype)))
        [res error] (if (nil? doctype)
                    (q-or-error query (db-now))
                    (q-or-error query (db-now) (keyword "doctype" doctype)))]
    (def docs (if (nil? tags) res (filter-by-tags res tags)))
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
        tags (or (get-tags (:arb/metadata doc)) [])
        doctype (get-doctype (:arb/metadata doc))
        doc-html (tx->html doc)]
    {:status 200
     :headers {"Content-Type" "application/hal+json; charset=utf-8"}
     :body {:_links {:self {:href (apply str "/documents/" (str uuid-str))}}
            :_embedded {:id uuid-str
                        :title title
                        :tags tags
                        :created-at (get-created-at uuid)
                        :updated-at (get-updated-at uuid)
                        :doctype doctype
                        :html doc-html}}}))

(defn remove-arb-root [tx-doc]
  (-> (mapv #(retract-entity (:db/id %)) (:arb/value tx-doc))
      (into (mapv #(retract-entity (:db/id %)) (:arb/metadata tx-doc)))))

(defn is-empty-result [result]
  (= {:db/id nil} result))

(defn put-doc [uuid-str doc-string title doctype tags]
  (def entity-spec [:arb/id (str->uuid uuid-str)])
  (let [found (d/pull (db-now) '[*] entity-spec)
        update (-> (html->tx
                     (md-to-html-string doc-string)
                     {:metadata/title title}
                     {:metadata/doctype (keyword "doctype" doctype)}
                     (gen-tags-meta tags))
                   (into {:arb/id (str->uuid uuid-str)}))]
    (if (is-empty-result found)
      {:status 404}
      (let [retractions (remove-arb-root found)
            retract-tx (d/transact (get-conn) {:tx-data retractions})
            update-tx (d/transact (get-conn) {:tx-data [update]})
            db-after (:db-after update-tx)
            doc (d/pull db-after '[*] entity-spec)
            doc-html (tx->html doc)]
        {:status 202
         :body {:_links {:self (apply str "/documents/" uuid-str)}
                :_embedded {:id uuid-str
                            :title (get-title (:arb/metadata doc))
                            :doctype (get-doctype (:arb/metadata doc))
                            :created-at (get-created-at
                                          (str->uuid uuid-str)
                                          db-after)
                            :updated-at (get-updated-at
                                          (str->uuid uuid-str)
                                          db-after)
                            :tags (get-tags (:arb/metadata doc db-after))
                            :html doc-html}}}))))

(defn post-doc [doc-string doctype title tags]
  (let [id (java.util.UUID/randomUUID)
        tx (-> (html->tx
                 (md-to-html-string doc-string)
                 {:metadata/title (or title "Untitled")}
                 {:metadata/doctype (keyword "doctype" doctype)}
                 (gen-tags-meta tags))
               (into {:arb/id id}) (edn->clj))
        [tx-result tx-error] (db/transact-or-error [tx])]
    (if (nil? tx-error)
      (let [db-after (:db-after tx-result)
            new-doc (d/pull db-after '[*] [:arb/id id])
            html (-> new-doc (tx->arb) (arb->hiccup) (html))]
        {:status 201
         :headers {"Content-Type" "application/hal+json; charset=utf-8"}
         :body {:_links {:self {:href (apply str "/documents/" (str id))}}
                :_embedded {:id id
                            :title (get-title (:arb/metadata new-doc))
                            :doctype (get-doctype (:arb/metadata new-doc))
                            :created-at (get-created-at id db-after)
                            :updated-at (get-updated-at id db-after)
                            :tags (get-tags (:arb/metadata new-doc))
                            :html html}}})
      {:status 500
       :body {:error (apply str "Error posting: " tx-error)}})))

(defroutes app-routes
  (GET "/" [] {:body {:_links {:documents {:href "/docs"}}}})
  (POST "/documents" [:as {body :body}]
        (post-doc (body :doc-string) (body :doctype) (body :title) (body :tags)))
  (PUT "/documents/:uuid-str" [uuid-str :as {body :body}]
       (put-doc uuid-str (body :doc-string) (body :title) (body :doctype) (body :tags)))
  (GET "/documents/:uuid-str" [uuid-str] (get-doc uuid-str))
  (GET "/latest" [:as request] (latest request))
  (route/not-found "Not found"))

(defn wrap-with-debugger [handler]
  (fn [request]
    (prn (str "Request: " (:request-method request) " " (:uri request)))
    (handler request)))

(def handler
  (-> app-routes
      (wrap-json-response)
      ;; (wrap-with-debugger)
      (wrap-json-body {:keywords? true})
      (wrap-cors :access-control-allow-origin [#"http://localhost:8080"
                                               #"http://localhost:4000"
                                               #"http://humanscode.com"]
                 :access-control-allow-methods [:get :put :post :delete])
      (wrap-defaults api-defaults)))

