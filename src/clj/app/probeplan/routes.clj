(ns app.probeplan.routes
  (:require
   [app.layout :as layout]
   [ctmx.core :as ctmx]))

(defn probeplan-list-route []
  (ctmx/make-routes
   "/probeplan"
   (fn [req]
     (layout/app-shell req
                       [:div "hello world"]))))

(defn probeplan-routes []
  ["" {:app.route/name :app/probeplan}
   (probeplan-list-route)])
