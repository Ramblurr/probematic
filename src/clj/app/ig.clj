(ns app.ig
  "This namespace contains our application's integrant system implementations"
  (:require
   [app.keycloak :as keycloak]
   [taoensso.carmine :as car]
   [app.email.email-worker :as email-worker]
   [app.config :as config]
   [app.datomic :as datomic]
   [app.i18n :as i18n]
   [app.interceptors :as interceptors]
   [app.jobs :as jobs]
   [app.routes :as routes]
   [clojure.tools.logging :as log]
   [ctmx.render :as ctmx.render]
   [datomic.client.api :as d]
   [datomic.dev-local :as dl]
   [hiccup2.core :as hiccup2]
   [integrant.core :as ig]
   [io.pedestal.http :as server]
   [ol.jobs.ig]
   [ol.system :as system]
   [reitit.http :as http]
   [reitit.pedestal :as pedestal]
   [sentry-clj.core :as sentry]
   [app.sardine :as sardine]
   [app.auth :as auth]))

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
  [_ {:keys [env] :as system}]
  (if (config/demo-mode? env)
    {}
    (jobs/job-defs system)))

(defmethod ig/init-key ::pedestal
  [_ {:keys [service routes handler env] :as system}]
  (let [port (-> service :io.pedestal.http/port)
        host (-> service :io.pedestal.http/host)
        start-msg (format "Starting %s on %s:%d" (str (:name env "app") (when (config/dev-mode? env) " [DEV]")) host port)]
    ;; (tap> routes)
    (tap> start-msg)
    (log/info start-msg)
    (cond-> service
      true (assoc :io.pedestal.http/container-options {:io.pedestal.http.jetty/http-configuration (interceptors/http-configuration
                                                                                                   (-> env :max-header-size))})
      true (assoc :io.pedestal.http/allowed-origins
                  {:creds true :allowed-origins (constantly true)})
      true (interceptors/with-default-interceptors system)
                 ;; swap in the reitit router
      true (pedestal/replace-last-interceptor
            (pedestal/routing-interceptor
             (http/router routes)
             handler))
      ;; (config/dev-mode? env) interceptors/prone-exception-interceptor
      (config/dev-mode? env) (server/dev-interceptors)
      true (server/create-server)
      true (server/start))))

(defmethod ig/halt-key! ::pedestal
  [_ server]
  (server/stop server))

(defmethod ig/init-key ::gigo-client
  [_ {:keys [env]}]
  (when-not (config/demo-mode? env)
    {:username (get-in env [:gigo :username])
     :password (get-in env [:gigo :password])
     :cookie-atom (atom nil)}))

(defmethod ig/init-key ::datomic-db
  [_ config]
  (log/info "Started Datomic DB")
  (let [db-name (select-keys config [:db-name])
        client (d/client (select-keys config [:server-type :system :storage-dir]))
        _ (d/create-database client db-name)
        conn (d/connect client db-name)]
    (datomic/load-dataset (:env config) conn)
    (assoc config :conn conn)))

(defmethod ig/halt-key! ::datomic-db
  [_ config]
  (log/info "Stopping Datomic DB")
  (dl/release-db (select-keys config [:system :db-name])))

(defmethod ig/init-key ::i18n-langs
  [_ _]
  (i18n/read-langs))

(defmethod ig/init-key ::sentry
  [_ {:keys [env]}]
  (when-not (config/demo-mode? env)
    (sentry/init! (-> env :sentry :dsn)
                  {:environment (-> env :profile)})))

(defmethod ig/init-key ::webdav-sardine
  [_ {:keys [env]}]
  (sardine/build-config (:nextcloud env)))

(defmethod ig/halt-key! ::webdav-sardine
  [_ {:keys [client]}]
  (sardine/shutdown client))

(defmethod ig/init-key ::mailgun
  [_ {:keys [env]}]
  (:mailgun env))

(defmethod ig/init-key ::redis
  [_ {:keys [env]}]
  {:pool (car/connection-pool {})
   :spec (-> env :redis :conn-spec)})

(defmethod ig/init-key ::email-worker
  [_ sys]
  (email-worker/start! sys))

(defmethod ig/halt-key! ::email-worker
  [_ sys]
  (email-worker/stop! sys))

(defmethod ig/init-key ::oauth2
  [_ sys]
  (auth/build-oauth2-config (:env sys)))

(defmethod ig/init-key ::keycloak [_ sys]
  (keycloak/init! sys))

(defmethod ig/halt-key! ::keycloak
  [_ sys]
  (keycloak/halt! sys))
