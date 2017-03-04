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

(deftest arb-tests
  (testing "html->arb"
    (testing "with empty div"
      (is (=
           (html->arb (html [:div]))
           (build-arb [[:arb {:original-tag :div} nil]]))))
    (testing "with div with text"
      (is (=
           (html->arb (html [:div {} "Text"]))
           (build-arb [[:arb {:original-tag :div} "Text"]]))))
    (testing "with two sibling tags"
      (is (=
           (html->arb (html [:div "A"] [:div "B"]))
           (build-arb [[:arb {:original-tag :div} "A"]
                       [:arb {:original-tag :div} "B"]]))))
    (testing "nested tags"
      (is (=
           (build-arb[[:arb {:original-tag :div} "parent"
                       [:arb {:original-tag :p} "child"]]])
           (html->arb (html [:div "parent" [:p "child"]])))))
    (testing "with nested tag in middle of text"
      (is (= (html->arb (html [:p "Paragraph with " [:strong "bold"] " text."]))
             (build-arb [[:arb {:original-tag :p}
                         "Paragraph with "
                         [:arb {:original-tag :strong} "bold"]
                         " text."]])))))

  (testing "arb->tx"
    (testing "with single arb node"
      (is (=
           {:arb/metadata [{:metadata/html-tag :div}]
            :arb/value [{:content/text "Text"}]}
           (arb->tx (hiccup->arb (list [:div {} "Text"]))))))
    (testing "arb with nested arb"
      (is (=
           {:arb/metadata [{:metadata/html-tag :div}]
            :arb/value [{:arb/metadata [{:metadata/html-tag :h1}]
                         :arb/value [{:content/text "Section Title"}]}
                        {:arb/metadata [{:metadata/html-tag :p}]
                         :arb/value [{:content/text "paragraph"}]}]}
           (arb->tx (hiccup->arb (list [:div {}
                                        [:h1 {} "Section Title"]
                                        [:p {} "paragraph"]])))))))

  (testing "tx->arb"
    (testing "with single node"
      (is (=
           [:arb {:original-tag :div} "text"]
           (tx->arb
             {:arb/metadata [{:metadata/html-tag :div}]
              :arb/value [{:content/text "text"}]}))))

    (testing "with single-level nested arb"
      (is (=
           [:arb
            {:original-tag :div}
            [:arb {:original-tag :p} "paragraph"]]
           (tx->arb
             {:arb/metadata [{:metadata/html-tag :div}]
              :arb/value [{:arb/metadata [{:metadata/html-tag :p}]
                           :arb/value [{:content/text "paragraph"}]}]}))))

    (testing "with single-level nested arb with siblings"
      (is (=
           [:arb
            {:original-tag :div}
            [:arb {:original-tag :h1} "title"]
            [:arb {:original-tag :p} "paragraph"]]
           (tx->arb
             {:arb/metadata [{:metadata/html-tag :div}]
              :arb/value [{:arb/metadata [{:metadata/html-tag :h1}]
                           :arb/value [{:content/text "title"}]}
                          {:arb/metadata [{:metadata/html-tag :p}]
                           :arb/value [{:content/text "paragraph"}]}]}))))))


