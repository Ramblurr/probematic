(ns app.email
  (:require
   [app.debug :as debug]
   [app.email.email-worker :as email-worker]
   [app.email.templates :as tmpl]
   [app.i18n :as i18n]
   [app.queries :as q]
   [app.util :as util]
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
   (tmpl/gig-created-email-html sys gig)
   (tmpl/gig-created-email-plain sys gig)
   (tmpl/gig-created-recipient-variables sys gig members)))

(defn build-gig-updated-email [{:keys [tr] :as sys} gig members edited-attrs]
  (build-batch-emails
   (mapv :member/email members)
   (tr [:email-subject/gig-updated] [(:gig/title gig)])
   (tmpl/gig-updated-email-html sys gig edited-attrs)
   (tmpl/gig-updated-email-plain sys gig edited-attrs)
   (tmpl/gig-updated-recipient-variables sys gig members)))

(defn- sys-from-req [req]
  {:tr (:tempura/tr req)
   :env (-> req :system :env)
   :redis (-> req :system :redis)})

(defn send-gig-created! [req gig-id]
  (let [db (datomic/db (:datomic-conn req))
        gig (q/retrieve-gig  db gig-id)
        members (q/active-members db)
        sys (sys-from-req req)]
    (queue-email! sys  (build-gig-created-email sys gig members))))

(defn send-gig-updated! [req gig-id edited-attrs]
  (when (>  (count edited-attrs) 0)
    (let [db (datomic/db (:datomic-conn req))
          gig (q/retrieve-gig  db gig-id)
          members (q/active-members db)
          sys (sys-from-req req)]
      (queue-email! sys  (build-gig-updated-email sys gig members edited-attrs)))))

(comment

  (do

    (require '[integrant.repl.state :as state])
    (def gig {:gig/pay-deal "Unterkunft, Verpflegung kostet nix, wir bekommen auch einen kräftigen Fahrtkostenzuschuss, und weitgehend Essen&trinken. die bandkassa nehmen wir mit für noch mehr Essen&Trinken."
              :gig/status :gig.status/confirmed
              :gig/title "Skappa’nabanda!"
              :gig/gig-id "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgIDg4ZGXCAw"
              :gig/gig-type :gig.type/gig
              :gig/setlist "Programm für Donnerstag im Detail:\r"
              :gig/more-details "FestivalOrt:\r"
              :gig/date #inst "2015-06-03T22:00:00.000-00:00"
              :gig/location "GRAZ"})
    (def gig2 {:gig/status :gig.status/confirmed
               :gig/call-time (t/time "18:47")
               :gig/title "Probe"
               :gig/gig-id "0185a673-9f2d-8b0e-8f1a-e70db25c9add"
               :gig/contact {:member/name "SNOrchestra"
                             :member/gigo-key "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA6K70hwoM"
                             :member/nick "SNO"}
               :gig/gig-type :gig.type/probe
               :gig/set-time (t/time "19:00")
               :gig/date  (t/date "2023-01-18")
               :gig/location "Proberaum in den Bögen"})

    (def member {:member/email "me@example.com"
                 :member/gigo-key "abcd123u"})

    (def member2 {:member/email "me+test@example.com"
                  :member/gigo-key "zxcysdf"})

    (def redis-opts (-> state/system :app.ig/redis)) ;; rcf

    (def tr (i18n/tr-with (i18n/read-langs) ["en"]))
    (def sys {:tr tr :env {:app-secret-key "hunter2" :app-base-url "https://foobar.com"}})) ;; rcf

  (spit "plain-email.txt"
        (str
         "\n++++\n"
         (tmpl/gig-created-email-plain sys gig)
         "\n++++\n"
         (tmpl/gig-created-email-plain sys gig2))) ;; rcf

  (spit "plain-email.txt"
        (str
         "\n++++\n"
         (tmpl/gig-updated-email-plain sys gig [:gig/status])
         "\n++++\n"
         (tmpl/gig-updated-email-plain sys gig2 [:gig/status]))) ;; rcf

  (build-gig-created-email sys gig [member member2])
  (build-gig-updated-email sys gig [member member2] [:gig/status])

  (queue-email! {:redis redis-opts} (debug/xxx (build-gig-created-email sys gig2 [member2])))
  (queue-email! {:redis redis-opts} (debug/xxx (build-gig-updated-email sys gig2 [member2] [:gig/status :gig/location])))

  ;;
  )
