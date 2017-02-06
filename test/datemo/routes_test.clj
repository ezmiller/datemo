(ns datemo.routes-test
  (:require [clojure.pprint :as pp])
  (:use clojure.test
        ring.mock.request
        datemo.routes))

(deftest test-app
  (testing "not-found route"
    (let [response (app (request :get "/bogus/route"))]
      (is (= (:status response) 404))
      (is (= (:body "Not found"))))))
