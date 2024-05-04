(ns app.jobs.sync-songs
  (:require
   [app.cms :as cms]
   [datomic.client.api :as datomic]
   [ol.jobs-util :as jobs]))

(defn song-sync-job [system _]
  (cms/sync-all-songs! system))

(defn make-songs-sync-job [system]
  (fn [{:job/keys [frequency initial-delay]}]
    (jobs/make-repeating-job
     (partial #'song-sync-job system) frequency initial-delay)))
