(ns lupapalvelu.xml.emit
  (:require [clojure.data.xml :refer :all]))

(declare element-to-xml)

(defn- create-element-hierarcy [data model ns]
  (element (if ns
             (str ns (:tag model))
             (:tag model)) (:attr model)
           (if (:child model)
             (map #(element-to-xml data % ns) (:child model))
             (str data))))

(defn element-to-xml
  ([data model] (element-to-xml data model nil))
  ([data model prev-ns]
    (let [current-data ((:tag model) data)
          ns (if (:ns model)
               (:ns model)
               prev-ns)]
      (when (not (nil? current-data))
        (if (sequential? current-data)
          (for [item current-data]
            (create-element-hierarcy item model ns))
          (create-element-hierarcy current-data model ns))))))
