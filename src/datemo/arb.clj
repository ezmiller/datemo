(ns datemo.arb
  (:require [clojure.pprint :refer [pprint]])
  (:use hickory.core
        hiccup.core))

(defn arb? [coll]
  (and
    (instance? clojure.lang.PersistentArrayMap coll)
    (contains? coll :arb/value)))

(defn get-in-metadata [k metadata]
  (k (first (filter #(k %) metadata))))

(defn walk-arb-fn [wrap-node process-node]
  (fn walk [arb]
    (let [[_ metadata & nodes] arb]
      (wrap-node
       metadata
       (apply vector (for [node nodes]
                       (if (or (string? node) (nil? node))
                         (process-node node)
                         (walk node))))))))

(defn arb->tx [arb]
  (let [process-node #(hash-map :content/text %)
        wrap-node #(hash-map
                    :arb/metadata [{:metadata/html-tag (:original-tag %1)}]
                    :arb/value %2)
        convert (walk-arb-fn wrap-node process-node)]
    (convert arb)))

(defn arb->hiccup [arb]
  (let [proccess-node #(identity %)
        wrap-node #(vec (concat [(:original-tag %1) {}] %2))
        convert (walk-arb-fn wrap-node proccess-node)]
    (convert arb)))

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

(defn tx->arb [tx]
  (let [{metadata :arb/metadata value :arb/value} tx]
    (if (and (= 1 (count value)) (not (nil? (:content/text (first value)))))
      [:arb
       {:original-tag (get-in-metadata :metadata/html-tag metadata)}
       (:content/text (first value))]
      (loop [arbs [], items value]
        (if (= 0 (count items))
          (into
            [:arb {:original-tag (:metadata/html-tag (first metadata))}]
            arbs)
          (recur
            (conj arbs (if-not (arb? (first items))
                         (:content/text (first items)) ;; If it's not an arb it must be a :content/text
                         (tx->arb (first items)))) (next items)))))))

(defn html->tx [html & metadata]
  (let [tx (-> html (html->arb) (arb->tx))]
    (assoc tx :arb/metadata (into (:arb/metadata tx) metadata))))

(defn tx->html [tx]
  (-> tx (tx->arb) (arb->hiccup) (html)))
