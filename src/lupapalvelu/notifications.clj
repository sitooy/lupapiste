(ns lupapalvelu.notifications
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [clojure.set :as set]
            [lupapalvelu.i18n :refer [loc] :as i18n]
            [sade.util :refer [future* to-local-date]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.email :as email]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]))

;;
;; Helpers
;;

(defn- get-application-link [{:keys [infoRequest id]} suffix lang]
  (let [permit-type-path (if infoRequest "/inforequest" "/application")
        full-path        (str permit-type-path "/" id suffix)]
    (str (env/value :host) "/app/" lang "/applicant#!" full-path)))

(defn- send-mail-to-recipients! [recipients subject msg]
  (future*
    (doseq [recipient recipients]
      (if (email/send-email-message recipient subject msg)
        (error "email could not be delivered." recipient subject msg)
        (info "email was sent successfully." recipient subject))))
  nil)

(defn- get-email-subject [{title :title} & [title-key]]
  (let [title-postfix (when title-key (if (i18n/has-term? "fi" "email.title" title-key)
                                        (i18n/localize "fi" "email.title" title-key)
                                        (i18n/localize "fi" title-key)))]
    (str "Lupapiste.fi: " title (when (and title title-key)" - ") (when title-key title-postfix))))

; emails are sent to everyone in auth array except statement persons
(defn- get-email-recipients-for-application [{:keys [auth statements]} included-roles excluded-roles]
  (let [included-users   (if (seq included-roles)
                           (filter (fn [user] (some #(= (:role user) %) included-roles)) auth)
                           auth)
        auth-user-emails (->> included-users
                           (remove :invite) ; filters out users that have not accepted invite
                           (filter (fn [user] (not-any? #(= (:role user) %) excluded-roles)))
                           (map #(:email (mongo/by-id :users (:id %) {:email 1}))))]
    (if (some #(= "statementGiver" %) excluded-roles)
      (set/difference
        (set auth-user-emails)
        (set (map #(-> % :person :email) statements)))
      auth-user-emails)))

;;
;; Model creation functions
;;

;; Application (the default)
(defn- create-app-model [{application :application} {tab :tab}]
  {:link-fi (get-application-link application tab "fi")
   :link-sv (get-application-link application tab "sv")
   :state-fi (i18n/localize :fi (str (:state application)))
   :state-sv (i18n/localize :sv (str (:state application)))
   :modified (to-local-date (:modified application))})

(defn- request-statement-reminder-email-model [{{created-date :created-date} :data application :application :as command} _]
  {:link-fi (get-application-link application nil "fi")
   :link-sv (get-application-link application nil "sv")
   :created-date created-date})

(defn- statement-giver-model [{{:keys [text organization]} :data} _]
  {:text text
   :organization-fi (:fi (:name organization))
   :organization-sv (:sv (:name organization))})

;;
;; Recipient functions
;;

(defn- default-recipients-fn [{application :application}]
  (get-email-recipients-for-application application nil ["statementGiver"]))
(defn from-user [{user :user}] [(:email user)])
(defn from-data [{data :data}] (let [email (:email data)]
                                 (if (sequential? email) email [email])))

;;
;; Configuration for generic notifications
;;

(defonce ^:private mail-config
  (atom {:application-state-change     {:subject-key    "state-change"
                                        :application-fn (fn [{id :id}] (mongo/by-id :applications id))}
         :reminder-application-state   {:subject-key    "active-application-reminder"
                                        :recipients-fn  from-data}
         :application-verdict          {:subject-key    "verdict"
                                        :tab            "/verdict"}
         :invite                       {:recipients-fn  from-data}
         :add-statement-giver          {:recipients-fn  from-user
                                        :subject-key    "application.statements"
                                        :model-fn       statement-giver-model}
         :request-statement            {:recipients-fn  from-data
                                        :subject-key    "statement-request"}
         :reminder-request-statement   {:recipients-fn  from-data
                                        :subject-key    "statement-request-reminder"
                                        :model-fn       request-statement-reminder-email-model}}))

;;
;; Public API
;;

(defn defemail [template-name m]
  (swap! mail-config assoc template-name m))

(defn notify! [template-name command]
  {:pre [(template-name @mail-config)]}
  (let [conf (template-name @mail-config)]
    (when ((get conf :pred-fn (constantly true)) command)
      (let [application-fn (get conf :application-fn identity)
            application    (application-fn (:application command))
            command        (assoc command :application application)
            recipients-fn  (get conf :recipients-fn default-recipients-fn)
            recipients     (remove ss/blank? (recipients-fn command))
            subject        (get-email-subject application (get conf :subject-key (name template-name)))
            model-fn       (get conf :model-fn create-app-model)
            model          (model-fn command conf)
            template-file  (get conf :template (str (name template-name) ".md"))
            msg            (email/apply-template template-file model)]
        (send-mail-to-recipients! recipients subject msg)))))
