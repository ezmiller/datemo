(defproject datemo "0.1.3"
  :description "Document server backed by datomic"
  :url "https://github.com/ezmiller/datemo"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  ;; :exclusions [commons-codec]
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :creds :gpg}}
  :dependencies [[clj-time "0.13.0"]
                 [commons-codec "1.10"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]
                 [com.datomic/client-cloud "0.8.50"]
                 [environ "1.1.0"]
                 [cheshire "5.7.0"]
                 [ring "1.6.3"
                  :exclusions [ring/ring-jetty-adapter]]
                 [ring/ring-defaults "0.2.3"]
                 [ring/ring-json "0.4.0"]
                 [ring-cors "0.1.9"]
                 [compojure "1.5.2"]
                 [hickory "0.7.0"]
                 [markdown-clj "0.9.94"]]
  :plugins [[lein-ring "0.11.0"]
            [lein-environ "1.1.0"]
            [lein-pprint "1.1.2"]
            [com.jakemccrary/lein-test-refresh "0.18.1"]]
  :ring {:init datemo.init/init
         :handler datemo.routes/handler
         :nrepl {:start? true
                 :port 9998}}
  :test-paths ["test"]
  :profiles
  {:production {:env {:db-name "production"}
                :dependencies [[javax.serverlet/servlet-api "2.5"]
                               [ring/ring-mock "0.3.0"]]}
   :dev {:env {:db-name "development"}
         :dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}
   :test {:env {:db-name "test"}
          :dependencies [[javax.servlet/servlet-api "2.5"]
                         [ring/ring-mock "0.3.0"]]}})
