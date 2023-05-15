(ns app.jobs.reminders
  (:require
   [app.email :as email]
   [app.gigs.domain :as domain]
   [app.errors :as errors]
   [app.queries :as q]
   [clojure.tools.logging :as log]
   [datomic.client.api :as datomic]
   [ol.jobs-util :as jobs]
   [tick.core :as t]))

(defn- process-gig-reminder
  [db {:reminder/keys [reminder-id reminder-status remind-at member gig] :as reminder}]
  ;; (tap> reminder)
  (if (or  (domain/cancelled? gig)
           (domain/in-past? gig))
    ;; gig not relevant anymore, cancel reminder
    (do
      (tap> "cancel reminder gig no longer relevant")
      {:reminder/reminder-status :reminder-status/cancelled :reminder/reminder-id reminder-id})
    (let [attendance (q/attendance-for-gig db (:gig/gig-id gig) (:member/member-id member))]
      (if (domain/no-response? (:attendance/plan attendance))
        ;; send reminder
        {:member member :gig gig :reminder-id reminder-id}
        ;; member already marked attendance, cancel reminder
        (do
          (tap> "cancel reminder, member responded")
          {:reminder/reminder-status :reminder-status/cancelled :reminder/reminder-id reminder-id})))))

(defn- group-processed-reminders [acc processed-reminder]
  ;; (tap> processed-reminder)
  (if (:reminder/reminder-status processed-reminder)
    (update acc :to-cancel conj (:reminder/reminder-id processed-reminder))
    (update-in acc [:to-send (-> processed-reminder :gig :gig/gig-id)] conj
               {:reminder-id (:reminder-id processed-reminder)
                :member (:member processed-reminder)})))

(defn process-reminders [db reminders as-of]
  (tap> {:reminders reminders :as-of as-of})
  (->> reminders
       (map (partial process-gig-reminder db))
       (reduce group-processed-reminders
               {:to-cancel #{} :to-send {}})))

(defn- send-reminder-for-gig! [sys [gig-id reminder-members]]
  ;; (tap> {:gig gig-id :m reminder-members})
  (email/send-gig-reminder-to! sys gig-id (mapv :member reminder-members))
  (mapv :reminder-id reminder-members))

(defn- send-gig-reminders! [sys reminders as-of]
  (let [{:keys [to-send to-cancel]} (process-reminders (:db sys) reminders as-of)
        ;; _ (tap> {:to-send to-send})
        sent-reminder-ids (doall (mapv (partial send-reminder-for-gig! sys) to-send))]
    ;; (tap> {:to-cancel to-cancel :sent sent-reminder-ids})
    (concat
     (map (fn [reminder-id]
            [:db/add [:reminder/reminder-id reminder-id] :reminder/reminder-status :reminder-status/cancelled]) to-cancel)
     (map (fn [reminder-id]
            [:db/add [:reminder/reminder-id (first reminder-id)] :reminder/reminder-status :reminder-status/sent]) sent-reminder-ids))))

(defn- send-reminders! [{:keys [datomic] :as system} _]
  (log/info "sending reminders")
  (try
    (let [datomic-conn (:conn datomic)
          db (datomic/db datomic-conn)
          reminders (q/overdue-reminders-by-type db)
          tx-data (send-gig-reminders!
                   (assoc system :db db :datomic-conn datomic-conn)
                   (:reminder-type/gig-attendance reminders)
                   (t/instant))]
      (tap> {:send-reminder-result tx-data})
      (when (seq tx-data)
        (datomic/transact datomic-conn {:tx-data tx-data}))
      :done)
    (catch Throwable e
      (tap> e)
      (errors/report-error! e))
    ))

(defn make-reminder-job [system]
  (fn [{:job/keys [frequency initial-delay]}]
    (jobs/make-repeating-job (partial send-reminders! system) frequency initial-delay)))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db  (datomic/db conn))
    (def system {:datomic {:conn conn}
                 :redis (-> state/system :app.ig/redis)
                 :i18n-langs (-> state/system :app.ig/i18n-langs)
                 :env (-> state/system :app.ig/env)})) ;; rcf

  (q/attendance-for-gig db (parse-uuid "0187e415-7b26-8e55-9371-4391baf9fe09") (parse-uuid "01860c2a-2929-8727-af1a-5545941b1111"))
  (q/attendance-for-gig db (parse-uuid "01875c27-376f-85ab-b492-9c9677c6d224") (parse-uuid "01860c2a-2929-8727-af1a-5545941b1111"))

  (send-gig-reminders! db [{:reminder/reminder-id "test"
                            :reminder/member (q/member-by-email db "REDACTED")
                            :reminder/gig  (first (q/gigs-future db))
                            :reminder/reminder-status :reminder-status/pending
                            :reminder/remind-at (t/tomorrow)}]
                       (t/instant))

  (let [as-of (t/>> (t/instant) (t/new-period 2 :days))]
    (process-reminders db (:reminder-type/gig-attendance (q/overdue-reminders-by-type db as-of)) as-of))
  (q/active-reminders-by-type db)
  (send-reminders! system nil)
  (datomic/transact conn {:tx-data
                          [[:db/retractEntity [:reminder/reminder-id #uuid "0187e791-2bf8-8ca0-ac0d-6eeb435aa78e"]]]})
  ;;
  )
