(ns lupapalvelu.find-address
  (:use [monger.operators]
        [lupapalvelu.security :only [current-user]]
        [clojure.data.zip.xml :only [xml-> text]]
        [lupapalvelu.i18n :only [*lang*]])
  (:require [clojure.string :as s]
            [monger.query :as q]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.wfs :as wfs]))

;; Should be in util or sumthin...

(defn uniq-by
  [by coll]
  (let [step (fn step [xs prev]
               (lazy-seq
                 ((fn [[f :as xs] prev]
                    (when-let [s (seq xs)]
                      (if (= (by f) prev) 
                        (recur (rest s) prev)
                          (cons f (step (rest s) (by f))))))
                   xs prev)))]
    (step coll nil)))

(defn- set-kind [kind]
  (fn [result] (assoc result :kind kind)))

;;;
;;; All search-... functions return a sequence of items, where each item is a map
;;; of (at least) following keys:
;;;   :kind       One of :poi, :property-id, or :address
;;;   :location   Map with :x and :y
;;;

(defn search-property-id [property-id]
  ; FIXME: Fix result to property format
  (map (set-kind :property-id)
       (wfs/point-by-property-id property-id)))

(defn search-poi-or-street [poi]
  (map (set-kind :poi)
       (q/with-collection "poi"
         (q/find {:name {$regex (str \^ (s/lower-case poi))}})
         (q/limit 10))))

(defn search-street-with-number [street number]
  ; FIXME: Fix result to property format
  (wfs/post wfs/maasto
    (wfs/query {"typeName" "oso:Osoitenimi"}
      (wfs/sort-by ["oso:kuntanimiFin"])
      (wfs/filter
        (wfs/and
          (wfs/property-is-like "oso:katunimi"   (str street "*"))
          (wfs/property-is-like "oso:katunumero" (str number "*"))
          (wfs/property-is-less "oso:jarjestysnumero" "10"))))))

(defn search-street-with-city [street city]
  (let [data (wfs/post wfs/maasto
               (wfs/query {"typeName" "oso:Osoitenimi"}
                 (wfs/sort-by ["oso:katunimi" "oso:katunumero"])
                 (wfs/filter
                   (wfs/and
                     (wfs/property-is-like "oso:katunimi" (str street "*"))
                     (wfs/or
                       (wfs/property-is-like "oso:kuntanimiFin" (str city "*"))
                       (wfs/property-is-like "oso:kuntanimiSwe" (str city "*")))
                     (wfs/property-is-less "oso:jarjestysnumero" "10")))))]
    (when data
      (map (comp (set-kind :address) wfs/feature-to-address)
           (uniq-by :street data)))))

(defn search-address [street number city]
  (let [data (wfs/post wfs/maasto
               (wfs/query {"typeName" "oso:Osoitenimi"}
                 (wfs/sort-by ["oso:katunimi" "oso:katunumero"])
                 (wfs/filter
                   (wfs/and
                     (wfs/property-is-like "oso:katunimi" (str street "*"))
                     (wfs/property-is-like "oso:katunumero" (str number "*"))
                     (wfs/or
                       (wfs/property-is-like "oso:kuntanimiFin" (str city "*"))
                       (wfs/property-is-like "oso:kuntanimiSwe" (str city "*")))
                     (wfs/property-is-less "oso:jarjestysnumero" "10")))))]
    (when data
      (map (comp (set-kind :address) wfs/feature-to-address)
           data))))

;;
;; Utils:
;;

(defn- pwz
  "Pad 's' with zeros so that its at least 'c' characters long"
  [c s]
  (apply str (conj (vec (repeat (- c (count s)) \0)) s)))

(defn- to-property-id
  "Convert property ID elements to 'database' format"
  [a b c d]
  (str (pwz 3 a) (pwz 3 b) (pwz 4 c) (pwz 4 d)))

(defn- apply-search
  "Return a function that can be used as a target function in 'search' function.
   The returned function accepts the list as returned from clojure.core/re-find.
   It strips the first element (complete match) and applies rest to provided
   function."
  [f]
  (fn [m] (apply f (drop 1 m))))

