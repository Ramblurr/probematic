(ns app.auth
  (:require
   [buddy.core.keys :as buddy-keys]
   [buddy.sign.jwt :as jwt]
   [clojure.java.io :as io]
   [clojure.set :as set]))

(defn throw-unauthorized
  ([msg data]
   (throw (ex-info msg
                   (merge {:app/error-type :app.error.type/authentication-failure} data))))
  ([msg cause data]
   (throw
    (ex-info msg
             (merge {:app/error-type :app.error.type/authentication-failure} data)
             cause))))

(def roles-authorization-interceptor
  "Reitit route interceptor that mounts itself if route has `:app.auth/roles` data. Expects `:app.auth/roles`
  to be a set of keyword and the context to have `[:session :app.auth/identity :app.auth/roles]` with user roles.
  responds with HTTP 403 if user doesn't have the roles defined, otherwise no-op."
  {:name ::auth
   :compile (fn [{::keys [roles]} _]
              (when (seq roles)
                {:description (str "requires roles " roles)
                 :spec {::roles #{keyword?}}
                 :context-spec {:user {::roles #{keyword}}}
                 :enter (fn [{:keys [request] :as ctx}]
                          (if (not (set/subset? roles
                                                (get-in request [:app.auth/session :session/roles])))
                            (throw-unauthorized "Current users lacks required roles" {:permitted-roles roles})
                            ctx))}))})

(defn get-session
  "Fetch the user's session info from the request map"
  [req]
  (:app.auth/session req))

(defn get-current-member
  "Fetch the user's member record from the request map"
  [req]
  (-> req :app.auth/session :session/member))

(def require-authenticated-user
  "Throws an unauthorized exception if the request map does not contain session information for the current user"
  {:name ::require-authenticated-user
   :enter (fn [ctx]
            (if (nil? (get-session (:request ctx)))
              (throw-unauthorized "Authentication Required" {})
              ctx))})

(defn has-roles?
  "Given a role set and a request, returns true if the current user has all the roles."
  [roles req]
  (set/subset? roles
               (get-in req [:app.auth/session :session/roles])))

(def demo-auth-interceptor
  {:name ::demo-auth-interceptor
   :enter #(-> % (assoc-in [:request :app.auth/session] {:session/username "admin"
                                                         :session/email "admin@example.com"
                                                         :session/groups #{"/Mitglieder" "/admin"}
                                                         :session/roles #{:Mitglieder :admin}}))})

(defn auth-interceptor
  "This function returns an interceptor that will extract and verify the JWT in the
  x-forwarded-access-token header. It will be verified with the certificate contained in the certificate-filename.

  This function will add a :app.auth/session key to the request map containing:
    :session/username - the preferred username of the authenticated user
    :session/email - the email of the user
    :session/groups - a set of groups in the claims (if included)
    :session/roles - a set of roles that the user has, filtered to only include those in known-roles

  Additionally the entire parsed JWT will be stored in the context under :app.auth/claims.

  If the JWT is invalid then an exception will be thrown with the
  key value :app/error-type :app.error.type/authentication-failure.

  If the JWT is not present, then the interceptor does nothing.
  "
  [certificate-filename known-roles]
  (let [certificate (buddy-keys/str->public-key (-> (io/resource certificate-filename) slurp))]
    {:name ::auth-interceptor
     :enter (fn [ctx]
              (let [headers (-> ctx :request :headers)
                    user-email (get headers "x-forwarded-email")
                    token (get headers "x-forwarded-access-token")]
                (if token
                  (try
                    (let [claims (jwt/unsign token certificate {:alg :rs256})
                          session {:session/username (:preferred_username claims)
                                   :session/email (:email claims)
                                   :session/groups (set (:groups claims))
                                   :session/roles (set (->> (get-in claims [:realm_access :roles])
                                                            (map keyword)
                                                            (filter #(contains? known-roles %))))}]
                      ;; (tap> {:headers headers :user-email user-email :token token :claims claims :session session})
                      (-> ctx
                          (assoc-in [:request :app.auth/session] session)
                          (assoc :app.auth/claims claims)))
                    (catch Exception e
                      (throw-unauthorized "Authentication Token Validation Failed" e
                                          {:user-email user-email
                                           :token token
                                           :buddy-cause (-> (ex-data e) :cause)})))
                  ctx)))}))
