(ns datemo.routes-test
  (:require [cheshire.core :as json :refer [parse-string]])
  (:use clojure.test
        ring.mock.request
        datemo.routes))

(deftest test-app
  (testing "GET /"
    (let [response (app (request :get "/"))]
      (is (= (:status response) 200))
      (is (= (json/parse-string (:body response) true)
             {:_links {:documents {:href "/docs"}}}))))

  (testing "not-found route"
    (let [response (app (request :get "/bogus/route"))]
      (is (= (:status response) 404))
      (is (= (:body response) "Not found")))))
