(ns app.jobs.poll-housekeeping
  (:require
   [clojure.tools.logging :as log]
   [app.datomic :as d]
   [app.errors :as errors]
   [app.queries :as q]
   [datomic.client.api :as datomic]
   [ol.jobs-util :as jobs]
   [tick.core :as t]))

(defn- poll-housekeeping-job
  [{:keys [datomic] :as system} _]
  (try
    (let [conn (:conn datomic)
          open-polls (q/find-open-polls (datomic/db conn))
          now (t/instant)
          polls-to-close (filter (fn [{:poll/keys [closes-at]}]
                                   (let [closes-at (t/instant (t/in closes-at (t/zone "Europe/Vienna")))]
                                     (t/< closes-at now))) open-polls)
          polls-to-close-txns (mapcat #(into [] %)
                                      (map (fn [poll]
                                             (log/info "Going to close poll"
                                                       :poll-id (:poll/poll-id poll)
                                                       :poll-title (:poll/title poll)
                                                       :poll-closes-at (:poll/closes-at poll)
                                                       :poll-closes-at-inst (t/instant (t/in (:poll/closes-at poll) (t/zone "Europe/Vienna")))
                                                       :now now)
                                             [[:db/add (d/ref poll) :poll/poll-status :poll.status/closed]
                                              [:db/add (d/ref poll) :poll/closes-at (t/inst (t/in (t/date-time) (t/zone "Europe/Vienna")))]]) polls-to-close))]




      (datomic/transact conn  {:tx-data polls-to-close-txns})
      :done)
    (catch Throwable e
      (tap> e)
      (errors/report-error! e))))

(defn make-poll-housekeeping-job
  [system]
  (fn [{:job/keys [frequency initial-delay]}]
    (jobs/make-repeating-job (partial poll-housekeeping-job system) frequency initial-delay)))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db  (datomic/db conn))
    (def system {:datomic {:conn conn}
                 :redis (-> state/system :app.ig/redis)
                 :i18n-langs (-> state/system :app.ig/i18n-langs)
                 :env (-> state/system :app.ig/env)}))

  (poll-housekeeping-job system nil) ;; rcf

  ;;
  )
