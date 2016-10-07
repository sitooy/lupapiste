(ns lupapalvelu.autom-check-reviews-itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.action :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.tasks :refer [task-is-review?]]
            [sade.core :refer [now fail]]
            [sade.xml :as sxml]
            [sade.coordinate :as coordinate]
            [sade.dummy-email-server :as dummy-email-server]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.integrations-api]
            [lupapalvelu.verdict-api]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.pdftk :as pdftk]
            [clojure.java.io :as io]
            [lupapalvelu.xml.krysp.application-from-krysp :as app-from-krysp])
  (:import [java.io File]))

(def db-name (str "test_autom-check-reviews-itest_" (now)))

(testable-privates lupapalvelu.verdict save-reviews-from-xml)

(mongo/connect!)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (mongo/remove-many :organizations {})
  (mongo/remove-many :applications {}))

(mongo/with-db db-name
  (let [krysp-url (str (server-address) "/dev/krysp")
        organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) minimal/organizations)]
    (dorun (map (partial mongo/insert :organizations) organizations))))

(defn  query-tasks [user application-id]
  (:tasks (query-application local-query user application-id)))

(defn count-reviews [user app-id] (count (filter task-is-review? (query-tasks user app-id))))

(facts "Automatic checking for reviews"
  (mongo/with-db db-name
    (against-background [(coordinate/convert anything anything anything anything) => nil
                         ]
      (let [application-submitted        (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 17")
            application-id-submitted     (:id application-submitted)
            application-verdict-given    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 18")
            application-id-verdict-given (:id application-verdict-given)
            ]

        (fact "Initial state of reviews before krysp reading is sane"
          (local-command sonja :approve-application :id application-id-verdict-given :lang "fi") => ok?
          (count  (:tasks (query-application local-query sonja application-id-verdict-given))) => 0
          (count (batchrun/fetch-verdicts)) => pos?
          (count  (:tasks (query-application local-query sonja application-id-verdict-given))) =not=> 0

          (give-local-verdict sonja application-id-verdict-given :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
          ;; (give-local-verdict sonja application-id-verdict-given :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
          (let [application-submitted (query-application local-query sonja application-id-submitted) => truthy
                application-verdict-given (query-application local-query sonja application-id-verdict-given) => truthy]

            (:state application-submitted) => "submitted"
            (:state application-verdict-given) => "verdictGiven")

          (count (:tasks application-verdict-given)) => 0)

        (against-background [(app-from-krysp/get-application-xml-by-application-id anything) => (sxml/parse-string
                                                                                                 (slurp "resources/krysp/dev/r-verdict-review.xml")
                                                                                                 ;;(slurp "dev-resources/krysp/verdict-r-buildings.xml")
                                                                                                 "utf-8")]
          (fact "checking for reviews in correct states"

            (let [app-before (query-application local-query sonja application-id-verdict-given)
                  review-count-before (count-reviews sonja application-id-verdict-given) => 3
                  poll-result (batchrun/poll-verdicts-for-reviews)
                  last-review (last (filter task-is-review? (query-tasks sonja application-id-verdict-given)))
                  app-after (query-application local-query sonja application-id-verdict-given)]

              (count-reviews sonja application-id-submitted) => 0
              (count poll-result) => pos?

              (:state last-review) => "sent"
              (count-reviews sonja application-id-submitted) => 0
              (count-reviews sonja application-id-verdict-given) => 4
              )))

        (fact "existing tasks are preserved"
          ;; should be seeing 1 added "aloituskokous" here compared to default verdict.xml
          (count-reviews sonja application-id-verdict-given) => 4
          (let [tasks (map tools/unwrapped  (query-tasks sonja application-id-verdict-given))
                reviews (filter task-is-review? tasks)
                review-types (map #(-> % :data :katselmuksenLaji) reviews)
                final-review? (fn [review]
                                (= (get-in review [:data :katselmus :tila]) "lopullinen"))]
            (fact "no validation errors"
              (not-any? :validationErrors reviews))
            (count (filter  (partial = "aloituskokous") review-types)) => 2
            (count (filter final-review? reviews)) => 1
            (get-in (first (filter final-review? reviews)) [:data :rakennus :0 :tila :tila]) => "lopullinen"))))))

(facts "Imported review PDF generation"
  (mongo/with-db db-name
    (let [parsed-xml (sxml/parse-string (slurp "resources/krysp/dev/r-verdict-review.xml") "utf-8")
          application    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 18")
          application-id (:id application)
          batchrun-user (batchrun/batchrun-user-for-review-fetch (batchrun/orgs-for-review-fetch))]
      (give-local-verdict sonja (:id application) :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
      (save-reviews-from-xml batchrun-user (now) application parsed-xml) ;; => ok?
      (let [updated-application (domain/get-application-no-access-checking application-id)
            last-attachment-id (last (get-attachment-ids updated-application))
            last-attachment-file-id (att/attachment-latest-file-id updated-application last-attachment-id)
            temp-pdf-path (File/createTempFile "review-test" ".tmp")]
        (try
          (with-open [content-fios ((:content (mongo/download last-attachment-file-id)))]
            (pdftk/uncompress-pdf content-fios (.getAbsolutePath temp-pdf-path)))
          (re-seq #"(?ms)\(Kiinteist.tunnus\).{1,100}18600303560006" (slurp temp-pdf-path :encoding "ISO-8859-1")) => not-empty
          (re-seq #"(?ms)\(Tila\).{1,100}lopullinen" (slurp temp-pdf-path :encoding "ISO-8859-1")) => truthy
          (finally
            (io/delete-file temp-pdf-path)))))))
