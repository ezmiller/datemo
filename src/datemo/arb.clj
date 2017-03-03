(ns datemo.arb
  (:require [clojure.pprint :refer [pprint]])
  (:use hickory.core))

(defn html->hiccup
  ([html] (as-hiccup (parse html)))
  ([html as-fragment]
   (if (false? as-fragment)
     (html->hiccup html)
     (map as-hiccup (parse-fragment html)))))

(defn hiccup->arb [hiccup]
  (if (string? (first hiccup))
    (first hiccup)
    (let [[tag attrs & rest] (first hiccup)
           rest-count (count rest)]
      (if (or (= 0 rest-count) (and (= 1 rest-count) (string? (first rest))))
        [:arb {:original-tag tag} (first rest)]
        (loop [arb [:arb {:original-tag tag}]
               forms rest]
          (if (= 1 (count forms))
            (conj arb (hiccup->arb forms))
            (recur (conj arb (hiccup->arb (list (first forms))))
                   (next forms))))))))

(defn html->arb
  ([html] (html->arb html false))
  ([html as-fragment]
   (if (false? as-fragment)
    (hiccup->arb (html->hiccup html)))
    (hiccup->arb (html->hiccup html as-fragment))))

(defn arb->tx [arb]
  (let [[arb-tag metadata & value] arb]
    (if (string? (first value))
      {:arb/metadata [{:metadata/html-tag (metadata :original-tag)}]
       :arb/value [{:content/text (first value)}]}
      (loop [values []
             items value]
        (if (= 1 (count items))
          {:arb/metadata [{:metadata/html-tag (metadata :original-tag)}]
           :arb/value (conj values (arb->tx (first items)))}
          (recur (conj values (arb->tx (first items))) (next items)))))))
