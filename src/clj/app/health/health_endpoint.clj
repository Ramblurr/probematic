(ns app.health.health-endpoint
  (:require
   [ring.util.http-response :as http-response])
  (:import
   [java.util Date]))

(defn healthcheck!
  [req]
  (tap> (dissoc req :reitit.core/match))
  (http-response/ok
   {:time     (str (Date. (System/currentTimeMillis)))
    :up-since (str (Date. (.getStartTime (java.lang.management.ManagementFactory/getRuntimeMXBean))))
    :app      {:status  "up"
               :message ""}}))
