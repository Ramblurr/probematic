(ns app.dashboard.routes
  (:require
   [app.dashboard.views :as view]
   [app.layout :as layout]
   [ctmx.core :as ctmx]
   [app.gigs.routes :as gig.routes]
   [app.auth :as auth]))

(defn dashboard-route []
  (ctmx/make-routes
   "/"
   (fn [req]
     (layout/app-shell req
                       (view/dashboard-page req)))))

(defn routes []
  ["" {:app.route/name :app/dashboard
       :app.auth/roles #{:Mitglieder}
       :interceptors [auth/roles-authorization-interceptor]}
   (dashboard-route)])
