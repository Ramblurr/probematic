(ns app.config
  (:require

   [app.debug :as debug]
   [clojure.string :as str]))

(defn profile [env]
  (-> env :ig/system :app.ig/profile))

(defn dev-mode? [env]
  (= :dev (profile env)))

(defn demo-mode? [env]
  (= :demo (profile env)))

(defn prod-mode? [env]
  (= :prod (profile env)))

(defn test-mode? [env]
  (= :test (profile env)))

(defn non-prod? [env]
  (some?  (#{"qa" "dev" "test"} (-> env :environment))))

(defn app-secret-key [env]
  (assert env)
  (:app-secret-key env))

(defn app-base-url [env]
  (assert env)
  (:app-base-url env))

(defn session-config [env]
  (let [{:keys [session-ttl-s] :as sc}  (:session-config env)]
    (-> sc
        (assoc-in [:cookie-attrs :max-age] (* 1000 session-ttl-s))
        (assoc-in [:cookie-attrs :secure] (not (dev-mode? env))))))

(defn oauth2-certificate-filename [env]
  (-> env :authorization :cert-filename))

(defn oauth2-known-roles [env]
  (-> env :authorization :known-roles))

(defn keycloak-auth-server-url [env]
  (-> env :keycloak :auth-server-url))

(defn discourse-forum-url [env]
  (-> env :discourse :forum-url))
