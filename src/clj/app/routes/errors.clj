(ns app.routes.errors
  (:require
   [app.ui :as ui]
   [app.util :as util]
   [clojure.set :as set]
   [sentry-clj.core :as sentry]
   [clojure.tools.logging :as log]
   [app.config :as config]))

(def dangerous-keys
  #{:datomic-conn
    :conn
    :db
    :db-before
    :db-after
    :system
    :env
    :claims
    :cookies
    :cookie-secret
    :session-config
    :app-secret-key
    :client-id
    :client-secret
    :oauth2
    :secrets
    :session
    :access_token
    :id_token
    :refresh_token
    :reitit.core/match
    :muuntaja/response
    :reitit.core/router})

(def redact-keys #{:password :pass "x-forwarded-access-token" "cookie"})

(defn sanitize [v]
  (->> v
       (util/remove-deep dangerous-keys)
       (util/replace-deep redact-keys "<REDACTED>")))

(defn unwrap-ex [ex]
  (sanitize
   (if-let [data (ex-data ex)]
     (if-let [nested-ex (:exception data)]
       nested-ex
       ex)
     ex)))

(defn format-req
  "Given a pedestal request map, returns a smaller map designed for sentry consumption"
  [req]
  (-> req
      (update :params (fn [params]
                        (-> {:params params}
                            (assoc :htmx? (:htmx? req))
                            (assoc :current-locale (:current-locale req))
                            (assoc :will-change-lang (:will-change-lang req))
                            (assoc :form-params (:form-params req))
                            (assoc :path-params (:path-params req)))))
      (update :request-method name)
      (select-keys   [:url :query-string :request-method :headers :params])
      (set/rename-keys  {:request-method :method :params :data})))

(defn send-sentry!
  "Sends a sentry event asynchronously"
  ([req ex]
   (send-sentry! (or (ex-message ex) "no message") req ex))
  ([msg req ex]
   (let [event-data {:message msg
                     :extra {:human-id (:human-id req)}
                     :request (-> req sanitize format-req)
                     :throwable (unwrap-ex ex)}]
     (tap> event-data)
     (sentry/send-event event-data))))

(defn log-error! [req ex]
  (when (config/prod-mode? (-> req :system :env))
    (log/error ex)))

(defn report-error!
  "Report an exception outside the normal request/response lifecycle"
  ([ex]
   (report-error! ex nil))
  ([ex extra]
   (let [event-data {:message (ex-message ex)
                     :extra extra
                     :throwable (unwrap-ex ex)}]
     (sentry/send-event event-data)
     (log/error ex))))

(defn unauthorized-error [req ex]
  (log-error! req ex)
  (send-sentry! req ex)
  (if (:htmx? req)
    (ui/error-page-response-fragment ex req 401)
    (ui/error-page-response ex req 401)))

(defn validation-error [req ex]
  (log-error! req ex)
  (send-sentry! req ex)
  (if (:htmx? req)
    (ui/error-page-response-fragment ex req 400)
    (ui/error-page-response ex req 400)))

(defn not-found-error [req ex]
  (log-error! req ex)
  (send-sentry! req ex)
  (if (:htmx? req)
    (ui/error-page-response-fragment ex req 404)
    (ui/error-page-response ex req 404)))

(defn unknown-error [req ex]
  (log-error! req ex)
  (send-sentry! req ex)
  (if (:htmx? req)
    (ui/error-page-response-fragment ex req 500)
    (ui/error-page-response ex req 500)))
