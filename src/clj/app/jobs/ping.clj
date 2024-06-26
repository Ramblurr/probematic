(ns app.jobs.ping
  (:require
   [ol.jobs-util :as jobs]))

(defn- ping [time]
  (println "ping" time))

(defn make-job-ping [{:job/keys [frequency initial-delay]}]
  (jobs/make-repeating-job ping frequency initial-delay))
