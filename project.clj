(defproject lupapalvelu "0.1.0-SNAPSHOT"
  :description "lupapalvelu"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [noir "1.3.0-beta10" :exclusions [org.clojure/clojure]]
                 [com.novemberain/monger "1.1.2"]
                 [enlive "1.0.1"]
                 [org.clojure/tools.nrepl "0.2.0-beta9"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [clj-http "0.5.3"]]
  :test-paths ["test" "itest"]
  :profiles {:dev {:dependencies [[midje "1.4.0" :exclusions [org.clojure/clojure]]
                                  [clj-webdriver "0.6.0-alpha11"]]
                   :plugins [[lein-midje "2.0.0-SNAPSHOT"]
                             [lein-buildid "0.1.0-SNAPSHOT"]]}}
  :main lupapalvelu.server
  :repl-options {:init-ns lupapalvelu.server}
  :min-lein-version "2.0.0")
