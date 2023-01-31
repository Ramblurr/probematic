(ns dev
  (:require
   [app.ig]
   [app.schemas :as schemas]
   [browser :as browser]
   [clojure.string :as str]
   [clojure.tools.namespace.repl :as repl]
   [datomic.client.api :as d]
   [integrant.repl.state :as state]
   [jsonista.core :as j]
   [malli.dev :as md]
   [ol.app.dev.dev-extras :as dev-extra]
   [ol.system :as system]
   [app.queries :as q]
   [jsonista.core :as json]
   [com.yetanalytics.squuid :as sq]
   [app.keycloak :as keycloak]
   [datomic.client.api :as datomic]))

;; (repl/disable-reload! (find-ns 'browser))
;; (repl/disable-reload! *ns*)

(set! *print-namespace-maps* false)

;(mr/set-default-registry! schemas/registry)

(defn go-with-browser []
  (dev-extra/go)
  (browser/open-browser "http://localhost:4180/")
  :done)

(defn reset []
  (dev-extra/reset)
  (browser/refresh))

(comment

  (repl/clear)

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
    (def kc (-> state/system :app.ig/keycloak))
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
    (require '[app.gigo.core :as gigo])
    (require '[app.jobs.sync-gigs :as sync-gigs])
    (require '[app.jobs.sync-members :as sync-members])
    (require '[app.keycloak :as keycloak])
    (def gigoc (:app.ig/gigo-client state/system))
    (def sno "ag1zfmdpZy1vLW1hdGljciMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDA")
    (let [members (j/read-value (slurp "/var/home/ramblurr/src/sno/probematic/gigo-members.json") j/keyword-keys-object-mapper)
          tx-data (map (fn [{:keys [gigo_key email nick name section occ]}]
                         {:member/gigo-key (str/trim gigo_key)
                          :member/member-id (sq/generate-squuid)
                          :member/email (str/trim email)
                          :member/nick (str/trim nick)
                          :member/name (str/trim name)
                          :member/section [:section/name (str/trim section)]
                          :member/active? (not occ)}) members)]
      (d/transact conn {:tx-data tx-data}))
    (sync-members/update-member-data! conn (sync-members/fetch-people-data! (:airtable env)))
    (gigo/update-cache! gigoc)
    (sync-gigs/update-gigs-db! conn @gigo/gigs-cache)
    (sync-gigs/update-gigs-attendance-db! conn @gigo/gigs-cache)
    (filter  #(= ""  (get-in % [:the_plan :feedback_value]))
             (:attendance
              (first @gigo/gigs-cache)))
    (d/transact conn {:tx-data
                      (map (fn [{:keys [id display_name]}]
                             [:db/add [:member/gigo-key id] :member/nick display_name])
                           (gigo/get-band-members! gigoc sno))})

    (keycloak/match-members-to-keycloak! {:db (datomic/db conn) :kc kc :datomic-conn conn})
    :seed-done) ;; END SEEDS
  1

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

  (d/transact conn {:tx-data [{:db/ident :song/arrangement-notes
                               :db/doc "Notes for the arrangement"
                               :db/valueType :db.type/string
                               :db/cardinality :db.cardinality/one}]})

  (def datomic (-> state/system :app.ig/datomic-db))
  (def conn (:conn datomic))
  (def app (-> state/system :app.ig.router/routes))

  (do
    (require '[integrant.repl.state :as state])
    (require '[keycloak.admin :as admin])
    (require '[datomic.client.api :as datomic])
    (def env (-> state/system :app.ig/env))
    (def kc (-> state/system :app.ig/keycloak))
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn)))
  ;; rcf
  (q/find-all-gigs db)

  (def user0 (second))

  ;;
  )
