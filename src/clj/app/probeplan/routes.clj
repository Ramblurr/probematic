(ns app.probeplan.routes
  (:require
   [app.layout :as layout]
   [app.probeplan.views :as view]
   [ctmx.core :as ctmx]))

(defn probeplan-index-route []
  (ctmx/make-routes
   "/probeplan"
   (fn [req]
     (layout/app-shell req
                       (view/probeplan-index-page req)))))

(defn probeplan-routes []
  ["" {:app.route/name :app/probeplan}
   (probeplan-index-route)])
