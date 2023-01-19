(ns app.main
  (:gen-class)
  (:require
   ol.system
   [app.ig]
   [clojure.tools.logging :as log]
   [integrant.core :as ig]))

(def system nil)

(defn profile []
  (if-let [p (System/getenv "APP_PROFILE")]
    (keyword p)
    :prod))

(defn -main [& args]
  (let [profile (profile)
        _ (log/info (format "Starting probematic with profile %s" profile))
        system-config (ol.system/system-config {:profile profile})
        sys (ig/init system-config)]
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread.
      (fn []
        (ig/halt! sys))))
    (alter-var-root #'system (constantly sys)))
  @(promise))
