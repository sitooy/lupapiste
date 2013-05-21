(ns lupapalvelu.document.vrk-test
  (:use [lupapalvelu.document.tools]
        [lupapalvelu.document.schemas]
        [lupapalvelu.document.validators]
        [lupapalvelu.document.model]
        [midje.sweet]
        [sade.util])
  (:require [lupapalvelu.document.validator :as v]))

(defn validator-facts []
  (let [validators (->> v/validators deref vals (filter (fn-> :facts nil? not)))]
    (println "About to test" (count validators) "awesome validators")
    (doseq [{:keys [doc schema paths] {:keys [ok fail]} :facts} validators]
      (let [dummy    (dummy-doc schema)
            update   (fn [values]
                       (reduce
                         (fn [d i]
                           (apply-update d (get paths i) (get values i)))
                         dummy (range 0 (count paths))))
            ok-doc   (update ok)
            fail-doc (update fail)]

        (facts "Embedded validator fact"
          (println doc)
          dummy => valid?
          ok-doc => valid?
          fail-doc => invalid?)))))

(facts "Embedded validator facts"
  (validator-facts))

(def uusi-rakennus
  (dummy-doc "uusiRakennus"))

(facts "VRK-validations"

  (fact "uusi rakennus is valid"
    uusi-rakennus => valid?)

  (fact "k\u00e4ytt\u00f6tarkoituksen mukainen maksimitilavuus"
    (-> uusi-rakennus
      (apply-update [:kaytto :kayttotarkoitus] "032 luhtitalot")
      (apply-update [:mitat :tilavuus] "100000")) => valid?
    (-> uusi-rakennus
      (apply-update [:kaytto :kayttotarkoitus] "032 luhtitalot")
      (apply-update [:mitat :tilavuus] "100001")) => (invalid-with? [:warn "vrk:CR327"]))

  (fact "Puutalossa saa olla korkeintaan 4 kerrosta"
    (-> uusi-rakennus
      (apply-update [:rakenne :kantavaRakennusaine] "puu")
      (apply-update [:mitat :kerrosluku] "3")) => valid?
    (-> uusi-rakennus
      (apply-update [:rakenne :kantavaRakennusaine] "puu")
      (apply-update [:mitat :kerrosluku] "5")) => (invalid-with? [:warn "vrk:BR106"]))

  (fact "Jos lammitustapa on 3 (sahkolammitys), on polttoaineen oltava 4 (sahko)"
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "suorasahk\u00f6")
      (apply-update [:lammitys :lammonlahde] "s\u00e4hk\u00f6")) => valid?
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "suorasahk\u00f6")
      (apply-update [:lammitys :lammonlahde] "kaasu")) => (invalid-with? [:warn "vrk:CR343"]))

  (fact "Sahko polttoaineena vaatii sahkoliittyman"
    (-> uusi-rakennus
      (apply-update [:lammitys :lammonlahde] "s\u00e4hk\u00f6")
      (apply-update [:verkostoliittymat :sahkoKytkin] true)) => valid?
    (-> uusi-rakennus
      (apply-update [:lammitys :lammonlahde] "s\u00e4hk\u00f6")
      (apply-update [:verkostoliittymat :sahkoKytkin] false)) => (invalid-with? [:warn "vrk:CR342"]))

  (fact "Sahkolammitus vaatii sahkoliittyman"
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "suorasahk\u00f6")
      (apply-update [:verkostoliittymat :sahkoKytkin] true)) => (not-invalid-with? [:warn "vrk:CR341"])
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "suorasahk\u00f6")
      (apply-update [:verkostoliittymat :sahkoKytkin] false)) => (invalid-with? [:warn "vrk:CR341"]))

  (fact "Jos lammitystapa on 5 (ei kiinteaa lammitystapaa), ei saa olla polttoainetta"
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "eiLammitysta")
      (apply-update [:lammitys :lammonlahde])) => valid?
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "eiLammitysta")
      (apply-update [:lammitys :lammonlahde] "kaasu")) => (invalid-with? [:warn "vrk:CR336"]))

  (fact "Jos lammitystapa ei ole 5 (ei kiinteaa lammitystapaa), on polttoaine ilmoitettava"
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "uuni")
      (apply-update [:lammitys :lammonlahde])) => (invalid-with? [:warn "vrk:CR335"])
    (-> uusi-rakennus
      (apply-update [:lammitys :lammitystapa] "ei tiedossa")
      (apply-update [:lammitys :lammonlahde] "ei tiedossa")) => valid?)

  (fact "Kokonaisalan oltava vähintään kerrosala"
    (-> uusi-rakennus
      (apply-update [:mitat :kerrosala] "100")
      (apply-update [:mitat :kokonaisala] "100")) => valid?
    (-> uusi-rakennus
      (apply-update [:mitat :kerrosala] "100")
      (apply-update [:mitat :kokonaisala] "99")) => (invalid-with? [:warn "vrk:CR326"]))

  (fact "Sahko polttoaineena vaatii varusteeksi sahkon"
    (-> uusi-rakennus
      (apply-update [:lammitus :lammonlahde] "s\u00e4hk\u00f6")
      (apply-update [:varusteet :sahkoKytkin] true)) => (not-invalid-with? [:warn "vrk:CR324"])
    (-> uusi-rakennus
      (apply-update [:lammitus :lammonlahde] "s\u00e4hk\u00f6")
      (apply-update [:varusteet :sahkoKytkin] false)) => (invalid-with? [:warn "vrk:CR324"]))

  (fact "Uuden rakennuksen kokonaisalan oltava vahintaan huoneistoala"
    (-> uusi-rakennus
      (apply-update [:mitat :kokonaisala] "100")
      (apply-update [:huoneistot :0 :huoneistoTyyppi :huoneistoAla] "60")) => (not-invalid-with? [:warn "vrk:CR322"])
    (-> uusi-rakennus
      (apply-update [:mitat :kokonaisala] "100")
      (apply-update [:huoneistot :0 :huoneistonTyyppi :huoneistoala] "60")
      (apply-update [:huoneistot :1 :huoneistonTyyppi :huoneistoala] "50")) => (invalid-with? [:warn "vrk:CR322"]))

  (fact "Jos kayttotarkoitus on 011 - 022, on kerrosluvun oltava valilla 1 - 4"
    (-> uusi-rakennus
      (apply-update [:kaytto :kayttotarkoitus] "021 rivitalot")
      (apply-update [:mitat :kerrosluku] "4")) => valid?
    (-> uusi-rakennus
      (apply-update [:kaytto :kayttotarkoitus] "021 rivitalot")
      (apply-update [:mitat :kerrosluku] "5")) => (invalid-with? [:warn "vrk:CR320"]))

  (fact "Verkostoliittymat ja rakennuksen varusteet tasmattava: Sahko"
    (-> uusi-rakennus
      (apply-update [:verkostoliittymat :sahkoKytkin] true)
      (apply-update [:varusteet :sahkoKytkin] true)) => valid?

    (-> uusi-rakennus
      (apply-update [:verkostoliittymat :sahkoKytkin] true)
      (apply-update [:varusteet :sahkoKytkin] false)) => (invalid-with? [:warn "vrk:CR328:sahko"]))

  (fact "Verkostoliittymat ja rakennuksen varusteet tasmattava: Viemari"
    (-> uusi-rakennus
      (apply-update [:verkostoliittymat :viemariKytkin] true)
      (apply-update [:varusteet :viemariKytkin] true)) => valid?

    (-> uusi-rakennus
      (apply-update [:verkostoliittymat :viemariKytkin] true)
      (apply-update [:varusteet :viemariKytkin] false)) => (invalid-with? [:warn "vrk:CR328:viemari"]))

  (fact "Verkostoliittymat ja rakennuksen varusteet tasmattava: Vesijohto"
    (-> uusi-rakennus
      (apply-update [:verkostoliittymat :vesijohtoKytkin] true)
      (apply-update [:varusteet :vesijohtoKytkin] true)) => valid?

    (-> uusi-rakennus
      (apply-update [:verkostoliittymat :vesijohtoKytkin] true)
      (apply-update [:varusteet :vesijohtoKytkin] false)) => (invalid-with? [:warn "vrk:CR328:vesijohto"]))

    )
