(defproject datemo "0.1.3"
  :description "Document server backed by datomic"
  :url "https://github.com/ezmiller/datemo"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  ;; :pedantic? :warn
  :dependencies [[clj-time "0.14.4"]
                 [commons-codec "1.11"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]
                 [com.datomic/client-cloud "0.8.52"]
                 ;; jetty-server is needed to resolve dependency conflict
                 ;; between jetty and com.datomic/client-cloud.
                 ;; See https://forum.datomic.com/t/dependency-conflict-with-ring-jetty/447
                 [org.eclipse.jetty/jetty-server "9.3.7.v20160115"]
                 [environ "1.1.0"]
                 [cheshire "5.8.0"]
                 [ring "1.7.0-RC1"]
                 [ring/ring-defaults "0.2.3"]
                 [ring/ring-json "0.4.0"]
                 [ring-cors "0.1.9"]
                 [compojure "1.6.1"]
                 [hickory "0.7.0"]
                 [markdown-clj "0.9.94"]]
  :plugins [[lein-ring "0.12.4"]
            [lein-environ "1.1.0"]
            [lein-pprint "1.1.2"]
            [lein-ancient "0.6.15"]
            [com.jakemccrary/lein-test-refresh "0.18.1"]]
  :ring {:init datemo.init/init
         :handler datemo.routes/handler
         :nrepl {:start? true
                 :port 9998}}
  :test-paths ["test"]
  :profiles {:production {:env {:db-name "production"}
                  :dependencies [[javax.serverlet/servlet-api "2.5"]
                                 [ring/ring-mock "0.3.2"]]}
             :dev {:env {:db-name "development"}
                   :dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring/ring-mock "0.3.2"]]}
             :test {:env {:testing true}
                    :dependencies [[javax.servlet/servlet-api "2.5"]
                                   [ring/ring-mock "0.3.2"]]}})
