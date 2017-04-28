;; For repl.

;; Sample arb doc in edn
;;
;; [{:arb/metadata [{:html-tag :body}]
;;   :arb/value [{:arb/metadata [{:html-tag :h1}]
;;                :arb/value [{:text "Title"}]}
;;               {:arb/metadata [{:html-tag :p}]
;;                :arb/value [{:text "This is a paragraph."}]}]}]


;; for client api
(require '[clojure.core.async :refer [<!!]]
         '[datomic.client :as client])

(def conn
  (<!! (client/connect
        {:db-name "datemo"
         :account-id client/PRO_ACCOUNT
         :secret "datemo"
         :region "none"
         :endpoint "localhost:8998"
         :service "peer-server"
         :access-key "datemo"})))

(def db (client/db conn))


;; for datomic api
(require '[datomic.api :as d])

(def conn (d/connect "datomic:dev://localhost:4334/datemo"))
(def db-now (d/db conn))

;; for installing the schema
;; run lein-repl, then:
(use 'datemo.db)
(-> (load-schema "schemas/arb.edn")
    (install-schema (get-conn)))

