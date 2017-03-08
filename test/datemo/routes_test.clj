(ns datemo.routes-test
  (:require [cheshire.core :as json :refer [parse-string]])
  (:use clojure.test
        ring.mock.request
        markdown.core
        datemo.routes))

(use '[clojure.pprint :refer [pprint]])

(defn parse [response]
  (-> (:body response) (json/parse-string true)))

(deftest test-app
  (testing "GET /"
    (let [response (app (request :get "/"))]
      (is (= (:status response) 200))
      (is (= (json/parse-string (:body response) true)
             {:_links {:documents {:href "/docs"}}}))))

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
