(ns app.ig
  "This namespace contains our application's integrant system implementations"
  (:require
   [app.auth :as auth]
   [app.config :as config]
   [nrepl.server :as nrepl]
   [app.datomic :as datomic]
   [app.email.email-worker :as email-worker]
   [app.i18n :as i18n]
   [app.interceptors :as interceptors]
   [app.jobs :as jobs]
   [app.keycloak :as keycloak]
   [app.routes :as routes]
   [app.sardine :as sardine]
   [clojure.tools.logging :as log]
   [ctmx.render :as ctmx.render]
   [datomic.client.api :as d]
   [datomic.dev-local :as dl]
   [hiccup2.core :as hiccup2]
   [integrant.core :as ig]
   [io.pedestal.http :as server]
   [ol.system :as system]
   [ol.jobs.ig]
   [reitit.http :as http]
   [sentry-clj.core :as sentry]
   [taoensso.carmine :as car]))

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
  (let [routes (routes/routes system)]
    {:routes  routes
     :router  (http/router routes)}))

(defmethod ig/init-key :app.ig.jobs/definitions
  [_ {:keys [env] :as system}]
  (if (config/demo-mode? env)
    {}
    (jobs/job-defs system)))

(defn csp-settings [env]
  (let [base-uri (config/app-base-url env)
        id-uri (config/keycloak-auth-server-url env)
        forum-uri (config/discourse-forum-url env)]
    (assert base-uri)
    (assert id-uri)
    (assert forum-uri)
    {:content-security-policy-settings {:img-src     "https://*.streetnoise.at 'self' data:"
                                        :object-src  "'none'"
                                        :default-src (format  "%s %s 'self'" base-uri id-uri)
                                        :font-src    (format  "%s 'self'" base-uri)
                                        :script-src  (format  "%s %s 'self' 'unsafe-inline' 'unsafe-eval'" base-uri forum-uri)
                                        :style-src   (format "%s 'self' 'unsafe-inline'" base-uri)
                                        :connect-src "'self'"
                                        :frame-src (format  "%s 'self'" forum-uri)}}))

(defn with-csp [service-map env]
  (assoc service-map :io.pedestal.http/secure-headers (csp-settings env)))
(defn with-cors [service-map env]
  (assoc service-map :io.pedestal.http/allowed-origins
         {:creds true
          :allowed-origins
          ["" (config/app-base-url env) (config/keycloak-auth-server-url env)]
            ;; (constantly true)
          }))
(defn with-container-opts [service-map env]
    ;; interceptors/prone-exception-interceptor
  (assoc service-map :io.pedestal.http/container-options {:io.pedestal.http.jetty/http-configuration (interceptors/http-configuration
                                                                                                      (-> env :max-header-size))}))
(defn maybe-with-dev-interceptors [service-map env]
  (if (config/dev-mode? env)
    (server/dev-interceptors service-map)
    service-map))
(defmethod ig/init-key ::pedestal
  [_ {:keys [service-map routes handler env] :as system}]
  (let [port      (-> service-map :io.pedestal.http/port)
        host      (-> service-map :io.pedestal.http/host)
        start-msg (format "Starting %s on %s:%d" (str (:name env "app") (when (config/dev-mode? env) " [DEV]")) host port)]

    (assert port)
    (assert host)
    ;; (tap> routes)
    (tap> start-msg)
    (log/info start-msg)
    (-> service-map
        (with-container-opts env)
        (with-cors env)
        (with-csp env)
        (server/default-interceptors)
        (interceptors/with-our-pedestal-interceptors system (:router routes) handler)
        (maybe-with-dev-interceptors env)
        (server/create-server)
        (server/start))))

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

(defmethod ig/init-key ::nrepl-server
  [_ {:keys [port bind ack-port] :as config}]
  (try
    (let [server (nrepl/start-server :port port
                                     :bind bind
                                     :ack-port ack-port)]
      (log/info "nREPL server started on port:" port)
      (assoc config ::server server))
    (catch Exception e
      (log/error "failed to start the nREPL server on port:" port)
      (throw e))))

(defmethod ig/halt-key! ::nrepl-server
  [_ {::keys [server]}]
  (nrepl/stop-server server)
  (log/info "nREPL server stopped"))
