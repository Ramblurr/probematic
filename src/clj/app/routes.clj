(ns app.routes
  (:require
   [reitit.coercion.malli :as rcm]
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
   [reitit.ring :as ring]
   [app.sardine :as sardine]))

(defn routes [system]
  ["" {:coercion     interceptors/default-coercion
       :muuntaja     interceptors/formats-instance
       :interceptors (conj  (interceptors/default-interceptors system)
                            (auth/session-interceptor system)
                            (interceptors/system-interceptor system)
                            (interceptors/datomic-interceptor system)
                             ;;
                            )}
   ["/login" {:handler (fn [req] (auth/login-page-handler (:env system) (:oauth2 system) req))}]
   ["/logout" {:handler (fn [req] (auth/logout-page-handler (:env system) (:oauth2 system) req))}]
   ["/oauth2"
    ["/callback" {:handler (fn [req] (auth/oauth2-callback-handler (:env system) (:oauth2 system) req))}]]

   ["" {:interceptors [auth/require-authenticated-user
                       (interceptors/webdav-interceptor system)
                       (interceptors/current-user-interceptor system)]}

    (members-routes)
    (dashboard-routes)
    (songs-routes)
    (events-routes)
    (probeplan-routes)
    (insurance-routes)
    (file-browser-routes)
    ["/nextcloud-fetch" {:parameters {:query [:map [:path string?]]}
                         :coercion rcm/coercion
                         :app.route/name :app/nextcloud-fetch
                         :handler (fn [req] (sardine/fetch-file-handler req false))}]
                                        ;["/index.html" (index-route frontend-index-adapter index-csp)]
    ]])

(defn default-handler [{:keys [] :as system}]
  (ring/routes
   (ring/create-resource-handler {:path "/"})
   (ring/create-default-handler)))
