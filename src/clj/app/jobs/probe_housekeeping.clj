(ns app.jobs.probe-housekeeping
  (:require
   [app.gigs.domain :as domain]
   [app.probeplan :as probeplan]
   [app.queries :as q]
   [app.routes.errors :as errors]
   [com.yetanalytics.squuid :as sq]
   [datomic.client.api :as datomic]
   [ol.jobs-util :as jobs]
   [tick.core :as t]
   [app.datomic :as d]))

(def minimum-gigs 4)
(def maximum-create 4)

(defn on-date? [date probe]
  (= (:gig/date probe)  date))

(defn- find-probe-dates
  "Returns a list of dates that need probes"
  [n next-probes]
  (let [next-wednesdays (->> (probeplan/wednesday-sequence (t/today))
                             (take 10)
                             (mapv t/date))]
    (->> next-wednesdays
         (remove #(some (partial on-date? %) next-probes))
         (take n))))

(defn newprobe-tx [date]
  (domain/gig->db
   {:gig/gig-id (sq/generate-squuid)
    :gig/title  "Probe"
    :gig/status :gig.status/confirmed
    :gig/date date
    :gig/gig-type :gig.type/probe
    :gig/location "Proberaum in den Bögen"
    :gig/call-time (t/time "18:45")
    :gig/set-time (t/time "19:00")
    :gig/contact [:member/gigo-key "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA6K70hwoM"]}))

(defn create-probes! [conn probes]
  (let [probe-dates (find-probe-dates (- minimum-gigs (count probes)) probes)
        txs (take maximum-create (map newprobe-tx probe-dates))]
    (datomic/transact conn {:tx-data txs})))

(defn assign-rehearsal-leaders! [conn]
  (let [db (datomic/db conn)
        prev-probe (q/previous-probe db)
        next-probe (q/next-probe db)
        last-leader2 (:gig/rehearsal-leader2 prev-probe)
        next-leader1 (:gig/rehearsal-leader1 next-probe)]
    (when (and (some? last-leader2)  (nil? next-leader1))
      (datomic/transact conn {:tx-data [[:db/add (d/ref next-probe) :gig/rehearsal-leader1 (d/ref last-leader2)]]}))))

(defn- probe-housekeeping-job [{:keys [datomic] :as system} _]
  (try
    (let [conn (:conn datomic)
          probes (q/next-probes (datomic/db conn) q/gig-detail-pattern)
          num-probes (count probes)]
      (when (< num-probes minimum-gigs)
        (create-probes! conn probes))
      (assign-rehearsal-leaders! conn)
      :done)
    (catch Throwable e
      (tap> e)
      (errors/report-error! e))))

(defn make-probe-housekeeping-job [system]
  (fn [{:job/keys [frequency initial-delay]}]
    (jobs/make-repeating-job (partial probe-housekeeping-job system) frequency initial-delay)))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db  (datomic/db conn))) ;; rcf

  (probe-housekeeping-job {:conn conn} nil)
  (assign-rehearsal-leaders! conn)
  ;;
  )
