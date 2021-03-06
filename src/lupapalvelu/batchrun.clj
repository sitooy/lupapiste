(ns lupapalvelu.batchrun
  (:require [taoensso.timbre :refer [debug debugf error errorf info]]
            [me.raynes.fs :as fs]
            [monger.operators :refer :all]
            [clojure.set :as set]
            [clojure.string :as s]
            [slingshot.slingshot :refer [try+]]
            [lupapalvelu.action :refer :all]
            [lupapalvelu.application :as app]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.neighbors-api :as neighbors]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.states :as states]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as user]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.xml.krysp.reader]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.xml.asianhallinta.verdict :as ah-verdict]
            [lupapalvelu.attachment :as attachment]
            [sade.util :refer [fn->] :as util]
            [sade.env :as env]
            [sade.dummy-email-server]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [clj-time.coerce :as c])
  (:import [org.xml.sax SAXParseException]))


(defn- older-than [timestamp] {$lt timestamp})

(defn- get-app-owner [application]
  (let [owner (auth/get-auths-by-role application :owner)]
    (user/get-user-by-id (-> owner first :id))))


;; Email definition for the "open info request reminder"

(defn- oir-reminder-base-email-model [{{token :token-id created-date :created-date} :data :as command} _ recipient]
  (merge (notifications/create-app-model command nil recipient)
         {:link (fn [lang] (str (env/value :host) "/api/raw/openinforequest?token-id=" token "&lang=" (name lang)))
          :inforequest-created created-date}))

(def- oir-reminder-email-conf
  {:recipients-fn  notifications/from-data
   :subject-key    "open-inforequest-reminder"
   :model-fn       oir-reminder-base-email-model
   :application-fn (fn [{id :id}] (mongo/by-id :applications id))})

(notifications/defemail :reminder-open-inforequest oir-reminder-email-conf)

;; Email definition for the "Neighbor reminder"

(notifications/defemail :reminder-neighbor (assoc neighbors/email-conf :subject-key "neighbor-reminder"))

;; Email definition for the "Request statement reminder"

(defn- statement-reminders-email-model [{{:keys [created-date statement]} :data application :application :as command} _ recipient]
  (merge (notifications/create-app-model command nil recipient)
    {:link     #(notifications/get-application-link application "/statement" % recipient)
     :statement-request-created created-date
     :due-date (util/to-local-date (:dueDate statement))
     :message  (:saateText statement)}))

(notifications/defemail :reminder-request-statement
  {:recipients-fn  :recipients
   :subject-key    "statement-request-reminder"
   :model-fn       statement-reminders-email-model})

;; Email definition for the "Statement due date reminder"

(notifications/defemail :reminder-statement-due-date
  {:recipients-fn  :recipients
   :subject-key    "reminder-statement-due-date"
   :model-fn       statement-reminders-email-model})

;; Email definition for the "Application state reminder"

(notifications/defemail :reminder-application-state
  {:subject-key    "active-application-reminder"
   :recipients-fn  notifications/from-user})

;; Email definition for the "YA work time is expiring"

(defn- ya-work-time-is-expiring-reminder-email-model [{{work-time-expires-date :work-time-expires-date} :data :as command} _ recipient]
  (assoc
    (notifications/create-app-model command nil recipient)
    :work-time-expires-date work-time-expires-date))

(notifications/defemail :reminder-ya-work-time-is-expiring
  {:subject-key    "ya-work-time-is-expiring-reminder"
   :model-fn       ya-work-time-is-expiring-reminder-email-model})




;; "Lausuntopyynto: Pyyntoon ei ole vastattu viikon kuluessa ja hakemuksen tila on valmisteilla tai vireilla. Lahetetaan viikoittain uudelleen."
(defn statement-request-reminder []
  (let [timestamp-now (now)
        timestamp-1-week-ago (util/get-timestamp-ago :week 1)
        apps (mongo/select :applications
                           {:state {$in ["open" "submitted"]}
                            :statements {$elemMatch {:requested (older-than timestamp-1-week-ago)
                                                     :given nil
                                                     $or [{:reminder-sent {$exists false}}
                                                          {:reminder-sent nil}
                                                          {:reminder-sent (older-than timestamp-1-week-ago)}]}}}
                           [:statements :state :modified :infoRequest :title :address :municipality :primaryOperation])]
    (doseq [app apps
            statement (:statements app)
            :let [requested (:requested statement)
                  due-date (:dueDate statement)
                  reminder-sent (:reminder-sent statement)]
            :when (and
                    (nil? (:given statement))
                    (< requested timestamp-1-week-ago)
                    (or (nil? reminder-sent) (< reminder-sent timestamp-1-week-ago))
                    (or (nil? due-date) (> due-date timestamp-now)))]
      (logging/with-logging-context {:applicationId (:id app)}
        (notifications/notify! :reminder-request-statement {:application app
                                                            :recipients [(user/get-user-by-email (get-in statement [:person :email]))]
                                                            :data {:created-date (util/to-local-date requested)
                                                                   :statement statement}})
        (update-application (application->command app)
          {:statements {$elemMatch {:id (:id statement)}}}
          {$set {:statements.$.reminder-sent timestamp-now}})))))



