(ns app.jobs
  (:require
   [app.jobs.airtable :refer [make-airtable-job]]
   [app.jobs.sync-members :refer [make-sync-members-job]]
   [app.jobs.sync-gigs :refer [make-gigs-sync-job]]
   [app.jobs.probe-housekeeping :refer [make-probe-housekeeping-job]]
   [app.jobs.ping :refer [make-job-ping]]))

;; Add your job constructors here
(defn job-defs [opts]
  {:job/ping               make-job-ping
   :job/probe-housekeeping (make-probe-housekeeping-job opts)
   :job/sync-gigs          (make-gigs-sync-job opts)
   :job/update-airtable    (make-airtable-job opts)
   :job/sync-members       (make-sync-members-job opts)})
