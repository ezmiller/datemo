(ns datemo.arb-test
  (:require [markdown.core :refer [md-to-html-string]])
  (:use clojure.test
        hiccup.core
        datemo.arb))

(defn long-str [& strings] (clojure.string/join "\n" strings))

(deftest test-arb
  (testing "html->arb"
    ;; these two tests need to be adapted to full document output
    ;; that comes from hickory when using the parse instead of 
    ;; parse-fragment method. need to decide which is  better to use.
    ;; (is (= (html->arb "<div></div>") [:arb {:original-tag :div} nil]))
    ;; (is (= (html->arb "<div>Text</div>") [:arb {:original-tag :div} "Text"]))
    (is (=
         (html->arb "<div>A</div><div>B</div>")
         [:arb {:original-tag :html}
          [:arb {:original-tag :head} nil]
          [:arb {:original-tag :body}
           [:arb {:original-tag :div} "A"]
           [:arb {:original-tag :div} "B"]]]))
  ))


