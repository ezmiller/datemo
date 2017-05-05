(ns datemo.arb
  (:require [clojure.pprint :refer [pprint]])
  (:use hickory.core
        hiccup.core))

(defn html->hiccup
  ([html] (as-hiccup (parse html)))
  ([html as-fragment]
   (if (false? as-fragment)
     (html->hiccup html)
     (let [result (map as-hiccup (parse-fragment html))]
       (if (not= 1 (count result))
         (into [:div {}] result)
         (first result))))))

(defn hiccup->arb [hiccup]
  (let [[tag attrs & value] hiccup]
    (if (or (nil? value) (and (= 1 (count value) (string? (first value)))))
      [:arb {:original-tag tag} (first value)]
      (loop [values [], items value]
        (if (= 0 (count items))
          (into [:arb {:original-tag tag}] values)
          (if (string? (first items))
            (recur (conj values (first items)) (next items))
            (recur (conj values (hiccup->arb (first items))) (next items))))))))

(defn html->arb
  ([html] (html->arb html true))
  ([html as-fragment]
   (if (false? as-fragment)
    (hiccup->arb (html->hiccup html)))
    (hiccup->arb (html->hiccup html as-fragment))))

(defn arb->tx [arb]
  (let [[arb-tag metadata & value] arb]
    (if (or (string? (first value)) (nil? (first value)))
      {:arb/metadata [{:metadata/html-tag (metadata :original-tag)}]
       :arb/value [{:content/text (first value)}]}
      (loop [values []
             items value]
        (if (= 1 (count items))
          {:arb/metadata [{:metadata/html-tag (metadata :original-tag)}]
           :arb/value (conj values (arb->tx (first items)))}
          (recur (conj values (arb->tx (first items))) (next items)))))))

(defn html->tx [html & metadata]
  (let [tx (-> html (html->arb) (arb->tx))]
    (assoc tx :arb/metadata (into (:arb/metadata tx) metadata))))

(defn tx->arb [tx]
  (let [{metadata :arb/metadata value :arb/value} tx]
    (if (and (= 1 (count value)) (not (nil? (:content/text (first value)))))
      [:arb
       {:original-tag (:metadata/html-tag (first metadata))}
       (:content/text (first value))]
      (loop [arbs [], items value]
        (if (= 0 (count items))
          (into
            [:arb {:original-tag (:metadata/html-tag (first metadata))}]
            arbs)
          (recur (conj arbs (tx->arb (first items))) (next items)))))))

(defn arb->hiccup [arb]
  (let [[arb-tag metadata & value] arb]
    (if (and (string? (first value)) (= 1 (count value)))
      [(:original-tag metadata) {} (first value)]
      (loop [hiccups [], items value]
        (if (= 0 (count items))
          (into [(:original-tag metadata) {}] hiccups)
          (if (string? (first items))
            (recur (conj hiccups (first items)) (next items))
            (recur
              (conj hiccups (arb->hiccup (first items)))
              (next items))))))))

(defn tx->html [tx]
  (-> tx (tx->arb) (arb->hiccup) (html)))
