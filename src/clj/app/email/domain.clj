(ns app.email.domain
  (:require
   [app.schemas :as s]))

(def sent-statuses #{:email-status/not-sent
                     :email-status/sent
                     :email-status/send-error})

(def QueuedEmailMessage
  (s/schema
   [:map {:name :app.entity/queued-email}
    [:email/email-id ::s/non-blank-string]
    [:email/tos [:vector ::s/email-address]]
    [:email/subject ::s/non-blank-string]
    [:email/body-html ::s/non-blank-string]
    [:email/body-plain ::s/non-blank-string]
    [:email/batch? :boolean]
    [:email/recipient-variables {:optional true} :map]
    ;; [:email/sent-status (s/enum-from sent-statuses)]
    [:email/created-at {:optional true} ::s/inst]]))
