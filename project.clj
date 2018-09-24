(defproject datemo "0.2"
  :description "Document server backed by datomic"
  :url "https://github.com/ezmiller/datemo"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  ;; :pedantic? :warn
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :plugins [[lein-tools-deps "0.4.1"]
            [lein-ring "0.12.4"]
            [lein-environ "1.1.0"]
            [lein-pprint "1.1.2"]
            [lein-ancient "0.6.15"]
            [com.jakemccrary/lein-test-refresh "0.18.1"]]
  ;; Need clj-time declared here or get an error. I think the package is used
  ;; in the middleware.
  :dependencies [[clj-time "0.4.4"]]
  :lein-tools-deps/config {:config-files ["./deps.edn"]}
  :ring {:init datemo.init/init
         :handler datemo.routes/handler
         :nrepl {:start? true
                 :port 9998}}
  :test-paths ["test"]
  :profiles {:production {:env {:db-name "production"}}
             :dev {:env {:db-name "development"}}
             :test {:env {:testing true}}})
