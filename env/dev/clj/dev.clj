(ns dev
  (:require
   [app.routes.pedestal-reitit]
   [app.ig]
   [app.routes.pedestal-reitit]
   [app.schemas :as schemas]
   [integrant.repl.state :as state]
   [malli.dev :as md]
   [migratus.core :as migratus]
   [ol.app.dev.dev-extras :refer :all]
   [ol.system :as system]))

(defn create-migration [config name]
  (migratus/create config name))

;(mr/set-default-registry! schemas/registry)
;

(comment
  (go)
  (halt)
  (reset)

  (md/start! schemas/malli-opts)
  (md/stop!)

  ;rich-comment-setup
  (do
    (def ds (:ol.hikari-cp.ig/hikari-connection state/system))
    (def migratus (:ol.sql.migratus.ig/migratus state/system)))

  (set-prep! {:profile :dev})
  (keys state/system)
  (-> state/system :app.ig/pedestal)
  (-> state/system :app.ig/env)
  (-> state/system :app.ig/profile)

  (system/config {:profile :dev})

  (system/system-config {:profile :dev})

  (create-migration migratus "audit log")

  (migratus/init migratus)
  (migratus/migrate migratus)

  (seeds/seed! ds)

  (add-lib 'fmnoise/flow {:mvn/version "4.2.1"}))
