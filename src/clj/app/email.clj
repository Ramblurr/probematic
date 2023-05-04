(ns app.email
  (:require
   [app.debug :as debug]
   [app.email.email-worker :as email-worker]
   [app.email.templates :as tmpl]
   [app.i18n :as i18n]
   [app.queries :as q]
   [app.ui :as ui]
   [app.urls :as url]
   [app.util :as util]
   [clojure.tools.logging :as log]
   [com.yetanalytics.squuid :as sq]
   [datomic.client.api :as datomic]
   [tick.core :as t]))

(defn queue-email! [sys email]
  (email-worker/queue-mail! (:redis sys) email))

(defn build-email [to subject body-html body-plain]
  (util/remove-nils
   {:email/batch? false
    :email/email-id (sq/generate-squuid)
    :email/tos [to]
    :email/subject subject
    :email/body-plain body-plain
    :email/body-html body-html
    :email/created-at (t/inst)}))

(defn build-batch-emails [tos subject body-html body-plain recipient-variables]
  (util/remove-nils
   {:email/batch? true
    :email/recipient-variables recipient-variables
    :email/email-id (sq/generate-squuid)
    :email/tos tos
    :email/subject subject
    :email/body-plain body-plain
    :email/body-html body-html
    :email/created-at (t/inst)}))

(defn build-gig-created-email [{:keys [tr] :as sys} gig members]
  (build-batch-emails
   (mapv :member/email members)
   (tr [:email-subject/gig-created] [(:gig/title gig)])
   (tmpl/gig-created-email-html sys gig false)
   (tmpl/gig-created-email-plain sys gig false)
   (tmpl/gig-created-recipient-variables sys gig members)))

(defn build-gig-updated-email [{:keys [tr] :as sys} gig members edited-attrs]
  (build-batch-emails
   (mapv :member/email members)
   (tr [:email-subject/gig-updated] [(:gig/title gig)])
   (tmpl/gig-updated-email-html sys gig edited-attrs)
   (tmpl/gig-updated-email-plain sys gig edited-attrs)
   (tmpl/gig-updated-recipient-variables sys gig members)))

(defn build-gig-reminder-email [{:keys [tr] :as sys} gig members]
  (build-batch-emails
   (mapv :member/email members)
   (tr [:email-subject/gig-reminder] [(:gig/title gig)])
   (tmpl/gig-created-email-html sys gig true)
   (tmpl/gig-created-email-plain sys gig true)
   (tmpl/gig-created-recipient-variables sys gig members)))

(defn build-new-user-invite [{:keys [tr] :as sys} {:member/keys [email] :as member} invite-code]
  (build-email email
               (tr [:email-subject/new-invite])
               (tmpl/new-user-invite-html sys invite-code)
               (tmpl/new-user-invite-plain sys invite-code)))

(defn build-generic-email [{:keys [tr] :as sys} to-email subject body-text cta-text cta-url]
  (build-email to-email
               subject
               (tmpl/generic-email-html sys body-text cta-text cta-url)
               (tmpl/generic-email-plain sys body-text cta-text cta-url)))

(defn- sys-from-req [req]
  {:tr (:tr req)
   :env (-> req :system :env)
   :redis (-> req :system :redis)
   :datomic-conn (-> req :datomic-conn)})

(defn send-gig-created! [req gig-id]
  (let [db (datomic/db (:datomic-conn req))
        gig (q/retrieve-gig  db gig-id)
        members (q/active-members db)
        sys (sys-from-req req)]
    (queue-email! sys  (build-gig-created-email sys gig members))))

(defn send-gig-reminder-to! [{:keys [datomic-conn tr env redis]} gig-id members]
  (assert datomic-conn)
  (assert env)
  (assert redis)
  (let [db (datomic/db datomic-conn)
        sys {:tr tr :env env :redis redis}
        gig (q/retrieve-gig db gig-id)]
    (queue-email! sys (build-gig-reminder-email sys gig members))))

(defn send-gig-reminder-to-all! [{:keys [db] :as req}  gig-id]
  (let [attendance (q/attendance-for-gig-with-all-active-members db gig-id)
        members (->> (q/attendance-plans-by-section-for-gig db attendance
                                                            :no-response?)
                     (mapcat :members)
                     (map :attendance/member))]
    ;; (tap> {:attendance attendance :members members})
    (send-gig-reminder-to! (sys-from-req req) gig-id members)
    :done))

