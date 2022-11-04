(ns dev
  (:require
   [app.routes.pedestal-reitit]
   [app.ig]
   [app.routes.pedestal-reitit]
   [app.gigo :as gigo]
   [app.jobs.sync-gigs :as sync-gigs]
   [app.jobs.sync-members :as sync-members]
   [app.schemas :as schemas]
   [integrant.repl.state :as state]
   [malli.dev :as md]
   [datomic.client.api :as d]
   [ol.app.dev.dev-extras :as dev-extra]
   [clojure.tools.namespace.repl :as repl]
   [ol.system :as system]
   [browser :as browser]))

(repl/disable-reload! (find-ns 'browser))

(set! *print-namespace-maps* false)

;(mr/set-default-registry! schemas/registry)
(def datomic (-> state/system :app.ig/datomic-db))
(def conn (:conn datomic))
(def app (-> state/system :app.ig.router/routes))

(defn go []
  (dev-extra/go)
  (browser/open-browser "http://localhost:6161/"))

(defn reset []
  (dev-extra/reset)
  (browser/refresh))

(defn halt []
  (dev-extra/halt))

(comment
  (go)
  (eta/chrome {:path-driver "/usr/bin/chromedriver"
               :path-browser "/usr/bin/chromium-freeworld"})

  @browser

  (dev-extra/go)
  (dev-extra/halt)
  (dev-extra/reset)

;;;  SEEDS

  (do
    (require '[integrant.repl.state :as state])
    (def _opts {:env        (:app.ig/env state/system)
                :gigo       (:app.ig/gigo-client state/system)
                :conn       (-> state/system :app.ig/datomic-db :conn)}))

  (do
    (gigo/update-cache! (:gigo _opts))
    (sync-gigs/update-gigs-db! (:conn _opts) @gigo/gigs-cache)
    (sync-members/update-member-data! (:conn _opts) (sync-members/fetch-people-data! (-> _opts :env :airtable))))

  ;; END SEEDS

  (md/start! schemas/malli-opts)
  (md/stop!)

  (set-prep! {:profile :dev})
  (keys state/system)
  (-> state/system :app.ig/pedestal)
  (-> state/system :app.ig/env)
  (-> state/system :app.ig/profile)

  (system/config {:profile :dev})

  (system/system-config {:profile :dev})

  (d/q '[:find ?e ?v
         :where
         [?e :member/name ?v]]
       (d/db (:conn datomic)))

  (d/q '[:find ?e ?v
         :where
         [?e :gig/title ?v]]
       (d/db (:conn datomic)))
  (def pattern [:gig/title :gig/id :gig/date])

  (d/q '[:find (pull ?e pattern)
         :in $ pattern
         :where
         [?e :gig/title ?v]]
       (d/db (:conn datomic)) pattern)

  (d/transact conn {:tx-data [{:db/ident :gig/gig-id
                               :db/doc "The gig id from gig-o-matic."
                               :db/valueType :db.type/string
                               :db/unique :db.unique/identity
                               :db/cardinality :db.cardinality/one}]})

  ;;
  )
