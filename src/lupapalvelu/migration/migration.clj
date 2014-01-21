(ns lupapalvelu.migration.migration
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.migration.core :refer :all]
            [lupapalvelu.migration.migrations]
            [slingshot.slingshot :refer [try+ throw+]]
            [clojure.stacktrace :refer [print-cause-trace]]
            [clojure.pprint :refer [pprint]])
  (:import [java.text SimpleDateFormat]
           [java.util Date]))


(def ^:private time-formatter (SimpleDateFormat. "yyyy/MM/dd HH:mm:ss"))

(defn- time->str [t]
  (->> t Date. (.format time-formatter)))

(defn- show-help []
  (println "Migrations commands:")
  (println "  list ............... List known migrations")
  (println "  hist ............... Show migration executions")
  (println "  hist -l ............ Show migration executions in long format")
  (println "  update ............. Execute migrations that have not been executed successfully")
  (println "  run [name...] ...... Execute migrations by name(s)")
  (println "  run-all ............ Execute all migrations"))

(defn- rtfm []
  (println "What? I dont even...")
  (show-help)
  1)

(defn- list-migrations []
  (doseq [m (sort-by :id (vals @migrations))]
    (println (:name m)))
  (flush))

(def ^:private status {true "SUCCESS" false "FAIL"})

(defn- show-history [long-format]
  (doseq [r (migration-history)]
    (printf "%s: %s: %s%n" (time->str (:time r)) (:name r) (status (:ok r)))
    (when long-format
      (if (:ok r)
        (pprint (:result r))
        (println (:error r)))
      (println))))

(defn- run-migration! [migration-name]
  (printf "Executing migration '%s': " migration-name)
  (let [result (execute-migration! migration-name)]
    (if (:ok result)
      (println "Successful")
      (do
        (println "Failure:")
        (println result)
        (throw+ result))))
  (flush))

(defn- run-migrations! [migration-names]
  (try+
    (dorun (map run-migration! migration-names))
    (println "All migrations executed successfully")
  (catch string? message (println "Execution terminated by failure:" message) 1)
  (catch map? result (println "Migration execution failure") 1)
  (catch Exception e (println "Execution terminated by failure") (print-cause-trace e) 1)))

(defn update! []
  (run-migrations! (map :name (unexecuted-migrations))))

(defn -main [& [action & args]]
  (mongo/connect!)
  (cond
    (nil? action)         (show-help)
    (= action "list")     (list-migrations)
    (= action "hist")     (show-history (= "-l" (first args)))
    (= action "run")      (if (seq args) (run-migrations! args) (rtfm))
    (= action "run-all")  (run-migrations! (map :name (->> @migrations vals (sort-by :order))))
    (= action "update")   (update!)
    :else                 (rtfm)))
