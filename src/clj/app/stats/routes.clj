(ns app.stats.routes
  (:require
   [app.layout :as layout]
   [app.stats.views :as view]
   [ctmx.core :as ctmx]))

(defn stats-index []
  (ctmx/make-routes
   "/stats"
   (fn [req]
     (layout/app-shell req
                       (view/stats-index-page req)))))

(defn routes []
  ["" {:app.route/name :app/stats}
   (stats-index)])
