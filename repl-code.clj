;; For repl.

;; Sample arb doc in edn
;;
;; [{:arb/metadata [{:html-tag :body}]
;;   :arb/value [{:arb/metadata [{:html-tag :h1}]
;;                :arb/value [{:text "Title"}]}
;;               {:arb/metadata [{:html-tag :p}]
;;                :arb/value [{:text "This is a paragraph."}]}]}]


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




