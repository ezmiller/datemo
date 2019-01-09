;; For repl, to get working with db.

(require '[datomic.client.api :as d])
(use 'datemo.db)
(init-client)
(connect "development") ;; can also connect "production"

;; At this point you can use the fns in datemo.db to work with the db.


