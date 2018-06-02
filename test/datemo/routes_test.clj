(ns datemo.routes-test
  (:require [cheshire.core :as json :refer [parse-string]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [datomic.client.api :as d])
  (:use clojure.test
        ring.mock.request
        markdown.core
        datemo.routes
        datemo.arb
        datemo.db))

(use '[clojure.pprint :refer [pprint]])

;; Install arb schema in scratch db.
(defn db-prep [f]
  (let [dbname (str "test-" (java.util.UUID/randomUUID))]
    (init-client)
    ;; (if nil? (get-client)
    ;;   (init-client))
    (create-db dbname)
    (connect dbname)
    (-> (load-schema "schemas/arb.edn")
        (install-schema (get-conn)))
    (f)
    (destroy-db dbname)))

(use-fixtures :each db-prep)

(defn parse [response]
  (-> (:body response) (json/parse-string true)))

(defmacro prep-request [method path data]
  `(-> (request ~method ~path (json/generate-string ~data))
       (header "Content-Type" "application/json; charset=utf-8")))

(defmacro doc-tx-spec [id title doctype tag value tags]
  [{:arb/id id
    :arb/value {:content/text value}
    :arb/metadata {:metadata/html-tag tag
                   :metadata/title title
                   :metadata/tags (mapv #(array-map :tag/name (keyword %)) tags)
                   :metadata/doctype (keyword "doctype" doctype)}}])

;; Tests

(deftest test-get-root
  (testing "GET /"
    (let [response (handler (request :get "/"))]
      (is (= (:status response) 200))
      (is (= (json/parse-string (:body response) true)
             {:_links {:documents {:href "/docs"}}})))))

(deftest test-get-latest
  (testing "GET /latest?page=1 with one note in db"
    (let [id (java.util.UUID/randomUUID)
          url (str "/latest?page=1")
          doc (doc-tx-spec id "A title" "note" :p "note1" ["tag1"])
          tx (d/transact (get-conn) {:tx-data doc})
          [_ _ tx-inst] (first (:tx-data tx))
          response (handler (request :get url))]
      (is (= {:_links {:self {:href "/latest"}}
              :_embedded [{:_links {:self {:href (apply str "/documents/" (str id))}}
                           :id (str id)
                           :title "A title"
                           :tags ["tag1"]
                           :created-at (c/to-string tx-inst)
                           :updated-at (c/to-string tx-inst)
                           :doctype "note"
                           :html "<p>note1</p>"}]}
             (parse response)))))

  (testing "GET /latest (no query params)"
    (let [url "/latest"
          response (handler (request :get url))]
      (is (= 200
             (:status response))
          (= 1
             (count (->> (parse response) (:_embedded)))))))

  (testing "GET /latest?page=2 with one note in db"
    (let [url (str "/latest?page=2")
          response (handler (request :get url))]
      (is (= 404
             (:status response)))))

  (testing "GET /latest?page=2 with 50 notes in db"
    (let [url "/latest?page=2"
          ids (mapv (fn [x] (java.util.UUID/randomUUID)) (range 50))
          doc-specs (mapv #(first (doc-tx-spec % "A title" "note" :p "test" [])) ids)
          tx (d/transact (get-conn) {:tx-data doc-specs})
          response (handler (request :get url))]
      (is (= 200 (:status response)))
      (is (= 20 (->> (parse response) (:_embedded) (count))))))

  (testing "GET /latest doctype parameter"
    (let [url "/latest?doctype=essay"
          ids (mapv (fn [x] (java.util.UUID/randomUUID)) (range 5))
          doc-specs (mapv #(first (doc-tx-spec % "A title" "essay" :p "test" [])) ids)
          tx (d/transact (get-conn) {:tx-data doc-specs})
          response (handler (request :get url))]
      (is (= 5
             (->> (parse response) (:_embedded) (count))))))

  (testing "GET /latest perpage parameter"
    (let [url "/latest?perpage=3"
          response (handler (request :get url))]
      (is (= 3 (->> (parse response) (:_embedded) (count))))))
  )

(deftest test-get-latest-with-tags
  (testing "GET /latest tag filter parameter"
    (let [doc-specs
            [
             (first (doc-tx-spec (java.util.UUID/randomUUID) "A title" "essay" :p "test" [:tag1]))
             (first (doc-tx-spec (java.util.UUID/randomUUID) "A title" "essay" :p "test" [:tag2]))
             (first (doc-tx-spec (java.util.UUID/randomUUID) "A title" "essay" :p "test" [:tag1 :tag2]))
             ]
          tx (d/transact (get-conn) {:tx-data doc-specs})
          response1 (handler (request :get "/latest?tags=tag1"))
          response2 (handler (request :get "/latest?tags=tag2"))
          response3 (handler (request :get "/latest?tags=tag1&tags=tag2"))
          response4 (handler (request :get "/latest?tags=tag4"))]
      (is (= 2 (-> (parse response1) (:_embedded) (count))))
      (is (= 2 (-> (parse response2) (:_embedded) (count))))
      (is (= 3 (-> (parse response3) (:_embedded) (count))))
      (is (= 0 (-> (parse response4) (:_embedded) (count)))))))


(deftest test-get-document-by-id
  (testing "GET /documents/:id"
    (def id (java.util.UUID/randomUUID))
    (def tx-spec [{:arb/id id
                   :arb/value {:content/text "test"}
                   :arb/metadata {:metadata/html-tag :p
                                  :metadata/doctype :doctype/note
                                  :metadata/tags [{:tag/name :tag1}]
                                  :metadata/title "A title"}}])
    (let [tx (d/transact (get-conn) {:tx-data tx-spec})
          [_ _ tx-inst] (first (:tx-data tx))
          response (handler (request :get (str "/documents/" id)))]
      (is (= 200 (:status response)))
      (is (= {:_links {:self {:href (str "/documents/" id) } }
              :_embedded {:id (str id)
                          :title "A title"
                          :tags ["tag1"]
                          :doctype "note"
                          :created-at (c/to-string tx-inst)
                          :updated-at (c/to-string tx-inst)
                          :html "<p>test</p>"}}
             (-> (parse response)))))))

(deftest test-put-document
    (testing "with single node doc"
      (let [id (java.util.UUID/randomUUID)
            tx-spec (doc-tx-spec id "A title" "note" :div "test" ["tag1"])
            data {:doc-string "replaced"
                  :title "A new title"
                  :doctype "essay"
                  :tags ["tag2"]}
            tx-result (d/transact (get-conn) {:tx-data tx-spec})
            [_ _ tx-inst] (first (:tx-data tx-result))
            response (handler (prep-request :put (str "/documents/" id) data))
            body (parse response)]
        (is (= 202 (:status response)))
        (is (= (str "/documents/" id) (get-in body [:_links :self ])))
        (is (= (str id) (get-in body [:_embedded :id])))
        (is (= "A new title" (get-in body [:_embedded :title])))
        (is (= "essay" (get-in body [:_embedded :doctype])))
        (is (= ["tag2"] (get-in body [:_embedded :tags])))
        (is (t/equal?
              (c/from-date tx-inst)
              (c/from-string (get-in body [:_embedded :created-at]))))
        (is (contains? (get-in body [:_embedded]) :updated-at))
        (is (not= nil (get-in body [:_embedded :updated-at])))
        (is (= "<p>replaced</p>" (get-in body [:_embedded :html]))))))

(deftest test-post-document
  ;; TODO: Add tests for error case.
  (testing "POST /documents"
    (let [data (->> (apply str "# Title  \nParagraph")
                    (array-map :doc-string)
                    (into {:doctype "note"})
                    (into {:tags ["tag1" "tag2"]}))
          response (handler (prep-request :post "/documents" data))
          body (parse response)
          status (:status response)
          headers (:headers response)]
      (is (= 201 status))
      (is (= {"Content-Type" "application/hal+json; charset=utf-8"}
             headers))
      (is (= "/documents/"
             (-> (:_links body)
                 (->> (:self) (:href) (re-find #"/documents/")))))
      (is (= true (-> (:_embedded body) (contains? :id))))
      (is (= "note" (-> (:_embedded body) (:doctype))))
      (is (= "Untitled" (get-in body [:_embedded :title])))
      (is (= ["tag1" "tag2"] (get-in body [:_embedded :tags])))
      (is (= false (nil? (get-in body [:_embedded :created-at]))))
      (is (= false (nil? (get-in body [:_embedded :updated-at]))))
      (is (= "<div><h1>Title</h1><p>Paragraph</p></div>"
             (get-in body [:_embedded :html])))
      )))

(deftest test-not-found-route
  (testing "not-found route"
    (let [response (handler (request :get "/bogus/route"))]
      (is (= (:status response) 404))
      (is (= (:body response) "Not found")))))
