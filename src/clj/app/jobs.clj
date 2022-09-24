(ns app.jobs
  (:require
   [app.jobs.ping :refer [make-job-ping]]))

;; Add your job constructors here
(defn job-defs [system]
  {:job/ping make-job-ping})
