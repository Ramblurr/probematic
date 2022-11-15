(ns app.routes
  (:require
   [app.auth :as auth]
   [app.auth.auth-endpoint :as auth.endpoint]
   [app.gigs.routes :refer [events-routes]]
   [app.insurance.routes :refer [insurance-routes insurance-interceptors]]
   [app.interceptors :as interceptors]
   [app.members.routes :refer [members-routes]]
   [app.routes.home :refer [home-routes]]
   [app.routes.pedestal-reitit]
   [app.songs.routes :refer [songs-routes]]
   [reitit.ring :as ring]))

(defn routes [system]
  ["" {:coercion     interceptors/default-coercion
       :muuntaja     interceptors/formats-instance
       :interceptors (conj  (interceptors/default-interceptors system)
                            (interceptors/system-interceptor system)
                            (interceptors/datomic-interceptor system)
                            auth/require-authenticated-user
                            (interceptors/current-user-interceptor system))}

   (members-routes)
   (home-routes)
   (songs-routes)
   (events-routes)
   ["" {:interceptors insurance-interceptors}
    (insurance-routes)]
     ;["/index.html" (index-route frontend-index-adapter index-csp)]
   ])

(defn default-handler [{:keys [] :as system}]
  (ring/routes
   (ring/create-resource-handler {:path "/"})
   (ring/create-default-handler)))
