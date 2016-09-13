(ns lupapalvelu.info-links-api
  (:require [clojure.set :refer [union]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [sade.core :refer [ok fail fail!]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.info-links :as info-links]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.states :as states]
            [lupapalvelu.i18n :as i18n]))

;;
;; API
;;

(defcommand info-link-delete
  {:description      "Remove an application-specific info-link"
   :user-roles       #{:authority :applicant}
   :user-authz-roles #{:statementGiver}
   :parameters       [id linkId]
   :input-validators [(partial action/number-parameters [:linkId])]
   :states           (states/all-states-but :draft)}
  [command]
  (ok :res (info-links/delete-info-link! (:application command) linkId)))

(defcommand info-link-reorder
  {:description      "Reorder application-specific info-links"
   :user-roles       #{:authority :applicant}
   :user-authz-roles #{:statementGiver}
   :parameters       [id linkIds]
   :input-validators [(partial action/vector-parameter-of :linkIds number?)]
   ;:input-validators [(partial action/vector-parameters-with-non-blank-items [:linkIds])]
   :states           (states/all-states-but :draft)}
  [command]
  (ok :res (info-links/reorder-info-links! (:application command) linkIds)))

(defcommand info-link-upsert
  {:description         "Add or update application-specific info-link"
   :user-roles          #{:authority :applicant}
   :user-authz-roles    #{:statementGiver}
   :parameters          [id text url]
   :optional-parameters [linkId]
   :input-validators    [(partial action/non-blank-parameters [:text])
                         (partial action/non-blank-parameters [:url])
                         (partial action/optional-parameter-of :linkId number?)]
   :states              (states/all-states-but :draft)}
  [command]
  (let [app (:application command)
        res (if linkId
              (info-links/update-info-link! app linkId text url)
              (info-links/add-info-link! app text url))]
    (ok :linkId res)))

(defquery info-links
  {:description      "Return a list of application-specific info-links"
   :parameters       [id]
   :user-roles       #{:authority :applicant}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles
   :states           states/all-states}
  [command]
  (let [app (:application command)]
    (ok :links (info-links/info-links-with-flags app (:user command)))))

(defquery organization-links
  {:description      "Authority admin defined organization links."
   :parameters       [id lang]
   :input-validators [i18n/valid-language]
   :user-roles       #{:authority :applicant}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles
   :states           states/all-states}
  [{:keys [application user]}]
  (ok :links (info-links/organization-links (:organization application)
                                            (:id user)
                                            lang)))

(defcommand mark-seen-organization-links
  {:description      "The user has seen the application's organization links."
   :user-roles       #{:authority :applicant}
   :parameters       [id]
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles
   :states           states/all-states
   :pre-checks       [app/validate-authority-in-drafts]}
  [{:keys [application user created]}]
  (info-links/mark-seen-organization-links (:organization application)
                                           (:id user)
                                           created))