;;
;; Public API:
;;

(defn search [term]
  (condp re-find (s/trim term)
    #"^(\d{14})$"                                 :>> (apply-search search-property-id)
    #"^(\d{1,3})-(\d{1,3})-(\d{1,4})-(\d{1,4})$"  :>> (fn [[_ a b c d]] (search-property-id (to-property-id a b c d)))
    #"^(\S+)$"                                    :>> (apply-search search-poi-or-street)
    #"^(\S+)\s+(\d+)\s*,?\s*$"                    :>> (apply-search search-street-with-number)
    #"^(\S+)\s+(\S+)$"                            :>> (apply-search search-street-with-city)
    #"^(\S+)\s+(\d+)\s*,?\s*(\S+)$"               :>> (apply-search search-address)
    []))

;;
;; Here be dragons
;;

(comment
  
  (def e (wfs/post wfs/maasto
         (wfs/query {"typeName" "oso:Osoitenimi"}
           (wfs/sort-by ["oso:katunimi" "oso:katunumero"])
             (wfs/filter
               (wfs/and
                 (wfs/property-is-like "oso:katunimi" (str "piirin" "*"))
                 (wfs/or
                   (wfs/property-is-like "oso:kuntanimiFin" (str "tampe" "*"))
                   (wfs/property-is-like "oso:kuntanimiSwe" (str "tampe" "*"))))))))
  
  (map #(assoc % :kind :poi)
       (q/with-collection "poi"
         (q/find {:name {$regex (str \^ (s/lower-case term))}})
         (q/limit 11)))
  
  (defn find-addresses [street number city]
    (wfs/post wfs/maasto
      (cond
        (and (s/blank? number) (s/blank? city)) (wfs/query {"typeName" "oso:Osoitenimi"}
                                                           (wfs/sort-by ["oso:kuntanimiFin"])
                                                           (wfs/filter
                                                             (wfs/and
                                                               (wfs/property-is-like "oso:katunimi" (str street "*"))
                                                               (wfs/property-is-equal "oso:jarjestysnumero" "1"))))
        (s/blank? city) (wfs/query {"typeName" "oso:Osoitenimi"}
                                   (wfs/sort-by ["oso:kuntanimiFin"])
                                   (wfs/filter
                                     (wfs/and
                                       (wfs/property-is-like "oso:katunimi"   (str street "*"))
                                       (wfs/property-is-like "oso:katunumero" (str number "*"))
                                       (wfs/property-is-less "oso:jarjestysnumero" "10"))))
        (s/blank? number) (wfs/query {"typeName" "oso:Osoitenimi"}
                                     (wfs/sort-by ["oso:katunumero"])
                                     (wfs/filter
                                       (wfs/and
                                         (wfs/property-is-like "oso:katunimi" (str street "*"))
                                                    (wfs/or
                                                      (wfs/property-is-like "oso:kuntanimiFin" (str city "*"))
                                                      (wfs/property-is-like "oso:kuntanimiSwe" (str city "*"))))))
        :else (wfs/query {"typeName" "oso:Osoitenimi"}
                         (wfs/sort-by ["oso:katunumero"])
                         (wfs/filter
                           (wfs/and
                             (wfs/property-is-like "oso:katunimi"     (str street "*"))
                             (wfs/property-is-like "oso:katunumero"   (str number "*"))
                             (wfs/or
                               (wfs/property-is-like "oso:kuntanimiFin" (str city "*"))
                               (wfs/property-is-like "oso:kuntanimiSwe" (str city "*")))))))))
  
  (defn find-addresses-proxy [request]
    (let [query (get (:query-params request) "query")
          address (parse-address query)
          [status response] (apply find-addresses address)
          feature->string (wfs/feature-to-address-string address)]
      (if (= status :ok)
        (let [features (take 10 response)]
          (resp/json {:query query
                      :suggestions (map feature->string features)
                      :data (map wfs/feature-to-address features)}))
        (do
          (errorf "find-addresses failed: status=%s, response=%s" status response)
          (resp/status 503 "Service temporarily unavailable"))))))
