(ns lupapalvelu.organization-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [clojure.core.memoize :as memo]
            [clojure.set :as set]
            [clojure.string :as s]
            [clojure.walk :refer [keywordize-keys]]
            [schema.core :as sc]
            [monger.operators :refer :all]
            [noir.core :refer [defpage]]
            [noir.response :as resp]
            [noir.request :as request]
            [camel-snake-kebab.core :as csk]
            [me.raynes.fs :as fs]
            [slingshot.slingshot :refer [try+]]
            [sade.core :refer [ok fail fail! now unauthorized]]
            [sade.env :as env]
            [sade.municipality :as muni]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]
            [sade.validators :as v]
            [lupapalvelu.action :refer [defquery defcommand defraw non-blank-parameters vector-parameters boolean-parameters number-parameters email-validator validate-url validate-optional-url map-parameters-with-required-keys] :as action]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.states :as states]
            [lupapalvelu.wfs :as wfs]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.waste-ads :as waste-ads]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.i18n :as i18n]))
;;
;; local api
;;

(defn- municipalities-with-organization []
  (let [organizations (org/get-organizations {} [:scope :krysp])]
    {:all (distinct
            (for [{scopes :scope} organizations
                  {municipality :municipality} scopes]
              municipality))
     :with-backend (remove nil?
                     (distinct
                       (for [{scopes :scope :as org} organizations
                             {municipality :municipality :as scope} scopes]
                         (when (-> org :krysp (get (-> scope :permitType keyword)) :url s/blank? not)
                           municipality))))}))

(defn- organization-attachments
  "Returns a map where key is permit type, value is a list of attachment types for the permit type"
  [{scope :scope}]
  (let [permit-types (->> scope (map :permitType) distinct (map keyword))]
    (->> (select-keys operations/operation-names-by-permit-type permit-types)
         (map (fn [[permit-type operations]] (->> (map att-type/get-attachment-types-for-operation operations)
                                                  (map att-type/->grouped-array)
                                                  (zipmap operations))))
         (zipmap permit-types))))

(defn- operations-attachements-by-operation [organization operations]
  (->> (map #(get-in organization [:operations-attachments %] []) operations)
       (zipmap operations)))

(defn- organization-operations-with-attachments
  "Returns a map of maps where key is permit type, value is a map operation names to list of attachment types"
  [{scope :scope :as organization}]
  (let [selected-ops (->> organization :selected-operations (map keyword) set)
        permit-types (->> scope (map :permitType) distinct (map keyword))]
    (zipmap permit-types (map (fn->> (operations/operation-names-by-permit-type)
                                     (filter selected-ops)
                                     (operations-attachements-by-operation organization))
                              permit-types))))

(defn- selected-operations-with-permit-types
  "Returns a map where key is permit type, value is a list of operations for the permit type"
  [{scope :scope selected-ops :selected-operations}]
  (reduce
    #(if-not (get-in %1 [%2])
       (let [selected-operations (set (map keyword selected-ops))
             operation-names (keys (filter
                                     (fn [[name op]]
                                       (and
                                         (= %2 (:permit-type op))
                                         (selected-operations name)))
                                     operations/operations))]
         (if operation-names (assoc %1 %2 operation-names) %1))
       %1)
    {}
    (map :permitType scope)))


;;
;; Actions
;;

(defquery organization-by-user
  {:description "Lists organization details."
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organization (org/get-organization (usr/authority-admins-organization-id user))
        ops-with-attachments (organization-operations-with-attachments organization)
        selected-operations-with-permit-type (selected-operations-with-permit-types organization)
        allowed-roles (org/allowed-roles-in-organization organization)]
    (ok :organization (-> organization
                        (assoc :operationsAttachments ops-with-attachments
                               :selectedOperations selected-operations-with-permit-type
                               :allowedRoles allowed-roles)
                        (dissoc :operations-attachments :selected-operations)
                        (update-in [:map-layers :server] select-keys [:url :username])
                        (update-in [:suti :server] select-keys [:url :username]))
        :attachmentTypes (organization-attachments organization))))

