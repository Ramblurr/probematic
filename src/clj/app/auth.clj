(ns app.auth
  (:require
   [ring.middleware.session.cookie :as cookie]
   [ring.middleware.session.store :as session.store]
   [io.pedestal.interceptor :as interceptor]
   [io.pedestal.interceptor.chain :as interceptor-chain]
   [io.pedestal.http.ring-middlewares :as ring-middlewares]
   [app.config :as config]
   [clojure.set :as set]
   [medley.core :as m])
  (:import
   [java.security SecureRandom]))

(def default-session-ttl-ms (* 1000 86400 14)) ;; 14 days

(defn session-cookie-attrs [max-age dev-mode?]
  {:path      "/"
   :http-only true
   :secure    (not dev-mode?)
   :max-age   (or max-age default-session-ttl-ms)
   :same-site :lax})

(defn- random-bytes [size]
  (let [seed (byte-array size)]
    (.nextBytes (SecureRandom.) seed)
    seed))

(defn cookie-store [secret]
  (cookie/cookie-store {:key (or secret (random-bytes 16))}))

(def roles-authorization-interceptor
  "Reitit route interceptor that mounts itself if route has `:app.auth/roles` data. Expects `:app.auth/roles`
  to be a set of keyword and the context to have `[:session :app.auth/identity :app.auth/roles]` with user roles.
  responds with HTTP 403 if user doesn't have the roles defined, otherwise no-op."
  {:name ::auth
   :compile (fn [{::keys [roles]} _]
              (if (seq roles)
                {:description (str "requires roles " roles)
                 :spec {::roles #{keyword?}}
                 :context-spec {:user {::roles #{keyword}}}
                 :enter (fn [{:keys [request] :as ctx}]
                          (if (not (set/subset? roles
                                                (get-in request [:session ::identity ::roles])))
                            (do
                              (-> ctx
                                  (assoc :response {:status 403, :body "Please login."})
                                  interceptor-chain/terminate))
                            ctx))}))})

(defn user [req]
  (get-in req [:session :user]))

(def require-user
  {:name ::require-user
   :enter (fn [{req :request :as ctx}]
            (if (nil? (user req))
              (assoc ctx :response {:status 401
                                    :body "Authentication required"})
              ctx))})

(defn session-interceptor
  [{:keys [env]}]
  (ring-middlewares/session {:store (cookie-store (config/cookie-secret env))
                             :cookie-attrs (session-cookie-attrs (config/cookie-default-max-age env) (config/dev-mode? env))}))

(defn user-info-interceptor
  []
  {:name ::user-info
   :enter (fn [context]
            (let [ident (get-in context [:request :session ::identity])]
              (assoc-in context
                        [:request :lacinia-app-context ::identity]
                        ident)))})

(def require-user
  {:name ::require-user
   :enter (fn [{req :request :as ctx}]
            (if (nil? (user req))
              (assoc ctx :response {:status 401
                                    :body "Authentication required"})
              ctx))})

(defn has-roles [roles identity]
  (set/subset? roles
               (:app.auth/roles identity)))
