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
(-> (load-schema "schemas/arb.edn")
    (install-schema conn))

(defn parse [response]
  (-> (:body response) (json/parse-string true)))

(defmacro prep-request [method path data]
  `(-> (request ~method ~path (json/generate-string ~data))
       (header "Content-Type" "application/json; charset=utf-8")))

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

  (testing "PUT /documents/:id"
    (testing "with single node doc"
      (let [id (d/squuid)
            tx-spec (into {:arb/id id} (html->tx "<div>test</div>"))
            data {:doc-string "<p>replaced</p>"}
            tx-result (d/transact conn [tx-spec])
            response (app (prep-request :put (str "/documents/" id) data))]
          (is (= 202 (:status response)))
          (is (= {:_links {:self (str "/documents/" id)}
                  :_embedded {:id (str id)
                              :html "<p>replaced</p>"}}
                 (-> (parse response))))))
    (testing "with multilevel nodes at top level"
      (let [id (d/squuid)
            tx-spec (into {:arb/id id}
                          (html->tx "<div>test</div><div>test2</div>"))
            data {:doc-string "<p>replaced</p>"}
            tx-result (d/transact conn [tx-spec])
            response (app (prep-request :put (str "/documents/" id) data))]
          (is (= 202 (:status response)))
          (is (= {:_links {:self (str "/documents/" id)}
                  :_embedded {:id (str id)
                              :html "<p>replaced</p>"}}
                 (-> (parse response)))))))

  (testing "POST /documents"
    (let [data (->> (apply str "# Title  \nParagraph")
                    (array-map :doc-string)
                    (into {:doctype "note"}))
          response (app (prep-request :post "/documents" data))]
      (is (= 201 (:status response)))
      (is (= {"Content-Type" "application/hal+json; charset=utf-8"}
             (:headers response)))
      (is (= "/documents/"
             (-> (parse response)
                 (:_links)
                 (->> (:self) (re-find #"/documents/")))))
      (is (= true (-> (parse response) (:_embedded) (contains? :id))))
      (is (= "note"
             (-> (parse response) (:_embedded) (:doctype))))
      (is (= "<div><h1>Title</h1><p>Paragraph</p></div>"
             (-> (parse response)
                 (:_embedded)
                 (:html))))))

  (testing "not-found route"
    (let [response (app (request :get "/bogus/route"))]
      (is (= (:status response) 404))
      (is (= (:body response) "Not found")))))
