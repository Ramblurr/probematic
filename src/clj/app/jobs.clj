(ns app.jobs
  (:require
   [app.jobs.airtable :refer [make-airtable-job]]
   [app.jobs.sync-members :refer [make-sync-members-job]]
   [app.jobs.ping :refer [make-job-ping]]))

;; Add your job constructors here
(defn job-defs [opts]
  {:job/ping make-job-ping
   :job/update-airtable (make-airtable-job opts)
   :job/sync-members    (make-sync-members-job opts)})
