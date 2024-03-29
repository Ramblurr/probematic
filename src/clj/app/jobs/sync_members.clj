(ns app.jobs.sync-members
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [datomic.client.api :as d]
   [ol.jobs-util :as jobs]
   [app.features :as f]
   [app.util :as util]
   [app.airtable :as airtable]
   [medley.core :as m]
   [app.twilio :as twilio]))

;; This job syncs the member phone numbers and gigo ids from airtable
;; into our local database

;; things we change in airtable
;;   - telephone numbers (lookedup and verified)

;; things we need to sync
;;
;;   - member names
;;   - gigo keys
;;   - phone numbers

(defn fetch-people-data! [atc]
  (as-> (airtable/list-records! atc (:gigo-linked-people-table atc)) v
    (get v "records")
    (mapv #(get % "fields") v)
    (mapv #(select-keys % ["Name" "Gigo ID" "Phone Number" "E-Mail" "Bandaktivität"]) v)
    (mapv #(set/rename-keys % {"Gigo ID"       :gigo-id
                               "Phone Number"  :phone
                               "E-Mail"        :email
                               "Bandaktivität" :active?
                               "Name"          :name}) v)
    (mapv #(update-in % [:gigo-id] first) v)))

(defn member-tx [person]
  (let [active-lookup {"Aktiv" true
                       "Inaktiv" false}]
    (->
     {:member/gigo-key (:gigo-id person)
      :member/name     (:name person)
      :member/email    (:email person)
      :member/active?  (get active-lookup (:active? person))}

     (m/assoc-some :member/phone (util/no-blanks (util/clean-number (:phone person)))))))

(defn update-member-data! [conn people]
  (d/transact conn {:tx-data  (mapv member-tx people)}))

(defn clean-phone-numbers [atc twi]
  (filter some?
          (mapv (fn [r]
                  (let [orig-number (get-in r ["fields" "Phone Number"])
                        number (util/clean-number orig-number)
                        id (get-in r ["id"])]
                    (when (not-empty number)
                      (if (str/starts-with? number "+")
                        ;; basic cleanup
                        (when (not= orig-number number)
                          {"id"     id
                           "fields" {"Phone Number" number}})
                        ;; proper cleanup - run it through the twilio lookup api
                        ;; this costs money though, so don't do it all the time
                        (let [lookup (twilio/lookup-number twi number "AT")]
                          (when (contains? lookup "phone_number")
                            {"id"     id
                             "fields" {"Phone Number" (get lookup "phone_number")}}))))))
                (get (airtable/list-records! atc (:gigo-linked-people-table atc)) "records"))))

(defn clean-phone-numbers! [atc twi]
  (let [to-update (partition 10 10 nil (clean-phone-numbers atc twi))]
    (run!
     #(airtable/patch-records! atc (:all-people-table atc) %)
     to-update)))

(defn- sync-members! [conn airtable-config twilio-config _]
  (when (f/feature? :feat/sync-members)
    (tap> "syncing members")
    (clean-phone-numbers! airtable-config twilio-config)
    (update-member-data! conn (fetch-people-data! airtable-config))))

(defn make-sync-members-job [{:keys [env conn]}]
  (fn [{:job/keys [frequency initial-delay]}]
    (tap> "register sync-members job")
    (jobs/make-repeating-job (partial #'sync-members! conn (:airtable env) (:twilio env)) frequency initial-delay)))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require '[app.db :as db])
    (def _opts {:env  (:app.ig/env state/system)
                :db   (:app.ig/env state/system)
                ;; :conn (:ol.datahike.ig/connection state/system)
                :conn (-> state/system :app.ig/datomic-db :conn)})) ;; rcf

  (fetch-people-data! (-> _opts :env :airtable))
  (lookup-number (-> _opts :env :twilio) "+43600000" "AT")
  (lookup-number (-> _opts :env :twilio) "Kein Telefon" "AT")
  (clean-phone-numbers (-> _opts :env :airtable) (-> _opts :env :twilio))
  (clean-phone-numbers! (-> _opts :env :airtable) (-> _opts :env :twilio))
  (db/members @(:conn _opts))

  (d/transact (:conn _opts) {:tx-data  (mapv member-tx (fetch-people-data! (-> _opts :env :airtable)))})

  (update-member-data! (:conn _opts) (fetch-people-data! (-> _opts :env :airtable)))
  (d/transact (:conn _opts) {:tx-data []})

  (sync-members! (:conn _opts) (-> _opts :env :airtable) (-> _opts :env :twilio) nil)

  (d/transact (:conn _opts) [[:db.fn/retractAttribute 16 :member/permission-granted?]])

                                        ;
  )
