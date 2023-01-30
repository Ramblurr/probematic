(ns app.probeplan.routes
  (:require
   [app.queries :as q]
   [app.layout :as layout]
   [app.probeplan.views :as view]
   [ctmx.core :as ctmx]))

(defn probeplan-index-route []
  (ctmx/make-routes
   "/probeplan"
   (fn [req]
     (layout/app-shell req
                       (view/probeplan-index-page req false)))))

(def probeplan-interceptor
  {:name ::probeplan-interceptor
   :enter (fn [ctx]
            (assoc-in ctx [:request :all-songs] (q/retrieve-all-songs (-> ctx :request :db))))})

(defn routes []
  ["" {:app.route/name :app/probeplan
       :interceptors [probeplan-interceptor]}
   (probeplan-index-route)])
