(ns datemo.arb-test
  (:require [markdown.core :refer [md-to-html-string]])
  (:use clojure.test
        hiccup.core
        datemo.arb))

(deftest test-arb
  (testing "html->arb"
    ;; these two tests need to be adapted to full document output
    ;; that comes from hickory when using the parse instead of 
    ;; parse-fragment method. need to decide which is  better to use.
    (is (=
         (html->arb (html [:div]))
         [:arb {:original-tag :html}
          [:arb {:original-tag :head} nil]
          [:arb {:original-tag :body}
           [:arb {:original-tag :div} nil]]]))
    (is (=
         (html->arb (html [:div {} "Text"]))
         [:arb {:original-tag :html}
          [:arb {:original-tag :head} nil]
          [:arb {:original-tag :body}
           [:arb {:original-tag :div} "Text"]]]))
    (is (=
         (html->arb (html [:div "A"] [:div "B"]))
         [:arb {:original-tag :html}
          [:arb {:original-tag :head} nil]
          [:arb {:original-tag :body}
           [:arb {:original-tag :div} "A"]
           [:arb {:original-tag :div} "B"]]]))
    (is (=
         (html->arb (html [:h1 "Section"] [:p "This is a paragraph."]))
         [:arb {:original-tag :html}
          [:arb {:original-tag :head} nil]
          [:arb {:original-tag :body}
           [:arb {:original-tag :h1} "Section"]
           [:arb {:original-tag :p} "This is a paragraph."]]]))
    (is (= (html->arb (html [:p "Paragraph with " [:strong "bold"] " text."]))
           [:arb {:original-tag :html}
            [:arb {:original-tag :head} nil]
            [:arb {:original-tag :body}
             [:arb {:original-tag :p}
              "Paragraph with "
              [:arb {:original-tag :strong} "bold"]
              " text."]]]))))


