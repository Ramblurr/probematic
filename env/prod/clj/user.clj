(ns user
  (:require
   [playback.preload]
   [app.main :as main]
   [portal.api :as inspect]))

(defn debug-in-prod []
  (inspect/open {:theme :portal.colors/gruvbox
                 :portal.launcher/host "0.0.0.0"
                 :portal.launcher/port  7001})
  (add-tap portal.api/submit))

(comment
  (debug-in-prod)
  (keys main/system)

  (require '[com.brunobonacci.mulog :as μ])
  (μ/log ::hello )

  (require '[app.dashboard.routes :as dashboard.routes])
  (dashboard.routes/routes)

  (tap> main/system)

  (require '[app.queries :as q])
  (require '[datomic.client.api :as datomic])
  (require '[app.jobs.reminders :as reminders])

  (let [db (datomic/db (-> main/system :app.ig/datomic-db :conn))
        sys
        {:env (:app.ig/env main/system)
         :gigo (:app.ig/gigo-client main/system)
         :datomic (:app.ig/datomic-db main/system)
         :i18n-langs (:app.ig/i18n-langs main/system)
         :redis (:app.ig/redis main/system)}
        ]
    ;; (tap> (q/active-reminders-by-type db))
    (reminders/send-reminders! sys nil)
    ;;
    )

  (datomic/transact (-> main/system :app.ig/datomic-db :conn)
                    {:tx-data [[:db/add [:reminder/reminder-id #uuid "01881ecb-7613-873d-8b27-5ab7e9aed06b"]
                                :reminder/remind-at #inst "2023-05-13T09:43:50.547Z"
                                ]]}
                    )





  ;;
  )
