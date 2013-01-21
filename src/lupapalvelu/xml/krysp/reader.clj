(ns lupapalvelu.xml.krysp.reader
  (:use sade.xml)
  (:require [clojure.string :refer [split]]
            [clojure.walk :refer [postwalk]]))

(defn- strip-key [[k v]] (if (keyword? k) [(-> k name (split #":") last keyword) v] [k v]))
(defn strip-keys [m] (postwalk (fn [x] (if (map? x) (into {} (map strip-key x)) x)) m))

(defn building-info [id]
  (let [url (str "http://212.213.116.162/geoserver/wfs?request=GetFeature&typeName=rakval%3AValmisRakennus&outputFormat=KRYSP&filter=%3CPropertyIsEqualTo%3E%3CPropertyName%3Erakval:rakennustieto/rakval:Rakennus/rakval:rakennuksenTiedot/rakval:rakennustunnus/rakval:kiinttun%3C/PropertyName%3E%3CLiteral%3E" id "%3C/Literal%3E%3C/PropertyIsEqualTo%3E")]
    (-> url parse xml->edn strip-keys)))

(-> "./dev-resources/public/krysp/building.xml" slurp parse xml->edn strip-keys)
