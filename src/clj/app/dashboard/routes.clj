(ns app.dashboard.routes
  (:require
   [app.auth :as auth]
   [app.dashboard.views :as view]
   [app.layout :as layout]
   [ctmx.core :as ctmx]))

(defn calendar-route
  []
  (ctmx/make-routes
   "/calendar"
   (fn [req]
     (layout/app-shell req
                       (view/calendar-page req)))))

(defn dashboard-route
  []
  (ctmx/make-routes
   "/"
   (fn [req]
     (layout/app-shell req
                       (view/dashboard-page req)))))

(defn routes
  []
  ["" {:app.route/name :app/dashboard
       :app.auth/roles #{:Mitglieder}
       :interceptors [auth/roles-authorization-interceptor]}
   (dashboard-route)
   (calendar-route)])
