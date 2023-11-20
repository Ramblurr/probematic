(ns app.jobs.sync-songs
  (:require
   [app.cms :as cms]
   [datomic.client.api :as datomic]
   [ol.jobs-util :as jobs]))

(defn make-songs-sync-job [system]
  (fn [{:job/keys [frequency initial-delay]}]
    (jobs/make-repeating-job (partial #'cms/sync-all-songs! system) frequency initial-delay)))


(comment
  (do
    (require '[integrant.repl.state :as state])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db  (datomic/db conn))
    (def system {:datomic {:conn conn}
                 :redis (-> state/system :app.ig/redis)
                 :i18n-langs (-> state/system :app.ig/i18n-langs)
                 :env (-> state/system :app.ig/env)})) ;; rcf

  (cms/sync-all-songs! system)

  ;;
  )