;; "Lausuntopyynnon maaraika umpeutunut, mutta lausuntoa ei ole annettu. Muistutus lahetetaan viikoittain uudelleen."
(defn statement-reminder-due-date []
  (let [timestamp-now (now)
        timestamp-1-week-ago (util/get-timestamp-ago :week 1)
        apps (mongo/select :applications
                           {:state {$nin (map name (clojure.set/union states/post-verdict-states states/terminal-states))}
                            :statements {$elemMatch {:given nil
                                                     $and [{:dueDate {$exists true}}
                                                           {:dueDate (older-than timestamp-now)}]
                                                     $or [{:duedate-reminder-sent {$exists false}}
                                                          {:duedate-reminder-sent nil}
                                                          {:duedate-reminder-sent (older-than timestamp-1-week-ago)}]}}}
                           [:statements :state :modified :infoRequest :title :address :municipality :primaryOperation])]
    (doseq [app apps
            statement (:statements app)
            :let [due-date (:dueDate statement)
                  duedate-reminder-sent (:duedate-reminder-sent statement)]
            :when (and
                    (nil? (:given statement))
                    (number? due-date)
                    (< due-date timestamp-now)
                    (or (nil? duedate-reminder-sent) (< duedate-reminder-sent timestamp-1-week-ago)))]
      (logging/with-logging-context {:applicationId (:id app)}
        (notifications/notify! :reminder-statement-due-date {:application app
                                                             :recipients [(user/get-user-by-email (get-in statement [:person :email]))]
                                                             :data {:due-date (util/to-local-date due-date)
                                                                    :statement statement}})
        (update-application (application->command app)
          {:statements {$elemMatch {:id (:id statement)}}}
          {$set {:statements.$.duedate-reminder-sent (now)}})))))



;; "Neuvontapyynto: Neuvontapyyntoon ei ole vastattu viikon kuluessa eli neuvontapyynnon tila on avoin. Lahetetaan viikoittain uudelleen."
(defn open-inforequest-reminder []
  (let [timestamp-1-week-ago (util/get-timestamp-ago :week 1)
        oirs (mongo/select :open-inforequest-token {:created (older-than timestamp-1-week-ago)
                                                    :last-used nil
                                                    $or [{:reminder-sent {$exists false}}
                                                         {:reminder-sent nil}
                                                         {:reminder-sent (older-than timestamp-1-week-ago)}]})]
    (doseq [oir oirs]
      (let [application (mongo/by-id :applications (:application-id oir) [:state :modified :title :address :municipality :primaryOperation])]
        (logging/with-logging-context {:applicationId (:id application)}
          (when (= "info" (:state application))
            (notifications/notify! :reminder-open-inforequest {:application application
                                                               :data {:email (:email oir)
                                                                      :token-id (:id oir)
                                                                      :created-date (util/to-local-date (:created oir))}})
            (mongo/update-by-id :open-inforequest-token (:id oir) {$set {:reminder-sent (now)}})
            ))))))


