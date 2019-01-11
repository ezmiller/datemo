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

(defn walk-hiccup-fn [wrap-node process-node]
  (fn walk [hiccup]
    (let [[tag _ & nodes] hiccup]
      (wrap-node tag
                 (apply vector (for [node nodes]
                                 (if (string? node)
                                   (process-node node)
                                   (walk node))))))))

(defn hiccup->arb [hiccup]
  (let [process-node #(identity %)
        wrap-node    #(vec (concat [:arb {:original-tag %1}] %2))
        convert      (walk-hiccup-fn wrap-node process-node)]
    (convert hiccup)))

(defn walk-tx-fn [wrap-node process-node]
  (fn walk [tx]
    (let [{metadata :arb/metadata, nodes :arb/value} tx]
      (clojure.pprint/pprint {:metadata metadata, :nodes nodes})
      (wrap-node
       metadata
       (apply vector (for [node nodes]
                       (if (:content/text node)
                         (process-node node)
                         (walk node))))))))

(defn tx->arb [tx]
  (let [process-node #(:content/text %)
        wrap-node    #(vec
                       (concat
                        [:arb {:original-tag (get-in-metadata :metadata/html-tag %1)}]
                        %2))
        convert      (walk-tx-fn wrap-node process-node)]
    (convert tx)))

(defn tx->html [tx]
  (-> tx (tx->arb) (arb->hiccup) (html)))

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

(defn html->hiccup
  ([html] (as-hiccup (parse html)))
  ([html as-fragment]
   (if (false? as-fragment)
     (html->hiccup html)
     (let [result (map as-hiccup (parse-fragment html))]
       (if (not= 1 (count result))
         (into [:div {}] result)
         (first result))))))

(defn html->arb
  ([html] (html->arb html true))
  ([html as-fragment]
   (if (false? as-fragment)
    (hiccup->arb (html->hiccup html)))
    (hiccup->arb (html->hiccup html as-fragment))))

(defn html->tx [html & metadata]
  (let [tx (-> html (html->arb) (arb->tx))]
    (assoc tx :arb/metadata (into (:arb/metadata tx) metadata))))

