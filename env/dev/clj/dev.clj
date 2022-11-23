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
   [app.util :as util]
   [app.insurance.controller :as controller]))

(repl/disable-reload! (find-ns 'browser))
(repl/disable-reload! *ns*)

(set! *print-namespace-maps* false)

;(mr/set-default-registry! schemas/registry)
(def datomic (-> state/system :app.ig/datomic-db))
(def conn (:conn datomic))
(def app (-> state/system :app.ig.router/routes))

(defn go-with-browser []
  (dev-extra/go)
  (browser/open-browser "http://localhost:4180/"))

(defn reset []
  (dev-extra/reset)
  (browser/refresh))

(comment

  ;; Run go to start the system
  (dev-extra/go)
  ;; Run halt to shutdown the system
  (dev-extra/halt)
  ;; Run reset to reload all code and restart the system
  ;; if the browser is connected it will refresh the page
  (refresh)

  ;; If you have chromium  and chromium driver installed
  ;; you can get code reloading by using this version of go
  ;; see readme for more info
  (go-with-browser)

;;;; Setup Integrant Repl State
;;; Run this before running either of the seeds below
  (do
    (require '[integrant.repl.state :as state])
    (def env (:app.ig/env state/system))
    (def conn (-> state/system :app.ig/datomic-db :conn))) ;; rcf

;;;; DEMO SEEDS
;;;  Run this to seed the instance with demo data
  (do
    (require '[app.demo :as demo])
    (demo/seed-random-members! conn))

;;; REAL  SEEDS
;;; only run this if you have access to the external systems
;;; DO NOT run this in demo mode

  (do
    (def gigoc (:app.ig/gigo-client state/system))
    (def sno "ag1zfmdpZy1vLW1hdGljciMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDA")
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
    (gigo/update-cache! gigoc)
    (sync-gigs/update-gigs-db! conn @gigo/gigs-cache)
    (d/transact conn {:tx-data
                      (map (fn [{:keys [id display_name]}]
                             [:db/add [:member/gigo-key id] :member/nick display_name])
                           (gigo/get-band-members! gigoc sno))})
    :seed-done) ;; END SEEDS

;;;; Scratch pad
  ;;  everything below is notes/scratch

  (require '[clojure.tools.namespace.repl :refer [refresh]])
  (refresh)

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
