(ns app.ig
  "This namespace contains our application's integrant system implementations"
  (:require
   [app.config :as config]
   [app.jobs :as jobs]
   [app.routes :as routes]
   [app.db :as db]
   [app.datomic :as datomic]
   [app.routes.helpers :as route.helpers]
   [clojure.tools.logging :as log]
   [integrant.core :as ig]
   [io.pedestal.http :as server]
   [ol.jobs.ig]
   [ol.system :as system]
   [datomic.client.api :as d]
   [datomic.dev-local :as dl]

   [reitit.http :as http]
   [reitit.pedestal :as pedestal]
   [ctmx.render :as ctmx.render]
   [hiccup2.core :as hiccup2]
   [app.debug :as debug]))

;; Ensure ctmx is using the XSS safe hiccup render function
(alter-var-root #'ctmx.render/html (constantly
                                    #(-> % ctmx.render/walk-attrs hiccup2/html str)))

(defmethod ig/init-key ::profile [_ profile]
  profile)

(defmethod ig/init-key ::env [_ profile]
  (system/config profile))

(defmethod ig/init-key ::handler [_ system]
  (routes/default-handler system))

(defmethod ig/init-key :app.ig.router/routes
  [_ system]
  (routes/routes system))

(defmethod ig/init-key :app.ig.jobs/definitions
  [_ system]
  (jobs/job-defs system))

(defmethod ig/init-key ::pedestal
  [_ {:keys [service routes handler env] :as system}]
  (let [port (-> service :io.pedestal.http/port)
        host (-> service :io.pedestal.http/host)
        start-msg (format "Starting %s on %s:%d" (str (:name env "app") (when (config/dev-mode? env) " [DEV]")) host port)]
    ;; (tap> routes)
    (tap> start-msg)
    (log/info start-msg)
    (cond-> service
      true (assoc :io.pedestal.http/allowed-origins
                  {:creds true :allowed-origins (constantly true)})
      true server/default-interceptors
                 ;; swap in the reitit router
      true (pedestal/replace-last-interceptor
            (pedestal/routing-interceptor
             (http/router routes)
             handler))
      (config/dev-mode? env) route.helpers/prone-exception-interceptor
      (config/dev-mode? env) (server/dev-interceptors)
      true (server/create-server)
      true (server/start))))

(defmethod ig/halt-key! ::pedestal
  [_ server]
  (server/stop server))

(defmethod ig/init-key ::gigo-client
  [_ {:keys [env] :as opts}]
  {:username (get-in env [:gigo :username])
   :password (get-in env [:gigo :password])
   :cookie-atom (atom nil)})

(defmethod ig/init-key ::datomic-db
  [_ config]
  (println "\nStarted Datomic DB")
  (let [db-name (select-keys config [:db-name])
        client (d/client (select-keys config [:server-type :system :storage-dir]))
        _ (d/create-database client db-name)
        conn (d/connect client db-name)]
    (datomic/load-dataset conn)
    (assoc config :conn conn)))

(defmethod ig/halt-key! ::datomic-db
  [_ config]
  (println "\nStopping Datomic DB")
  (dl/release-db (select-keys config [:system :db-name])))
