(ns dev
  (:require
   [app.routes.pedestal-reitit]
   [app.ig]
   [app.routes.pedestal-reitit]
   [app.gigo.core :as gigo]
   [app.jobs.sync-gigs :as sync-gigs]
   [app.jobs.sync-members :as sync-members]
   [app.schemas :as schemas]
   [integrant.repl.state :as state]
   [malli.dev :as md]
   [datomic.client.api :as d]
   [ol.app.dev.dev-extras :as dev-extra]
   [clojure.tools.namespace.repl :as repl]
   [ol.system :as system]
   [browser :as browser]
   [jsonista.core :as j]
   [clojure.string :as str]
   [tick.core :as t]
   [medley.core :as m]
   [app.util :as util]))

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
    (def env (:app.ig/env state/system))
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def gigo (:app.ig/gigo-client state/system))
    (def sno "ag1zfmdpZy1vLW1hdGljciMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDA")) ;; rcf

  (do
    (let [members (j/read-value (slurp "/var/home/ramblurr/src/sno/probematic2/gigo-members.json") j/keyword-keys-object-mapper)
          tx-data (map (fn [{:keys [gigo_key email nick name section occ]}]
                         {:member/gigo-key (str/trim gigo_key)
                          :member/email (str/trim email)
                          :member/nick (str/trim nick)
                          :member/name (str/trim name)
                          :member/section [:section/name (str/trim section)]
                          :member/active? (not occ)}) members)]
      (d/transact conn {:tx-data tx-data}))
    (sync-members/update-member-data! conn (sync-members/fetch-people-data! (:airtable env)))
    (gigo/update-cache! gigo)
    (sync-gigs/update-gigs-db! conn @gigo/gigs-cache)

    (d/transact conn {:tx-data
                      (map (fn [{:keys [id display_name]}]
                             [:db/add [:member/gigo-key id] :member/nick display_name])
                           (gigo/get-band-members! gigo sno))})
    :seed-done) ;; END SEEDS

  (halt)
  (dev-extra/go)

  (require '[datomic.dev-local :as dl])
  (dl/release-db {:system "dev" :db-name "probematic"})
  (md/start! schemas/malli-opts)
  (md/stop!)

  (set-prep! {:profile :dev})
  (keys state/system)
  (-> state/system :app.ig/pedestal)
  (-> state/system :app.ig/env)
  (-> state/system :app.ig/profile)

  (system/config {:profile :dev})

  (system/system-config {:profile :dev})

  ;;
  )