;; "Naapurin kuuleminen: Kuulemisen tila on "Sahkoposti lahetetty", eika allekirjoitusta ole tehty viikon kuluessa ja hakemuksen tila on valmisteilla tai vireilla. Muistutus lahetetaan kerran."
(defn neighbor-reminder []
  (let [timestamp-1-week-ago (util/get-timestamp-ago :week 1)
        apps (mongo/select :applications
                           {:state {$in ["open" "submitted"]}
                            :neighbors.status {$elemMatch {$and [{:state {$in ["email-sent"]}}
                                                                 {:created (older-than timestamp-1-week-ago)}
                                                                 ]}}}
                           [:neighbors :state :modified :title :address :municipality :primaryOperation])]
    (doseq [app apps
            neighbor (:neighbors app)
            :let [statuses (:status neighbor)]]
      (logging/with-logging-context {:applicationId (:id app)}
        (when (not-any? #(or
                           (= "reminder-sent" (:state %))
                           (= "response-given-ok" (:state %))
                           (= "response-given-comments" (:state %))
                           (= "mark-done" (:state %))) statuses)

          (doseq [status statuses]

            (when (and
                    (= "email-sent" (:state status))
                    (< (:created status) timestamp-1-week-ago))
              (notifications/notify! :reminder-neighbor {:application app
                                                         :user        {:email (:email status)}
                                                         :data        {:token      (:token status)
                                                                       :expires    (util/to-local-datetime (+ ttl/neighbor-token-ttl (:created status)))
                                                                       :neighborId (:id neighbor)}})
              (update-application (application->command app)
                {:neighbors {$elemMatch {:id (:id neighbor)}}}
                {$push {:neighbors.$.status {:state    "reminder-sent"
                                             :token    (:token status)
                                             :created  (now)}}}))))))))



;; "YA hakemus: Hakemukselle merkitty tyoaika umpeutuu viikon kuluessa ja hakemuksen tila on valmisteilla tai vireilla. Lahetetaan viikoittain uudelleen."
(defn ya-work-time-is-expiring-reminder []
  (let [timestamp-1-week-in-future (util/get-timestamp-from-now :week 1)
        apps (mongo/select :applications
                           {:permitType "YA"
                            :state {$in ["verdictGiven" "constructionStarted"]}
                            ;; Cannot compare timestamp directly against date string here (e.g against "08.10.2015"). Must do it in function body.
                            :documents {$elemMatch {:schema-info.name "tyoaika"}}
                            :work-time-expiring-reminder-sent {$exists false}}
                           [:documents :auth :state :modified :title :address :municipality :infoRequest :primaryOperation])]
    (doseq [app apps
            :let [tyoaika-doc (some
                                (fn [doc]
                                  (when (= "tyoaika" (-> doc :schema-info :name)) doc))
                                (:documents app))
                  work-time-expires-timestamp (-> tyoaika-doc :data :tyoaika-paattyy-ms :value)]
            :when (and
                    work-time-expires-timestamp
                    (> work-time-expires-timestamp (now))
                    (< work-time-expires-timestamp timestamp-1-week-in-future))]
      (logging/with-logging-context {:applicationId (:id app)}
        (notifications/notify! :reminder-ya-work-time-is-expiring {:application app
                                                                   :user (get-app-owner app)
                                                                   :data {:work-time-expires-date (util/to-local-date work-time-expires-timestamp)}})
        (update-application (application->command app)
          {$set {:work-time-expiring-reminder-sent (now)}})))))



;; "Hakemus: Hakemuksen tila on valmisteilla tai vireilla, mutta edellisesta paivityksesta on aikaa yli kuukausi. Lahetetaan kuukausittain uudelleen."
(defn application-state-reminder []
  (let [timestamp-1-month-ago (util/get-timestamp-ago :month 1)
        apps (mongo/select :applications
                           {:state {$in ["open" "submitted"]}
                            :modified (older-than timestamp-1-month-ago)
                            $or [{:reminder-sent {$exists false}}
                                 {:reminder-sent nil}
                                 {:reminder-sent (older-than timestamp-1-month-ago)}]}
                           [:auth :state :modified :title :address :municipality :infoRequest :primaryOperation])]
    (doseq [app apps]
      (logging/with-logging-context {:applicationId (:id app)}
        (notifications/notify! :reminder-application-state {:application app
                                                            :user (get-app-owner app)})
        (update-application (application->command app)
          {$set {:reminder-sent (now)}})))))


(defn send-reminder-emails [& args]
  (when (env/feature? :reminders)
    (mongo/connect!)
    (statement-request-reminder)
    (statement-reminder-due-date)
    (open-inforequest-reminder)
    (neighbor-reminder)
    (application-state-reminder)
    (ya-work-time-is-expiring-reminder)

    (mongo/disconnect!)))

