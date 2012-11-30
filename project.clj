(defproject lupapalvelu "0.1.0-SNAPSHOT"
  :description "lupapalvelu"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [noir "1.3.0-beta10" :exclusions [org.clojure/clojure]]
                 [com.novemberain/monger "1.4.0"]
                 [enlive "1.0.1" :exclusions [org.clojure/clojure]]
                 [org.clojure/tools.nrepl "0.2.0-RC1"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [clj-http "0.5.8"]
                 [digest "1.4.2"]
                 [clj-time "0.4.4"]
                 [com.draines/postal "1.9.1"]
                 [org.clojure/data.xml "0.0.6"]
                 [fi.sito/oskari "0.9.2"]]
  :profiles {:dev {:dependencies [[midje "1.4.0" :exclusions [org.clojure/clojure]]
                                  [clj-webdriver "0.6.0-beta2" :exclusions [cheshire/cheshire]]]
                   :plugins [[lein-midje "2.0.1"]
                             [lein-buildid "0.1.0"]
                             [lein-nitpicker "0.2.0"]]}
             :itest {:test-paths ^:replace ["itest"]}
             :ftest {:test-paths ^:replace ["ftest"]}}

  :nitpicker {:exts ["clj" "js" "html"]
              :excludes [#"\/jquery\/" #"\/theme\/default\/" #"\/public\/lib\/" #"openlayers"]}

  :repositories [["solita-archiva" {:url "http://mvn.solita.fi/archiva/repository/solita"
                                    :checksum :ignore}]]
  :plugin-repositories ["solita-archiva" {:url "http://mvn.solita.fi/archiva/repository/solita"
                                          :checksum :ignore}]
  :main lupapalvelu.server
  :repl-options {:init-ns lupapalvelu.server}
  :min-lein-version "2.0.0")
