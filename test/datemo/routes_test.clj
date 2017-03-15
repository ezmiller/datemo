(ns datemo.routes-test
  (:require [cheshire.core :as json :refer [parse-string]]
            [datomic.api :as d])
  (:use clojure.test
        ring.mock.request
        markdown.core
        datemo.routes
        datemo.db))

(use '[clojure.pprint :refer [pprint]])

(defn parse [response]
  (-> (:body response) (json/parse-string true)))

(deftest test-app
  (testing "GET /"
    (let [response (app (request :get "/"))]
      (is (= (:status response) 200))
      (is (= (json/parse-string (:body response) true)
             {:_links {:documents {:href "/docs"}}}))))

  (testing "GET /documents/:id"
    (def id (d/squuid))
    (try
      (d/transact conn [{:arb/id id
                         :arb/metadata {:metadata/html-tag :p}
                         :arb/value {:content/text "test"}}])
      (catch Exception e (.getMessage e)))
    (let [response (app (request :get (str "/documents/" id)))]
      (is (= 302 (:status response)))
      (is (= {:_links {:self (str "/documents/" id)}
              :_embedded {:id (str id)
                          :html "<p>test</p>"}}
             (-> (parse response))))))

  (testing "POST /documents"
    (def data
      (array-map :doc-string (md-to-html-string "# Title  \nParagraph")))
    (let [response (app (request :post "/documents" data))]
      (is (= 201 (:status response)))
      (is (= {"Content-Type" "application/hal+json; charset=utf-8"}
             (:headers response)))
      (is (= "/documents/"
             (-> (parse response)
                 (:_links)
                 (->> (:self) (re-find #"/documents/")))))
      (is (= true (-> (parse response) (:_embedded) (contains? :id))))
      (is (= "<div><h1>Title</h1><p>Paragraph</p></div>"
             (-> (parse response)
                 (:_embedded)
                 (:html))))))

  (testing "not-found route"
    (let [response (app (request :get "/bogus/route"))]
      (is (= (:status response) 404))
      (is (= (:body response) "Not found")))))
