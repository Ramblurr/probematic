(ns app.main
  (:gen-class)
  (:require
   ol.system
   [app.ig]
   [signal.handler :as signal]
   [clojure.tools.logging :as log]
   [integrant.core :as ig]))

(def system nil)

(defn profile []
  (if-let [p (System/getenv "APP_PROFILE")]
    (keyword p)
    :prod))

(defn stop-system! []
  (alter-var-root #'system ig/halt!))

(defn -main [& args]
  (let [profile (profile)
        _ (log/info (format "Starting probematic with profile %s" profile))
        system-config (ol.system/system-config {:profile profile})
        sys (ig/init system-config)]
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread.
      (fn []
        (stop-system!))))
    (alter-var-root #'system (constantly sys)))
  @(promise))

(signal/with-handler :term
  (log/info "caught SIGTERM, quitting")
  (stop-system!)
  (log/info "all components shut down"))
