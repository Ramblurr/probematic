(ns app.config)

(defn profile [env]
  (-> env :ig/system :app.ig/profile))

(defn dev-mode? [env]
  (= :dev (profile env)))

(defn prod-mode? [env]
  (= :prod (profile env)))

(defn test-mode? [env]
  (= :test (profile env)))

(defn cookie-default-max-age [env]
  (-> env :cookie-session-config :cookie-default-max-age))

(defn cookie-secret [env]
  (-> env :cookie-session-config :cookie-secret))

(defn non-prod? [env]
  (some?  (#{"qa" "dev" "test"} (-> env :environment))))
