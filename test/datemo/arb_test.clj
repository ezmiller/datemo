(ns datemo.arb-test
  (:require [markdown.core :refer [md-to-html-string]]
            [clojure.pprint :refer [pprint]])
  (:use clojure.test
        hiccup.core
        datemo.arb))

(defn build-arb [hiccup-nodes]
  (loop [nodes hiccup-nodes
         arb-body [:arb {:original-tag :body}]]
    (if (= 1 (count nodes))
      [:arb {:original-tag :html}
         [:arb {:original-tag :head} nil]
         (conj arb-body (first nodes))]
      (recur (next nodes) (conj arb-body (first nodes))))))

(deftest test-arb
  (testing "html->arb"
    (is (=
         (html->arb (html [:div]))
         (build-arb [[:arb {:original-tag :div} nil]])))
    (is (=
         (html->arb (html [:div {} "Text"]))
         (build-arb [[:arb {:original-tag :div} "Text"]])))
    (is (=
         (html->arb (html [:div "A"] [:div "B"]))
         (build-arb [[:arb {:original-tag :div} "A"]
                     [:arb {:original-tag :div} "B"]])))
    (is (=
         (html->arb (html [:h1 "Section"] [:p "This is a paragraph."]))
         (build-arb [[:arb {:original-tag :h1} "Section"]
                     [:arb {:original-tag :p} "This is a paragraph."]])))
    (is (= (html->arb (html [:p "Paragraph with " [:strong "bold"] " text."]))
           (build-arb [[:arb {:original-tag :p}
                       "Paragraph with "
                       [:arb {:original-tag :strong} "bold"]
                       " text."]])))))


