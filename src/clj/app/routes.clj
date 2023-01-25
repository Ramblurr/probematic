(ns app.routes
  (:require
   [app.auth :as auth]
   [app.dashboard.routes :refer [dashboard-routes]]
   [app.file-browser.routes :refer [file-browser-routes]]
   [app.gigs.routes :refer [gig-answer-link-route gigs-routes]]
   [app.insurance.routes :refer [insurance-routes]]
   [app.interceptors :as interceptors]
   [app.members.routes :refer [members-routes]]
   [app.probeplan.routes :refer [probeplan-routes]]
   [app.sardine :as sardine]
   [app.songs.routes :refer [songs-routes]]
   [reitit.coercion.malli :as rcm]
   [reitit.ring :as ring]))

(defn routes [system]
  ["" {:coercion     interceptors/default-coercion
       :muuntaja     interceptors/formats-instance
       :interceptors (conj  (interceptors/default-interceptors system)
                            (auth/session-interceptor system)
                            (interceptors/system-interceptor system)
                            (interceptors/datomic-interceptor system)
                            (interceptors/current-user-interceptor system)
                             ;;
                            )}
   ["/login" {:handler (fn [req] (auth/login-page-handler (:env system) (:oauth2 system) req))}]
   ["/logout" {:handler (fn [req] (auth/logout-page-handler (:env system) (:oauth2 system) req))}]
   ["/oauth2"
    ["/callback" {:handler (fn [req] (auth/oauth2-callback-handler (:env system) (:oauth2 system) req))}]]
   (gig-answer-link-route)

   ["" {:interceptors [auth/require-authenticated-user
                       (interceptors/webdav-interceptor system)]}

    (members-routes)
    (dashboard-routes)
    (songs-routes)
    (gigs-routes)
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
