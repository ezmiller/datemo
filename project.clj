(defproject datemo "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :exclusions [[commons-codec]]
  :dependencies [[commons-codec "1.6"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.395"]
                 [com.datomic/datomic-pro "0.9.5544"]
                 [environ "1.1.0"]
                 [cheshire "5.7.0"]
                 [ring "1.5.1"]
                 [ring/ring-defaults "0.2.3"]
                 [ring/ring-json "0.4.0"]
                 [ring-cors "0.1.9"]
                 [compojure "1.5.2"]
                 [hickory "0.7.0"]
                 [markdown-clj "0.9.94"]]
  :plugins [[lein-ring "0.11.0"]
            [lein-environ "1.1.0"]
            [com.jakemccrary/lein-test-refresh "0.18.1"]]
  :ring {:handler datemo.routes/app
         :nrepl {:start? true
                 :port 9998}}
  :test-paths ["test"]
  :profiles
  {:dev {:env {:database-uri "datomic:dev://localhost:4334/datemo"}
         :dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}
   :test {:env {:use-scratch-db "true"}
          :dependencies [[javax.servlet/servlet-api "2.5"]
                         [ring/ring-mock "0.3.0"]]}})
