(ns lupapalvelu.assignment-api
  (:require [lupapalvelu.action :as action :refer [defcommand defquery parameters-matching-schema]]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.schemas :as ssc]))

;; Helpers and validators

(defn- userid->summary [id]
  (usr/summary (usr/get-user-by-id id)))

(defn- userid->session-summary [id]
  (usr/session-summary (usr/get-user-by-id id)))

(defn- validate-receiver [{{:keys [organization]} :application
                           {:keys [recipientId]}    :data}]
  (when (and recipientId
             (not (usr/user-is-authority-in-organization? (userid->session-summary recipientId)
                                                          organization)))
    (fail :error.invalid-assignment-receiver)))


;;
;; Queries
;;

(defquery assignments
  {:description "Return all the assignments the user is allowed to see"
   :user-roles #{:authority}
   :feature :assignments}
  [{user :user}]
  (ok :assignments (assignment/get-assignments user)))

(defquery assignments-for-application
  {:description "Return the assignments for the current application"
   :user-roles #{:authority}
   :feature :assignments}
  [{user     :user
    {id :id} :application}]
  (ok :assignments (assignment/get-assignments-for-application user id)))

(defquery assignment
  {:description "Return a single assignment"
   :user-roles #{:authority}
   :parameters [assignmentId]
   :input-validators [(partial action/parameters-matching-schema [:assignmentId] ssc/ObjectIdStr)]
   :feature :assignments}
  [{user :user}]
  (ok :assignment (assignment/get-assignment user assignmentId)))

;;
;; Commands
;;

(defcommand create-assignment
  {:description      "Create an assignment"
   :user-roles       #{:authority}
   :parameters       [recipientId target description]
   :input-validators [(partial action/non-blank-parameters [:recipientId :description])
                      (partial action/vector-parameters [:target])]
   :pre-checks       [validate-receiver]
   :states           states/all-application-states-but-draft-or-terminal
   :feature          :assignments}
  [{user                      :user
    created                   :created
    {:keys [organization id]} :application}]
  (ok :id (assignment/insert-assignment {:organizationId organization
                                         :applicationId  id
                                         :creator        (usr/summary user)
                                         :recipient      (userid->summary recipientId)
                                         :target         target
                                         :description    description}
                                        created)))

(defcommand complete-assignment
  {:description "Complete an assignment"
   :user-roles #{:authority}
   :parameters [assignmentId]
   :input-validators [(partial action/non-blank-parameters [:assignmentId])]
   :feature :assignments}
  [{user    :user
    created :created}]
  (if (> (assignment/complete-assignment assignmentId user created) 0)
    (ok)
    (fail :error.assignment-not-completed)))