(defquery organization-name-by-user
  {:description "Lists organization names for all languages."
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (ok (-> (usr/authority-admins-organization-id user)
          org/get-organization
          (select-keys [:id :name]))))

(defquery user-organizations-for-permit-type
  {:parameters [permitType]
   :user-roles #{:authority}
   :input-validators [permit/permit-type-validator]}
  [{user :user}]
  (ok :organizations (org/get-organizations {:_id {$in (usr/organization-ids-by-roles user #{:authority})}
                                           :scope {$elemMatch {:permitType permitType}}})))

(defcommand update-organization
  {:description "Update organization details."
   :parameters [permitType municipality
                inforequestEnabled applicationEnabled openInforequestEnabled openInforequestEmail
                opening]
   :input-validators [permit/permit-type-validator]
   :user-roles #{:admin}}
  [_]
  (mongo/update-by-query :organizations
      {:scope {$elemMatch {:permitType permitType :municipality municipality}}}
      {$set {:scope.$.inforequest-enabled inforequestEnabled
             :scope.$.new-application-enabled applicationEnabled
             :scope.$.open-inforequest openInforequestEnabled
             :scope.$.open-inforequest-email openInforequestEmail
             :scope.$.opening (when (number? opening) opening)}})
  (ok))

(defcommand add-scope
  {:description "Admin can add new scopes for organization"
   :parameters [organization permitType municipality
                inforequestEnabled applicationEnabled openInforequestEnabled openInforequestEmail
                opening]
   :input-validators [permit/permit-type-validator
                      (fn [{{:keys [municipality]} :data}]
                        (when-not (contains? muni/municipality-codes municipality)
                          (fail :error.invalid-municipality)))]
   :user-roles #{:admin}}
  (let [scope-count (mongo/count :organizations {:scope {$elemMatch {:permitType permitType :municipality municipality}}})]
    (if (zero? scope-count)
      (do
        (org/update-organization
          organization
          {$push {:scope
                  {:municipality            municipality
                   :permitType              permitType
                   :inforequest-enabled     inforequestEnabled
                   :new-application-enabled applicationEnabled
                   :open-inforequest        openInforequestEnabled
                   :open-inforequest-email  openInforequestEmail
                   :opening                 (when (number? opening) opening)}}})
        (ok))
      (fail :error.organization.duplicate-scope))))

(defn- validate-map-with-optional-url-values [param command]
  (let [urls (map ss/trim (vals (get-in command [:data param])))]
    (some #(when-not (ss/blank? %)
             (validate-url %))
          urls)))

(defcommand add-organization-link
  {:description "Adds link to organization."
   :parameters [url name]
   :user-roles #{:authorityAdmin}
   :input-validators [(partial map-parameters-with-required-keys
                               [:url :name] i18n/supported-langs)
                      (partial validate-map-with-optional-url-values :url)]}
  [{user :user created :created}]
  (org/add-organization-link (usr/authority-admins-organization-id user)
                             name url created)
  (ok))

(defcommand update-organization-link
  {:description "Updates organization link."
   :parameters [url name index]
   :user-roles #{:authorityAdmin}
   :input-validators [(partial map-parameters-with-required-keys
                               [:url :name] i18n/supported-langs)
                      (partial validate-map-with-optional-url-values :url)
                      (partial number-parameters [:index])]}
  [{user :user created :created}]
  (org/update-organization-link (usr/authority-admins-organization-id user)
                                index name url created)
  (ok))

(defcommand remove-organization-link
  {:description "Removes organization link."
   :parameters [url name]
   :input-validators [(partial map-parameters-with-required-keys
                               [:url :name] i18n/supported-langs)
                      (partial validate-map-with-optional-url-values :url)]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (org/remove-organization-link (usr/authority-admins-organization-id user)
                                name url)
  (ok))

(defquery organizations
  {:user-roles #{:admin}}
  [_]
  (ok :organizations (org/get-organizations)))

(defquery allowed-autologin-ips-for-organization
  {:parameters [org-id]
   :input-validators [(partial non-blank-parameters [:org-id])]
   :user-roles #{:admin}}
  [_]
  (ok :ips (org/get-autologin-ips-for-organization org-id)))

(defcommand update-allowed-autologin-ips
  {:parameters [org-id ips]
   :input-validators [(partial non-blank-parameters [:org-id])
                      (comp org/valid-ip-addresses :ips :data)]
   :user-roles #{:admin}}
  [_]
  (->> (org/autogin-ip-mongo-changes ips)
       (org/update-organization org-id))
  (ok))

(defquery organization-by-id
  {:parameters [organizationId]
   :input-validators [(partial non-blank-parameters [:organizationId])]
   :user-roles #{:admin}}
  [_]
  (ok :data (org/get-organization organizationId)))

(defquery permit-types
  {:user-roles #{:admin}}
  [_]
  (ok :permitTypes (keys (permit/permit-types))))

(defquery municipalities-with-organization
  {:description "Returns a list of municipality IDs that are affiliated with Lupapiste."
   :user-roles #{:applicant :authority :admin}}
  [_]
  (let [munis (municipalities-with-organization)]
    (ok
      :municipalities (:all munis)
      :municipalitiesWithBackendInUse (:with-backend munis))))

(defquery municipalities
  {:description "Returns a list of all municipality IDs. For admin use."
   :user-roles #{:admin}}
  (ok :municipalities muni/municipality-codes))

(defquery all-operations-for-organization
  {:description "Returns operations that match the permit types of the organization whose id is given as parameter"
   :parameters [organizationId]
   :user-roles #{:authorityAdmin}
   :input-validators [(partial non-blank-parameters [:organizationId])]}
  (when-let [org (org/get-organization organizationId)]
    (ok :operations (operations/organization-operations org))))

(defquery selected-operations-for-municipality
  {:description "Returns selected operations of all the organizations who have a scope with the given municipality.
                 If a \"permitType\" parameter is given, returns selected operations for only that organization (the municipality + permitType combination)."
   :parameters [:municipality]
   :user-roles #{:applicant :authority :authorityAdmin}
   :input-validators [(partial non-blank-parameters [:municipality])]}
  [{{:keys [municipality permitType]} :data}]
  (when-let [organizations (org/resolve-organizations municipality permitType)]
    (ok :operations (operations/selected-operations-for-organizations organizations))))

(defquery addable-operations
  {:description "returns operations addable for the application whose id is given as parameter"
   :parameters  [:id]
   :user-roles #{:applicant :authority}
   :states      states/pre-sent-application-states}
  [{{:keys [organization permitType]} :application}]
  (when-let [org (org/get-organization organization)]
    (let [selected-operations (map keyword (:selected-operations org))]
      (ok :operations (operations/addable-operations selected-operations permitType)))))

(defquery organization-details
  {:description "Resolves organization based on municipality and selected operation."
   :parameters [municipality operation]
   :input-validators [(partial non-blank-parameters [:municipality :operation])]
   :user-roles #{:applicant :authority}}
  [_]
  (let [permit-type (:permit-type ((keyword operation) operations/operations))]
    (if-let [organization (org/resolve-organization municipality permit-type)]
      (let [scope (org/resolve-organization-scope municipality permit-type organization)]
        (ok
          :inforequests-disabled (not (:inforequest-enabled scope))
          :new-applications-disabled (not (:new-application-enabled scope))
          :links (:links organization)
          :attachmentsForOp (-> organization :operations-attachments ((keyword operation)))))
      (fail :municipalityNotSupported))))

(defcommand set-organization-selected-operations
  {:parameters [operations]
   :user-roles #{:authorityAdmin}
   :input-validators  [(partial vector-parameters [:operations])
                       (fn [{{:keys [operations]} :data}]
                         (when-not (every? (->> operations/operations keys (map name) set) operations)
                           (fail :error.unknown-operation)))]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:selected-operations operations}})
  (ok))

(defcommand organization-operations-attachments
  {:parameters [operation attachments]
   :user-roles #{:authorityAdmin}
   :input-validators [(partial non-blank-parameters [:operation])
                      (partial vector-parameters [:attachments])
                      (fn [{{:keys [operation attachments]} :data, user :user}]
                        (let [organization (org/get-organization (usr/authority-admins-organization-id user))
                              selected-operations (set (:selected-operations organization))
                              allowed-types (att-type/get-attachment-types-for-operation operation)
                              attachment-types (map (fn [[group id]] {:type-group group :type-id id}) attachments)]
                          (cond
                            (not (selected-operations operation)) (do
                                                                    (error "Unknown operation: " (logging/sanitize 100 operation))
                                                                    (fail :error.unknown-operation))
                            (not-every? (partial att-type/contains? allowed-types) attachment-types) (fail :error.unknown-attachment-type))))]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {(str "operations-attachments." operation) attachments}})
  (ok))

(defcommand set-organization-app-required-fields-filling-obligatory
  {:parameters [enabled]
   :user-roles #{:authorityAdmin}
   :input-validators  [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:app-required-fields-filling-obligatory enabled}})
  (ok))

(defcommand set-organization-assignments
  {:parameters [enabled]
   :user-roles #{:authorityAdmin}
   :input-validators  [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:assignments-enabled enabled}})
  (ok))

(defcommand set-organization-inspection-summaries
  {:parameters [enabled]
   :user-roles #{:authorityAdmin}
   :input-validators  [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:inspection-summaries-enabled enabled}})
  (ok))

(defcommand set-organization-extended-construction-waste-report
  {:parameters [enabled]
   :user-roles #{:authorityAdmin}
   :pre-checks [(org/permit-type-validator :R)]
   :input-validators  [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:extended-construction-waste-report-enabled enabled}})
  (ok))

(defcommand set-organization-validate-verdict-given-date
  {:parameters [enabled]
   :user-roles #{:authorityAdmin}
   :input-validators  [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:validate-verdict-given-date enabled}})
  (ok))

(defcommand set-organization-use-attachment-links-integration
  {:parameters       [enabled]
   :user-roles       #{:authorityAdmin}
   :input-validators [(partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user) {$set {:use-attachment-links-integration enabled}})
  (ok))

(defcommand set-organization-calendars-enabled
  {:parameters [enabled organizationId]
   :user-roles #{:admin}
   :input-validators  [(partial non-blank-parameters [:organizationId])
                       (partial boolean-parameters [:enabled])]
   :feature :ajanvaraus}
  [{user :user}]
  (org/update-organization organizationId {$set {:calendars-enabled enabled}})
  (ok))

(defcommand set-organization-permanent-archive-enabled
  {:parameters [enabled organizationId]
   :user-roles #{:admin}
   :input-validators  [(partial non-blank-parameters [:organizationId])
                       (partial boolean-parameters [:enabled])]}
  [{user :user}]
  (org/update-organization organizationId {$set {:permanent-archive-enabled enabled}})
  (ok))

(defcommand set-organization-permanent-archive-start-date
  {:parameters [date]
   :user-roles #{:authorityAdmin}
   :input-validators  [(partial number-parameters [:date])]
   :pre-checks [(fn [{:keys [user]}]
                  (when-not (org/some-organization-has-archive-enabled? [(usr/authority-admins-organization-id user)])
                    unauthorized))]}
  [{user :user}]
  (when (pos? date)
    (org/update-organization (usr/authority-admins-organization-id user) {$set {:permanent-archive-in-use-since date}})
    (ok)))

(defn split-emails [emails] (ss/split emails #"[\s,;]+"))

(def email-list-validators [(partial action/string-parameters [:emails])
                            (fn [{{emails :emails} :data}]
                              (let [splitted (split-emails emails)]
                                (when (and (not (ss/blank? emails)) (some (complement v/valid-email?) splitted))
                                  (fail :error.email))))])

(defcommand set-organization-neighbor-order-email
  {:parameters [emails]
   :description "When application is submitted and the applicant wishes that the organization hears neighbours,
                 send notification to these email addresses"
   :user-roles #{:authorityAdmin}
   :input-validators email-list-validators}
  [{user :user}]
  (let [addresses (when-not (ss/blank? emails) (split-emails emails))
        organization-id (usr/authority-admins-organization-id user)]
    (org/update-organization organization-id {$set {:notifications.neighbor-order-emails addresses}})
    (ok)))

(defcommand set-organization-submit-notification-email
  {:parameters [emails]
   :description "When application is submitted, send notification to these email addresses"
   :user-roles #{:authorityAdmin}
   :input-validators email-list-validators}
  [{user :user}]
  (let [addresses (when-not (ss/blank? emails) (split-emails emails))
        organization-id (usr/authority-admins-organization-id user)]
    (org/update-organization organization-id {$set {:notifications.submit-notification-emails addresses}})
    (ok)))

(defcommand set-organization-inforequest-notification-email
  {:parameters [emails]
   :description "When inforequest is received to organization, send notification to these email addresses"
   :user-roles #{:authorityAdmin}
   :input-validators email-list-validators}
  [{user :user}]
  (let [addresses (when-not (ss/blank? emails) (split-emails emails))
        organization-id (usr/authority-admins-organization-id user)]
    (org/update-organization organization-id {$set {:notifications.inforequest-notification-emails addresses}})
    (ok)))

(defcommand set-organization-default-reservation-location
  {:parameters [location]
   :description "When reservation is made, use this location as default value"
   :user-roles #{:authorityAdmin}
   :input-validators [(partial action/string-parameters [:location])]
   :feature :ajanvaraus}
  [{user :user}]
  (let [organization-id (usr/authority-admins-organization-id user)]
    (org/update-organization organization-id {$set {:reservations.default-location location}})
    (ok)))

(defquery krysp-config
  {:user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organization-id (usr/authority-admins-organization-id user)]
    (if-let [organization (org/get-organization organization-id)]
      (let [permit-types (mapv (comp keyword :permitType) (:scope organization))
            krysp-keys   (conj permit-types :osoitteet)
            empty-confs  (zipmap krysp-keys (repeat {}))]
        (ok :krysp (merge empty-confs (:krysp organization))))
      (fail :error.unknown-organization))))

(defcommand set-krysp-endpoint
  {:parameters [:url username password permitType version]
   :user-roles #{:authorityAdmin}
   :input-validators [(fn [{{permit-type :permitType} :data}]
                        (when-not (or
                                    (= "osoitteet" permit-type)
                                    (permit/valid-permit-type? permit-type))
                          (fail :error.missing-parameters :parameters [:permitType])))
                      (partial validate-optional-url :url)]}
  [{data :data user :user}]
  (let [url             (-> data :url ss/trim)
        organization-id (usr/authority-admins-organization-id user)
        krysp-config    (org/get-krysp-wfs {:_id organization-id} permitType)
        password        (if (s/blank? password) (second (:credentials krysp-config)) password)]
    (if (or (s/blank? url) (wfs/wfs-is-alive? url username password))
      (org/set-krysp-endpoint organization-id url username password permitType version)
      (fail :auth-admin.legacyNotResponding))))

(defcommand set-kopiolaitos-info
  {:parameters [kopiolaitosEmail kopiolaitosOrdererAddress kopiolaitosOrdererPhone kopiolaitosOrdererEmail]
   :user-roles #{:authorityAdmin}
   :input-validators [(fn [{{email-str :kopiolaitosEmail} :data :as command}]
                        (let [emails (util/separate-emails email-str)]
                          ;; action/email-validator returns nil if email was valid
                          (when (some #(email-validator :email {:data {:email %}}) emails)
                            (fail :error.set-kopiolaitos-info.invalid-email))))]}
  [{user :user}]
  (org/update-organization (usr/authority-admins-organization-id user)
    {$set {:kopiolaitos-email kopiolaitosEmail
           :kopiolaitos-orderer-address kopiolaitosOrdererAddress
           :kopiolaitos-orderer-phone kopiolaitosOrdererPhone
           :kopiolaitos-orderer-email kopiolaitosOrdererEmail}})
  (ok))

(defquery kopiolaitos-config
  {:user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organization-id (usr/authority-admins-organization-id user)]
    (if-let [organization (org/get-organization organization-id)]
      (ok
        :kopiolaitos-email (:kopiolaitos-email organization)
        :kopiolaitos-orderer-address (:kopiolaitos-orderer-address organization)
        :kopiolaitos-orderer-phone (:kopiolaitos-orderer-phone organization)
        :kopiolaitos-orderer-email (:kopiolaitos-orderer-email organization))
      (fail :error.unknown-organization))))

(defquery get-organization-names
  {:description "Returns an organization id -> name map. (Used by TOJ.)"
   :user-roles #{:anonymous}}
  [_]
  (ok :names (into {} (for [{:keys [id name]} (org/get-organizations {} {:name 1})]
                        [id name]))))

(defquery vendor-backend-redirect-config
  {:user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [organization-id (usr/authority-admins-organization-id user)]
    (if-let [organization (org/get-organization organization-id)]
      (ok (:vendor-backend-redirect organization))
      (fail :error.unknown-organization))))

(defcommand save-vendor-backend-redirect-config
  {:parameters       [key val]
   :user-roles       #{:authorityAdmin}
   :input-validators [(fn [{{key :key} :data}]
                        (when-not (contains? #{:vendorBackendUrlForBackendId :vendorBackendUrlForLpId} (keyword key))
                          (fail :error.illegal-key)))
                      (partial validate-optional-url :val)]}
  [{user :user}]
  (let [key    (csk/->kebab-case key)
        org-id (usr/authority-admins-organization-id user)]
    (org/update-organization org-id {$set {(str "vendor-backend-redirect." key) (ss/trim val)}})))

(defcommand update-organization-name
  {:description "Updates organization name for different languages. 'name' should be a map with lang-id as key and name as value."
   :parameters       [org-id name]
   :user-roles       #{:authorityAdmin :admin}
   :pre-checks       [(fn [{{org-id :org-id} :data user :user}]
                        (when-not (or (usr/admin? user)
                                      (= org-id (usr/authority-admins-organization-id user)))
                          (fail :error.unauthorized)))]
   :input-validators [(fn [{{name :name} :data}]
                        (when-not (map? name)
                          (fail :error.invalid-type)))
                      (fn [{{name :name} :data}]
                        (when-not (every? (set i18n/supported-langs) (keys name))
                          (fail :error.illegal-key)))
                      (fn [{{name :name} :data}]
                        (when-not (every? string? (vals name))
                          (fail :error.invalid-type)))
                      (fn [{{name :name} :data}]
                        (when (some ss/blank? (vals name))
                          (fail :error.empty-organization-name)))]}
  [_]
  (->> (util/map-keys (fn->> clojure.core/name (str "name.")) name)
       (hash-map $set)
       (org/update-organization org-id)))

(defcommand save-organization-tags
  {:parameters [tags]
   :input-validators [(partial action/vector-parameter-of :tags map?)]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [org-id (usr/authority-admins-organization-id user)
        old-tag-ids (set (map :id (:tags (org/get-organization org-id))))
        new-tag-ids (set (map :id tags))
        removed-ids (set/difference old-tag-ids new-tag-ids)
        tags-with-ids (org/create-tag-ids tags)
        validation-errors (seq (remove nil? (map (partial sc/check org/Tag) tags-with-ids)))]
    (when validation-errors (fail! :error.missing-parameters))

    (when (seq removed-ids)
      (mongo/update-by-query :applications {:tags {$in removed-ids} :organization org-id} {$pull {:tags {$in removed-ids}}}))
    (org/update-organization org-id {$set {:tags tags-with-ids}})))

(defquery remove-tag-ok
  {:parameters [tagId]
   :input-validators [(partial non-blank-parameters [:tagId])]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [org-id (usr/authority-admins-organization-id user)]
    (when-let [tag-applications (seq (mongo/select
                                       :applications
                                       {:tags tagId :organization org-id}
                                       [:_id]))]
      (fail :warning.tags.removing-from-applications :applications tag-applications))))

(defquery get-organization-tags
  {:user-authz-roles #{:statementGiver}
   :org-authz-roles auth/reader-org-authz-roles
   :user-roles #{:authorityAdmin :authority}}
  [{{:keys [orgAuthz] :as user} :user}]
  (if (seq orgAuthz)
    (let [organization-tags (mongo/select
                                  :organizations
                                  {:_id {$in (keys orgAuthz)} :tags {$exists true}}
                                  [:tags :name])
          result (map (juxt :id #(select-keys % [:tags :name])) organization-tags)]
      (ok :tags (into {} result)))
    (ok :tags {})))

(defquery get-organization-areas
  {:user-authz-roles #{:statementGiver}
   :org-authz-roles  auth/reader-org-authz-roles
   :user-roles       #{:authorityAdmin :authority}}
  [{{:keys [orgAuthz] :as user} :user}]
  (if (seq orgAuthz)
    (let [organization-areas (mongo/select
                               :organizations
                               {:_id {$in (keys orgAuthz)} :areas-wgs84 {$exists true}}
                               [:areas-wgs84 :name])
          organization-areas (map #(clojure.set/rename-keys % {:areas-wgs84 :areas}) organization-areas)
          result (map (juxt :id #(select-keys % [:areas :name])) organization-areas)]
      (ok :areas (into {} result)))
    (ok :areas {})))

(defraw organization-area
  {:user-roles #{:authorityAdmin}}
  [{user :user {[{:keys [tempfile filename size]}] :files created :created} :data :as action}]
  (let [org-id (usr/authority-admins-organization-id user)
        filename (mime/sanitize-filename filename)
        content-type (mime/mime-type filename)
        file-info {:filename    filename
                   :contentType  content-type
                   :size         size
                   :organization org-id
                   :created      created}
        tmpdir (fs/temp-dir "area")]
    (try+
      (when-not (= (:contentType file-info) "application/zip")
        (fail! :error.illegal-shapefile))
      (let [areas (org/parse-shapefile-to-organization-areas org-id tempfile tmpdir)]
        (->> (assoc file-info :areas areas :ok true)
             (resp/json)
             (resp/content-type "application/json")
             (resp/status 200)))
      (catch [:sade.core/type :sade.core/fail] {:keys [text] :as all}
        (error "Failed to parse shapefile" text)
        (->> {:ok false :text text}
             (resp/json)
             (resp/status 200)))
      (catch Throwable t
        (error "Failed to parse shapefile" t)
        (->> {:ok false :text (.getMessage t)}
             (resp/json)
             (resp/status 200)))
      (finally
        (when tmpdir
          (fs/delete-dir tmpdir))))))

(defquery get-map-layers-data
  {:description "Organization server and layer details."
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [org-id (usr/authority-admins-organization-id user)
        {:keys [server layers]} (org/organization-map-layers-data org-id)]
    (ok :server (select-keys server [:url :username]), :layers layers)))

(defcommand update-map-server-details
  {:parameters [url username password]
   :input-validators [(partial validate-optional-url :url)]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (org/update-organization-map-server (usr/authority-admins-organization-id user)
                                    (ss/trim url) username password)
  (ok))

(defcommand update-user-layers
  {:parameters [layers]
   :input-validators [(partial action/vector-parameter-of :layers map?)]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (let [selected-layers (remove (comp ss/blank? :id) layers)
        validation-errors (remove nil? (map (partial sc/check org/Layer) selected-layers))]
    (if (zero? (count validation-errors))
      (do
        (org/update-organization (usr/authority-admins-organization-id user)
          {$set {:map-layers.layers selected-layers}})
        (ok))
      (fail :error.missing-parameters))))

(defcommand update-suti-server-details
  {:parameters [url username password]
   :input-validators [(partial validate-optional-url :url)]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (org/update-organization-suti-server (usr/authority-admins-organization-id user)
                                     (ss/trim url) username password)
  (ok))

(defraw waste-ads-feed
  {:description "Simple RSS feed for construction waste information."
   :parameters [fmt]
   :optional-parameters [org lang]
   :input-validators [org/valid-feed-format org/valid-org i18n/valid-language]
   :user-roles #{:anonymous}}
  ((memo/ttl waste-ads/waste-ads :ttl/threshold 900000)             ; 15 min
    (ss/upper-case org)
    (-> fmt ss/lower-case keyword)
    (-> (or lang :fi) ss/lower-case keyword)))

(defcommand section-toggle-enabled
  {:description      "Enable/disable section requirement for fetched
  verdicts support."
   :parameters       [flag]
   :input-validators [(partial action/boolean-parameters [:flag])]
   :user-roles       #{:authorityAdmin}}
  [{user :user}]
  (org/toggle-group-enabled (usr/authority-admins-organization-id user) :section flag))

(defcommand section-toggle-operation
  {:description "Toggles operation either requiring section or not."
   :parameters [operationId flag]
   :input-validators [(partial action/non-blank-parameters [:operationId])
                      (partial action/boolean-parameters [:flag])]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (org/toggle-group-operation (usr/authority-admins-organization user)
                              :section
                              (ss/trim operationId)
                              flag))

(defn- validate-handler-role-in-organization
  "Pre-check that fails if roleId is defined but not found in handler-roles of authority admin's organization."
  [{{role-id :roleId} :data user :user user-orgs :user-organizations}]
  (when-let [org (-> (usr/authority-admins-organization-id user)
                     (util/find-by-id user-orgs))]
    (when (and role-id (not (util/find-by-id role-id (:handler-roles org))))
      (fail :error.unknown-handler))))

(defn- validate-handler-role-not-general
  "Pre-check that fails if roleId is defined and found in handler-roles of authority admin's organization and is set as general."
  [{{role-id :roleId} :data user :user user-orgs :user-organizations}]
  (when-let [org (-> (usr/authority-admins-organization-id user)
                     (util/find-by-id user-orgs))]
    (when (and role-id (:general (util/find-by-id role-id (:handler-roles org))))
      (fail :error.illegal-handler-role))))

(defcommand upsert-handler-role
  {:description "Create and modify organization handler role"
   :parameters [name]
   :optional-parameters [roleId]
   :pre-checks [validate-handler-role-in-organization]
   :input-validators [(partial action/map-parameters-with-required-keys [:name] i18n/all-languages)]
   :user-roles #{:authorityAdmin}}
  [{user :user user-orgs :user-organizations}]
  (let [handler-role (org/create-handler-role roleId name)]
    (if (sc/check org/HandlerRole handler-role)
      (fail :error.missing-parameters)
      (do (-> (usr/authority-admins-organization-id user)
              (util/find-by-id user-orgs)
              (org/upsert-handler-role! handler-role))
          (ok :id (:id handler-role))))))

(defcommand disable-handler-role
  {:description "Set organization handler role disabled"
   :parameters [roleId]
   :pre-checks [validate-handler-role-in-organization
                validate-handler-role-not-general]
   :input-validators [(partial non-blank-parameters [:roleId])]
   :user-roles #{:authorityAdmin}}
  [{user :user}]
  (-> (usr/authority-admins-organization-id user)
      (org/disable-handler-role! roleId)))
