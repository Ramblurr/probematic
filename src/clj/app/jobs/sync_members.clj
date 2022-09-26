(ns app.jobs.sync-members
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [datahike.api :as d]
   [jsonista.core :as j]
   [ol.jobs-util :as jobs]
   [org.httpkit.client :as client]

   [app.features :as f]
   [app.util :as util]
   [app.airtable :as airtable]
   [medley.core :as m]))

;; This job syncs the member phone numbers and gigo ids from airtable
;; into our local database

;; things we change in airtable
;;   - telephone numbers (lookedup and verified)

;; things we need to sync
;;
;;   - member names
;;   - gigo keys
;;   - phone numbers

(defn lookup-number
  ([twi number]
   (lookup-number twi number ""))
  ([twi number country-code]
   (let [resp @(client/request {:url          (str "https://lookups.twilio.com/v1/PhoneNumbers/" (client/url-encode number))
                                :query-params {"CountryCode" country-code}
                                :method       :get
                                :basic-auth   [(:twilio-account twi) (:twilio-token twi)]})]
     (if (= 200 (:status resp))
       (j/read-value (:body resp))
       resp))))

(defn fetch-people-data! [atc]
  (as-> (airtable/list-records! atc (:gigo-linked-people-table atc)) v
    (get v "records")
    (mapv #(get % "fields") v)
    (mapv #(select-keys % ["Name" "Gigo ID" "Phone Number"]) v)
    (mapv #(set/rename-keys % {"Gigo ID"      :gigo-id
                               "Phone Number" :phone
                               "Name"         :name}) v)
    (mapv #(update-in % [:gigo-id] first) v)))

(defn member-tx [person]
  (->
   {:member/gigo-key (:gigo-id person)
    :member/name     (:name person)}
   (m/assoc-some :member/phone (util/no-blanks (util/clean-number (:phone person))))))

(defn update-member-data! [conn people]
  (d/transact conn (mapv member-tx people)))

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
                        (let [lookup (lookup-number twi number "AT")]
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
                :conn (:ol.datahike.ig/connection state/system)}))

  (fetch-people-data! (-> _opts :env :airtable))
  (lookup-number (-> _opts :env :twilio) "+43600000" "AT")
  (lookup-number (-> _opts :env :twilio) "Kein Telefon" "AT")
  (clean-phone-numbers (-> _opts :env :airtable) (-> _opts :env :twilio))
  (clean-phone-numbers! (-> _opts :env :airtable) (-> _opts :env :twilio))
  (db/members @(:conn _opts))
  (update-member-data! (:conn _opts) (fetch-people-data! (-> _opts :env :airtable)))

  (sync-members! (:conn _opts) (-> _opts :env :airtable) (-> _opts :env :twilio) nil)

  (d/transact (:conn _opts) [[:db.fn/retractAttribute 16 :member/permission-granted?]])

  ;
  )