(defn send-gig-updated! [req gig-id edited-attrs]
  (when (>  (count edited-attrs) 0)
    (let [db (datomic/db (:datomic-conn req))
          gig (q/retrieve-gig  db gig-id)
          members (q/active-members db)
          sys (sys-from-req req)]
      (queue-email! sys  (build-gig-updated-email sys gig members edited-attrs)))))

(defn send-new-user-email! [req new-member invite-code]
  (log/info (format "new user email to %s code=%s" (:member/email new-member) invite-code))
  (queue-email! (sys-from-req req)
                (build-new-user-invite (sys-from-req req) new-member invite-code)))

(defn send-admin-email! [req from-member req-human-id]
  (let [member-name (:member/name from-member)
        admin-email (-> req :system :env :admin-email)]
    (assert admin-email)
    (queue-email! (sys-from-req req)
                  (build-generic-email (sys-from-req req)
                                       admin-email
                                       (str "SNOrga Error from " member-name)
                                       (format "There was a SNOrga error that needs attention from %s with human-id %s" member-name req-human-id)
                                       nil
                                       nil))))
(defn send-rehearsal-leader-email! [{:keys [i18n-langs env redis]} gig leader-member]
  (assert gig)
  (assert leader-member)
  (let [tr (i18n/tr-with i18n-langs [:en])
        sys {:tr tr :env  env :redis redis}]
    (build-generic-email sys
                         (:member/email leader-member)
                         (tr [:email/subject-log-plays])
                         (tr [:email/body-log-plays] [(ui/gig-date-plain gig)])
                         (tr [:email/cta-log-plays])
                         (url/absolute-link-gig env (:gig/gig-id gig)))))

(comment

  (do

    (require '[integrant.repl.state :as state])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn))
    (def gig (q/retrieve-gig db "01863829-2527-89fb-a582-4bd00f40c6b2"))
    (def gig2 {:gig/status :gig.status/confirmed
               :gig/call-time (t/time "18:47")
               :gig/title "Probe"
               :gig/gig-id "0185a673-9f2d-8b0e-8f1a-e70db25c9add"
               :gig/contact {:member/name "SNOrchestra"
                             :member/member-id "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA6K70hwoM"
                             :member/nick "SNO"}
               :gig/gig-type :gig.type/probe
               :gig/set-time (t/time "19:00")
               :gig/date (t/date "2023-01-30")
               :gig/location "Proberaum in den Bögen"})
    (require '[app.config :as config])

    (def member {:member/email "me@example.com"
                 :member/member-id "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA2NP7ggoM"})

    (def member2 {:member/email "me+test@example.com"
                  :member/member-id "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA2NP7ggoM"})

    (def redis-opts (-> state/system :app.ig/redis))
    (def env (-> state/system :app.ig/env))

    (def tr (i18n/tr-with (i18n/read-langs) ["en"]))
    (def sys {:tr tr :env env})) ;; rcf

  (spit "plain-email.txt"
        (str
         "\n++++\n"
         (tmpl/gig-created-email-plain sys gig)
         "\n++++\n"
         (tmpl/gig-created-email-plain sys gig2)))

  (spit "plain-email.txt"
        (str
         "\n++++\n"
         (tmpl/gig-updated-email-plain sys gig [:gig/status])
         "\n++++\n"
         (tmpl/gig-updated-email-plain sys gig2 [:gig/status])))

  (build-gig-created-email sys gig [member])
  :email/recipient-variables
  (build-gig-updated-email sys gig [member member2] [:gig/status])

  (queue-email! {:redis redis-opts} (debug/xxx (build-gig-created-email sys gig2 [member2])))
  (queue-email! {:redis redis-opts} (debug/xxx (build-gig-updated-email sys gig2 [member2] [:gig/status :gig/location])))

  (tmpl/payload-for-attendance env (:gig/gig-id gig) (:member/member-id
                                                      (q/member-by-email db "CHANGEME")) :plan/definitely)

;;
  )
