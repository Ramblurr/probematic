(ns app.settings.routes
  (:require
   [app.auth :as auth]
   [app.settings.views :as view]
   [app.layout :as layout]
   [ctmx.core :as ctmx]))

(defn settings-route []
  (ctmx/make-routes
   "/band-settings"
   (fn [req]
     (layout/app-shell req
                       (view/settings-page req)))))

(defn routes []
  ["" {:app.route/name :app/band-settings
       :app.auth/roles #{:Mitglieder}
       :interceptors [auth/roles-authorization-interceptor]}
   (settings-route)])
