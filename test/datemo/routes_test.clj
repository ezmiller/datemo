(ns datemo.routes-test
  (:require [cheshire.core :as json :refer [parse-string]]
            [datomic.api :as d])
  (:use clojure.test
        ring.mock.request
        markdown.core
        datemo.routes
        datemo.arb
        datemo.db))

(use '[clojure.pprint :refer [pprint]])

;; Install arb schema in scratch db.
(defn db-prep [f]
  (d/delete-database (get-db-uri))
  (init-db true)
  (-> (load-schema "schemas/arb.edn")
      (install-schema (get-conn)))
  (f))

(use-fixtures :each db-prep)

(defn parse [response]
  (-> (:body response) (json/parse-string true)))

(defmacro prep-request [method path data]
  `(-> (request ~method ~path (json/generate-string ~data))
       (header "Content-Type" "application/json; charset=utf-8")))

(defmacro doc-tx-spec [id title doctype tag value]
  [{:arb/id id
    :arb/doctype (keyword "doctype" doctype)
    :arb/metadata {:metadata/html-tag tag :metadata/title title}
    :arb/value {:content/text value}}])

(deftest test-get-root
  (testing "GET /"
    (let [response (app (request :get "/"))]
      (is (= (:status response) 200))
      (is (= (json/parse-string (:body response) true)
             {:_links {:documents {:href "/docs"}}})))))

(deftest test-get-latest
  (testing "GET /latest?page=1 with one note in db"
    (let [id (d/squuid)
          url (str "/latest?page=1")
          doc (doc-tx-spec id "A title" "note" :p "note1")
          tx (d/transact (get-conn) doc)
          response (app (request :get url))]
      (is (= {:_links {:self {:href "/latest"}}
              :_embedded [{:_links {:self {:href (apply str "/documents/" (str id))}}
                           :id (str id)
                           :title "A title"
                           :html "<p>note1</p>"}]}
             (parse response)))))

  (testing "GET /latest (no query params)"
    (let [url "/latest"
          response (app (request :get url))]
      (is (= 200
             (:status response))
          (= 1
             (count (->> (parse response) (:_embedded)))))))

  (testing "GET /latest?page=2 with one note in db"
    (let [url (str "/latest?page=2")
          response (app (request :get url))]
      (is (= 404
             (:status response)))))

  (testing "GET /latest?page=2 with 50 notes in db"
    (let [url "/latest?page=2"
          ids (mapv (fn [x] (d/squuid)) (range 50))
          doc-specs (mapv #(first (doc-tx-spec % "A title" "note" :p "test")) ids)
          tx (d/transact (get-conn) doc-specs)
          response (app (request :get url))]
      (is (= 200 (:status response)))
      (is (= 20 (->> (parse response) (:_embedded) (count))))))

  (testing "GET /latest doctype parameter"
    (let [url "/latest?doctype=essay"
          ids (mapv (fn [x] (d/squuid)) (range 5))
          doc-specs (mapv #(first (doc-tx-spec % "A title" "essay" :p "test")) ids)
          tx (d/transact (get-conn) doc-specs)
          response (app (request :get url))]
      (is (= 5
             (->> (parse response) (:_embedded) (count))))))

  (testing "GET /latest perpage parameter"
    (let [url "/latest?perpage=3"
          response (app (request :get url))]
      (is (= 3 (->> (parse response) (:_embedded) (count)))))))

(deftest test-get-document-by-id
  (testing "GET /documents/:id"
    (def id (d/squuid))
    (try
      (d/transact (get-conn) [{:arb/id id
                               :arb/value {:content/text "test"}
                               :arb/metadata {:metadata/html-tag :p
                                              :metadata/title "A title"}}])
      (catch Exception e (.getMessage e)))
    (let [response (app (request :get (str "/documents/" id)))]
      (is (= 200 (:status response)))
      (is (= {:_links {:self {:href (str "/documents/" id) } }
              :_embedded {:id (str id)
                          :title "A title"
                          :html "<p>test</p>"}}
             (-> (parse response)))))))

(deftest test-put-document
    (testing "with single node doc"
      (let [id (d/squuid)
            tx-spec (-> (html->tx
                          "<div>test</div>"
                          {:metadata/title "A title"})
                        (into {:arb/id id})
                        (into {:arb/doctype :doctype/note}))
            data {:doc-string "replaced" :title "A new title" :doctype "note"}
            tx-result (d/transact (get-conn) [tx-spec])
            response (app (prep-request :put (str "/documents/" id) data))]
          (is (= 202 (:status response)))
          (is (= {:_links {:self (str "/documents/" id)}
                  :_embedded {:id (str id)
                              :title "A new title"
                              :doctype "note"
                              :html "<p>replaced</p>"}}
                 (-> (parse response))))))

    (testing "with multilevel nodes at top level"
      (let [id (d/squuid)
            tx-spec (-> (html->tx "<div>test</div><div>test2</div>")
                        (into {:arb/id id})
                        (into {:arb/title "A title"})
                        (into {:arb/doctype :doctype/note}))
            data {:doc-string "replaced" :title "A new title" :doctype "note"}
            tx-result (d/transact (get-conn) [tx-spec])
            response (app (prep-request :put (str "/documents/" id) data))]
          (is (= 202 (:status response)))
          (is (= {:_links {:self (str "/documents/" id)}
                  :_embedded {:id (str id)
                              :title "A new title"
                              :doctype "note"
                              :html "<p>replaced</p>"}}
                 (-> (parse response)))))))

(deftest test-post-document
  ;; TODO: Add tests for error case.
  (testing "POST /documents"
    (let [data (->> (apply str "# Title  \nParagraph")
                    (array-map :doc-string)
                    (into {:doctype "note"}))
          response (app (prep-request :post "/documents" data))
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
      (is (= "<div><h1>Title</h1><p>Paragraph</p></div>"
             (-> (parse response)
                 (:_embedded)
                 (:html)))))))

(deftest test-not-found-route
  (testing "not-found route"
    (let [response (app (request :get "/bogus/route"))]
      (is (= (:status response) 404))
      (is (= (:body response) "Not found")))))
