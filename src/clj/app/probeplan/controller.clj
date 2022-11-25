(ns app.probeplan.controller
  (:require
   [app.datomic :as d]
   [app.queries :as q]
   [datomic.client.api :as datomic]
   [medley.core :as m]
   [tick.core :as t]
   [tick.alpha.interval :as t.i]
   [app.debug :as debug]
   [app.util :as util]
   [app.probeplan.domain :as domain]))

(defn generate-probeplan! [db]
  (let [play-stats (q/load-play-stats db)]
    (domain/generate-probeplan play-stats)))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (def env (:app.ig/env state/system))
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db  (datomic/db conn))) ;; rcf
  (generate-probeplan! db)

;;
  )
