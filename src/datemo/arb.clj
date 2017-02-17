(ns datemo.arb
  (:require [clojure.pprint :refer [pprint]])
  (:use hickory.core))

(defn html->hiccup [html]
  (as-hiccup (parse html)))

(defn hiccup->arb [hiccup]
  (let [[tag attrs & rest] (first hiccup)
        rest-count (count rest)]
    (if (or (= 0 rest-count) (and (= 1 rest-count) (string? (first rest))))
      [:arb {:original-tag tag} (first rest)]
      (loop [arb [:arb {:original-tag tag}]
             forms rest]
        (if (= 1 (count forms))
          (conj arb (if (string? (first forms))
                      (first forms)
                      (hiccup->arb forms)))
          (recur (conj arb (if (string? (first forms))
                             (first forms)
                             (hiccup->arb (list (first forms)))))
                 (next forms)))))))

(defn html->arb [html]
  (hiccup->arb (html->hiccup html)))
