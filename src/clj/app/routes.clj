(ns app.routes
  (:require
   [app.routes.pedestal-reitit :as pedestal-reitit]
   [app.auth :as auth]
   [app.dashboard.routes :refer [dashboard-routes]]
   [app.file-browser.routes :refer [file-browser-routes]]
   [app.gigs.routes :refer [gig-answer-link-route gigs-routes gigs-routes2]]
   [app.insurance.routes :refer [insurance-routes]]
   [app.interceptors :as interceptors]
   [app.members.routes :refer [members-routes invite-accept-route]]
   [app.probeplan.routes :refer [probeplan-routes]]
   [app.sardine :as sardine]
   [app.songs.routes :refer [songs-routes]]
   [reitit.coercion.malli :as rcm]
   [reitit.ring :as ring]
   [integrant.repl.state :as state]
   [reitit.http :as http]))

(pedestal-reitit/nop)

(defn routes [system]
  ["" {:coercion     interceptors/default-coercion
       :muuntaja     interceptors/formats-instance
       :interceptors (conj  (interceptors/default-reitit-interceptors system)
                            (auth/session-interceptor system)
                            (interceptors/datomic-interceptor system)
                            (interceptors/current-user-interceptor system))}
   ["/login" {:handler (fn [req] (auth/login-page-handler (:env system) (:oauth2 system) req))
              :name ::login}]
   ["/logout" {:handler (fn [req] (auth/logout-page-handler (:env system) (:oauth2 system) req))
               :name ::logout}]
   ["/oauth2"
    ["/callback" {:handler (fn [req] (auth/oauth2-callback-handler (:env system) (:oauth2 system) req))
                  :name ::oauth2.callback}]]
   (gig-answer-link-route)
   (invite-accept-route)

   ["" {:interceptors [auth/require-authenticated-user
                       (interceptors/webdav-interceptor system)]}

    (members-routes)
    (dashboard-routes)
    (songs-routes)
    (gigs-routes)
    (gigs-routes2)

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

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require '[reitit.core :as r])
    (require '[reitit.http :as http])
    (require '[app.urls :as url])
    (def _routes (-> state/system :app.ig.router/routes :routes))
    (def _router (-> state/system :app.ig.router/routes :router))
    (tap> _routes))
  ;; rcf

  (keys state/system)
  (keys (:app.ig.router/routes state/system))
  (r/route-names _router)
  (r/match-by-path _router "/gigs")
  (r/match-by-name _router :app.gigs.routes/details-edit-form2)
  (r/match-by-name _router :app.gigs.routes/details-page)
  (url/endpoint _router    :app.gigs.routes/details-page)
  (r/match-by-name! _router :app.gigs.routes/details-page {})
  (r/match-by-name! _router :app.gigs.routes/details-edit-form {})

  ;;
  )