(defn fetch-verdicts []
  (let [orgs-with-wfs-url-defined-for-some-scope (organization/get-organizations
                                                   {$or [{:krysp.R.url {$exists true}}
                                                         {:krysp.YA.url {$exists true}}
                                                         {:krysp.P.url {$exists true}}
                                                         {:krysp.MAL.url {$exists true}}
                                                         {:krysp.VVVL.url {$exists true}}
                                                         {:krysp.YI.url {$exists true}}
                                                         {:krysp.YL.url {$exists true}}
                                                         {:krysp.KT.url {$exists true}}]}
                                                   {:krysp 1})
        orgs-by-id (util/key-by :id orgs-with-wfs-url-defined-for-some-scope)
        org-ids (keys orgs-by-id)
        apps (mongo/select :applications {:state {$in ["sent"]} :organization {$in org-ids}})
        eraajo-user (user/batchrun-user org-ids)]
    (doall
      (pmap
        (fn [{:keys [id permitType organization] :as app}]
          (logging/with-logging-context {:applicationId id, :userId (:id eraajo-user)}
            (let [url (get-in orgs-by-id [organization :krysp (keyword permitType) :url])]
              (try
                (if-not (s/blank? url)
                  (let [command (assoc (application->command app) :user eraajo-user :created (now) :action :fetch-verdicts)
                        result (verdict/do-check-for-verdict command)]
                    (when (-> result :verdicts count pos?)
                      ;; Print manually to events.log, because "normal" prints would be sent as emails to us.
                      (logging/log-event :info {:run-by "Automatic verdicts checking" :event "Found new verdict"})
                      (notifications/notify! :application-state-change command))
                    (when (fail? result)
                      (logging/log-event :error {:run-by "Automatic verdicts checking"
                                                 :event "Failed to check verdict"
                                                 :failure result
                                                 :organization {:id organization :permit-type permitType}
                                                 })))

                  (logging/log-event :info {:run-by "Automatic verdicts checking"
                                            :event "No Krysp WFS url defined for organization"
                                            :organization {:id organization :permit-type permitType}}))
                (catch Throwable t
                  (logging/log-event :error {:run-by "Automatic verdicts checking"
                                             :event "Unable to get verdict from backend"
                                             :exception-message (.getMessage t)
                                             :application-id id
                                             :organization {:id organization :permit-type permitType}}))))))
          apps))))

(defn check-for-verdicts [& args]
  (mongo/connect!)
  (fetch-verdicts))

(defn- get-asianhallinta-ftp-users [organizations]
  (->> (for [org organizations
             scope (:scope org)]
         (get-in scope [:caseManagement :ftpUser]))
    (remove nil?)
    distinct))

