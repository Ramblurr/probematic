(ns app.routes
  (:require
   [app.auth :as auth]
   [app.gigs.routes :refer [events-routes]]
   [app.file-browser.routes :refer [file-browser-routes]]
   [app.insurance.routes :refer [insurance-routes]]
   [app.interceptors :as interceptors]
   [app.members.routes :refer [members-routes]]
   [app.dashboard.routes :refer [dashboard-routes]]
   [app.routes.pedestal-reitit]
   [app.songs.routes :refer [songs-routes]]
   [app.probeplan.routes :refer [probeplan-routes]]
   [reitit.ring :as ring]))

(defn routes [system]
  ["" {:coercion     interceptors/default-coercion
       :muuntaja     interceptors/formats-instance
       :interceptors (conj  (interceptors/default-interceptors system)
                            (interceptors/system-interceptor system)
                            (interceptors/datomic-interceptor system)
                            auth/require-authenticated-user
                            (interceptors/webdav-interceptor system)
                            (interceptors/current-user-interceptor system))}

   (members-routes)
   (dashboard-routes)
   (songs-routes)
   (events-routes)
   (probeplan-routes)
   (insurance-routes)
   (file-browser-routes)
     ;["/index.html" (index-route frontend-index-adapter index-csp)]
   ])

(defn default-handler [{:keys [] :as system}]
  (ring/routes
   (ring/create-resource-handler {:path "/"})
   (ring/create-default-handler)))
