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
  (testing "html->hiccup"
    (testing "given two sibling tags, will wrap in div"
      (is (=
           [:div {} [:div {} "A"] [:div {} "B"]]
           (html->hiccup "<div>A</div><div>B</div>" true)))))

  (testing "html->arb"
    (testing "with empty div"
      (is (=
           [:arb {:original-tag :div} nil]
           (html->arb (html [:div])))))
    (testing "with div with text"
      (is (=
           [:arb {:original-tag :div} "Text"]
           (html->arb (html [:div {} "Text"])))))
    (testing "parent with two siblings"
      (is (=
           [:arb {:original-tag :div}
            [:arb {:original-tag :div} "A"]
            [:arb {:original-tag :div} "B"]]
           (html->arb (html [:div {} [:div "A"] [:div "B"]])))))
    (testing "nested tags"
      (is (=
           [:arb {:original-tag :div} "parent"
            [:arb {:original-tag :p} "child"]]
           (html->arb (html [:div "parent" [:p "child"]])))))
    (testing "with nested tag in middle of text"
      (is (=
            [:arb
             {:original-tag :p}
             "Paragraph with "
             [:arb {:original-tag :strong} "bold"]
             " text."]
            (html->arb
              (html [:p "Paragraph with " [:strong "bold"] " text."]))))))

  (testing "arb->tx"
    (testing "single arb node with nil value"
      (is (=
           {:arb/metadata [{:metadata/html-tag :div}]
            :arb/value [{:content/text nil}]}
           (arb->tx [:arb {:original-tag :div} nil]))))
    (testing "single arb node with text value"
      (is (=
           {:arb/metadata [{:metadata/html-tag :div}]
            :arb/value [{:content/text "Text"}]}
           (arb->tx [:arb {:original-tag :div} "Text"]))))
    (testing "arb with emphasis markup"
      (is (=
           {:arb/metadata [{:metadata/html-tag :div}],
            :arb/value [{:content/text "This is "}
                        {:arb/metadata [{:metadata/html-tag :em}],
                         :arb/value [{:content/text "italic"}]}
                        {:content/text "text"}]}
           (arb->tx [:arb
                     {:original-tag :div}
                     "This is " [:arb {:original-tag :em} "italic"] "text"]))))
    (testing "arb with nested arb"
      (is (=
           {:arb/metadata [{:metadata/html-tag :div}]
            :arb/value [{:arb/metadata [{:metadata/html-tag :h1}]
                         :arb/value [{:content/text "Section Title"}]}
                        {:arb/metadata [{:metadata/html-tag :p}]
                         :arb/value [{:content/text "paragraph"}]}]}
           (arb->tx [:arb {:original-tag :div}
                     [:arb {:original-tag :h1} "Section Title"]
                     [:arb {:original-tag :p} "paragraph"]])))))

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
    (testing "with single-level nested arb with multiple arb siblings"
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
                           :arb/value [{:content/text "paragraph"}]}]}))))
    (testing "with single-level nested arb with arb and :content/text siblings"
      (is (=
           [:arb
            {:original-tag :div}
            "Text with "
            [:arb {:original-tag :em} "emphasis"]
            " in between."]
           (tx->arb
             {:arb/metadata [{:metadata/html-tag :div}]
              :arb/value [{:content/text "Text with "}
                          {:arb/metadata [{:metadata/html-tag :em}]
                           :arb/value [{:content/text "emphasis"}]}
                          {:content/text " in between."}]})))))

  (testing "arb->hiccup"
    (testing "single node"
      (is (=
           [:div {} "text"]
           (arb->hiccup [:arb {:original-tag :div} "text"]))))
    (testing "with single branch"
      (is (=
           [:div {}
            [:p {} "paragraph"]]
           (arb->hiccup [:arb
                         {:original-tag :div}
                         [:arb {:original-tag :p} "paragraph"]]))))
    (testing "two branches"
      (is (=
           [:div {}
            [:p {} "paragraph1"]
            [:p {} "paragraph2"]]
           (arb->hiccup [:arb
                         {:original-tag :div}
                         [:arb {:original-tag :p} "paragraph1"]
                         [:arb {:original-tag :p} "paragraph2"]]))))
    (testing "single branch 2-levels deep"
      (is (=
           [:div {}
            [:div {}
             [:p {} "paragraph"]]]
           (arb->hiccup [:arb
                         {:original-tag :div}
                         [:arb {:original-tag :div}
                          [:arb {:original-tag :p} "paragraph"]]]))))
    (testing "single branches 1-level deep with nodes of different type"
      (is (=
           [:div {}
            [:p {} "paragraph" [:strong {} "with bold"] "in the middle"]]
           (arb->hiccup [:arb
                         {:original-tag :div}
                         [:arb {:original-tag :p}
                          "paragraph"
                          [:arb {:original-tag :strong} "with bold"]
                          "in the middle"]]))))))