(defn fetch-asianhallinta-verdicts []
  (let [ah-organizations (mongo/select :organizations
                                       {"scope.caseManagement.ftpUser" {$exists true}}
                                       {"scope.caseManagement.ftpUser" 1})
        ftp-users (get-asianhallinta-ftp-users ah-organizations)
        eraajo-user (user/batchrun-user (map :id ah-organizations))]
    (doseq [ftp-user ftp-users
            :let [path (str
                         (env/value :outgoing-directory) "/"
                         ftp-user "/"
                         "asianhallinta/to_lupapiste/")]
            zip (util/get-files-by-regex path #".+\.zip$")]
      (fs/mkdirs (str path "archive"))
      (fs/mkdirs (str path "error"))
      (let [zip-path (.getPath zip)
            result (try
                     (ah-verdict/process-ah-verdict zip-path ftp-user eraajo-user)
                     (catch Throwable e
                       (logging/log-event :error {:run-by "Automatic ah-verdicts checking"
                                                  :event "Unable to process ah-verdict zip file"
                                                  :exception-message (.getMessage e)})
                       ;; (error e "Error processing zip-file in asianhallinta verdict batchrun")
                       (fail :error.unknown)))
            target (str path (if (ok? result) "archive" "error") "/" (.getName zip))]
        (logging/log-event :info {:run-by "Automatic ah-verdicts checking"
                                  :event (if (ok? result)  "Succesfully processed ah-verdict" "Failed to process ah-verdict") :zip-path zip-path})
        (when-not (fs/rename zip target)
          (errorf "Failed to rename %s to %s" zip-path target))))))

(defn check-for-asianhallinta-verdicts [& args]
  (mongo/connect!)
  (fetch-asianhallinta-verdicts))

(defn orgs-for-review-fetch [& organization-ids]
  (mongo/select :organizations (merge {:krysp.R.url {$exists true},
                                          :krysp.R.version {$gte "2.1.5"}}
                                         (when (seq organization-ids) {:_id {$in organization-ids}}))
                               {:krysp 1}))

(defn- save-reviews-for-application [user application {:keys [updates added-tasks-with-updated-buildings] :as result}]
  (logging/with-logging-context {:applicationId (:id application) :userId (:id user)}
    (when (ok? result)
      (try
        (verdict/save-review-updates user application updates added-tasks-with-updated-buildings)
        (catch Throwable t
          (logging/log-event :error {:run-by "Automatic review checking"
                                     :event "Failed to save"
                                     :exception-message (.getMessage t)}))))))

(defn- read-reviews-for-application [user created application app-xml]
  (try
    (when (and application app-xml)
      (logging/with-logging-context {:applicationId (:id application) :userId (:id user)}
        (let [{:keys [review-count updated-tasks validation-errors] :as result} (verdict/read-reviews-from-xml user created application app-xml)]
          (cond
            (and (ok? result) (pos? review-count)) (logging/log-event :info {:run-by "Automatic review checking"
                                                                             :event "Reviews found"
                                                                             :updated-tasks updated-tasks})
            (fail? result)                         (logging/log-event :error {:run-by "Automatic review checking"
                                                                              :event "Failed to read reviews"
                                                                              :validation-errors validation-errors}))
          result)))
    (catch Throwable t
      (errorf "error.integration - Could not read reviews for %s" (:id application)))))

(defn- fetch-reviews-for-organization-permit-type-consecutively [organization permit-type applications]
  (->> (map (fn [app]
              (try
                (debugf "fetch-reviews-for-organization-permit-type. org: %s, permit-type: %s: processing application id: %s"
                        (:id organization) permit-type (:id app))
                (krysp-fetch/fetch-xmls-for-applications organization permit-type [app])
                (catch Throwable t
                  (errorf "error.integration - Unable to get reviews for %s from %s backend: %s - %s"
                          (:id app) (:id organization) (.getName (class t)) (.getMessage t))
                  nil)))
            applications)
       (apply concat)
       (remove nil?)))

(defn- fetch-reviews-for-organization-permit-type [eraajo-user organization permit-type applications]
  (logging/with-logging-context {:org (:id organization), :permitType permit-type, :userId (:id eraajo-user)}
    (try+
      (debugf "fetch-reviews-for-organization-permit-type. org: %s, permit-type: %s: processing application ids: [%s]"
              (:id organization) permit-type (ss/join ", " (map :id applications)))
      (krysp-fetch/fetch-xmls-for-applications organization permit-type applications)

      (catch SAXParseException e
        (errorf "error.integration - Could not understand response when getting reviews in chunks for %s from %s backend" permit-type (:id organization))
        ;; Fallback into fetching xmls consecutively
        (fetch-reviews-for-organization-permit-type-consecutively organization permit-type applications))

      (catch [:sade.core/type :sade.core/fail
              :status         404] _
        (errorf "error.integration - Unable to get reviews in chunks for %s from %s backend: Got HTTP status 404" permit-type (:id organization))
        ;; Fallback into fetching xmls consecutively
        (fetch-reviews-for-organization-permit-type-consecutively organization permit-type applications))


      (catch [:sade.core/type :sade.core/fail] t
        (errorf "error.integration - Unable to get reviews for %s backend: %s"
                (:id organization) (select-keys t [:status :text])))

      (catch Object o
        (errorf "error.integration - Unable to get reviews in chunks for %s from %s backend: %s - %s"
                permit-type (:id organization) (.getName (class o)) (get &throw-context :message ""))))))

(defn- organization-applications-for-review-fetching
  [organization-id permit-type]
  (let [eligible-application-states (set/difference states/post-verdict-states states/terminal-states #{:foremanVerdictGiven})]
    (mongo/select :applications {:state {$in eligible-application-states}
                                 :permitType permit-type
                                 :organization organization-id
                                 :primaryOperation.name {$nin ["tyonjohtajan-nimeaminen-v2" "suunnittelijan-nimeaminen"]}}
                  (merge app/timestamp-key
                         {:state true
                          :municipality true
                          :address true
                          :permitType true
                          :permitSubtype true
                          :organization true
                          :primaryOperation true
                          :tasks true
                          :verdicts true
                          :history true}))))

(defn fetch-review-updates-for-organization
  [eraajo-user created applications permit-types {org-krysp :krysp :as organization}]
  (let [grouped-apps (if (seq applications)
                         (group-by :permitType applications)
                         (->> (remove (fn-> keyword org-krysp :url s/blank?) permit-types)
                              (map #(vector % (organization-applications-for-review-fetching (:id organization) %)))))]
    (->> (mapcat (partial apply fetch-reviews-for-organization-permit-type eraajo-user organization) grouped-apps)
         (map (fn [[app app-xml]] [app (read-reviews-for-application eraajo-user created app app-xml)])))))

(defn poll-verdicts-for-reviews [& {:keys [application-ids organization-ids]}]
  (let [applications (when (seq application-ids)
                       (mongo/select :applications {:_id {$in application-ids}}))
        organizations (apply orgs-for-review-fetch (concat organization-ids (map :organization applications)))
        eraajo-user (user/batchrun-user (map :id organizations))]
    (->> (pmap (partial fetch-review-updates-for-organization eraajo-user (now) applications [:R]) organizations)
         (apply concat)
         (run! (partial apply save-reviews-for-application eraajo-user)))))

(defn check-for-reviews [& args]
  (logging/log-event :info {:run-by "Automatic review checking" :event "Started"})
  (mongo/connect!)
  (poll-verdicts-for-reviews)
  (logging/log-event :info {:run-by "Automatic review checking" :event "Finished"}))

(defn check-reviews-for-orgs [& args]
  (logging/log-event :info {:run-by "Automatic review checking" :event "Started" :organizations args})
  (mongo/connect!)
  (poll-verdicts-for-reviews :organization-ids args)
  (logging/log-event :info {:run-by "Automatic review checking" :event "Finished" :organizations args}))

(defn check-reviews-for-ids [& args]
  (logging/log-event :info {:run-by "Automatic review checking" :event "Started" :applications args})
  (mongo/connect!)
  (poll-verdicts-for-reviews :application-ids args)
  (logging/log-event :info {:run-by "Automatic review checking" :event "Finished" :applications args}))

(defn pdfa-convert-review-pdfs [& args]
  (mongo/connect!)
  (debug "# of applications with background generated tasks:"
           (mongo/count :applications {:tasks.source.type "background"}))
  (let [eraajo-user (user/batchrun-user (map :id (orgs-for-review-fetch)))]
    (doseq [application (mongo/select :applications {:tasks.source.type "background"})]
      (let [command (assoc (application->command application) :user eraajo-user :created (now))]
        (doseq [task (:tasks application)]
          (if (= "background" (:type (:source task)))
            (do
              (doseq [att (:attachments application)]
                (if (= (:id task) (:id (:source att)))
                  (do
                    (debug "application" (:id (:application command)) "- converting task" (:id task) "-> attachment" (:id att) )
                    (attachment/convert-existing-to-pdfa! (:application command) (:user command) att)))))))))))

(defn pdf-to-pdfa-conversion [& args]
  (info "Starting pdf to pdf/a conversion")
  (mongo/connect!)
  (let [organization (first args)
        start-ts (c/to-long (c/from-string (second args)))
        end-ts (c/to-long (c/from-string (second (next args))))]
  (doseq [application (mongo/select :applications {:organization organization :state :verdictGiven})]
    (let [command (application->command application)
          last-verdict-given-date (:ts (last (sort-by :ts (filter #(= (:state % ) "verdictGiven") (:history application)))))]
      (logging/with-logging-context {:applicationId (:id application)}
        (when (and (= (:state application) "verdictGiven") (< start-ts last-verdict-given-date end-ts))
          (info "Converting attachments of application" (:id application))
          (doseq [attachment (:attachments application)]
            (when (:latestVersion attachment)
              (when-not (get-in attachment [:latestVersion :archivable])
                  (do
                    (info "Trying to convert attachment" (get-in attachment [:latestVersion :filename]))
                    (let [result (attachment/convert-existing-to-pdfa! (:application command) nil attachment)]
                      (if (:archivabilityError result)
                        (error "Conversion failed to" (:id application) "/" (:id attachment) "/" (get-in attachment [:latestVersion :filename]) "with error:" (:archivabilityError result))
                        (info "Conversion succeed to" (get-in attachment [:latestVersion :filename]) "/" (:id application))))))))))))))
