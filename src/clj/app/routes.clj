(ns app.routes
  (:require
   [app.routes.home :refer [home-routes]]
   [app.songs.routes :refer [songs-routes]]
   [app.members.routes :refer [members-routes]]
   [app.insurance.routes :refer [insurance-routes insurance-interceptors]]
   [app.gigs.routes :refer [events-routes]]
   [app.routes.login :refer [login-routes]]
   [app.routes.pedestal-reitit]
   [app.auth.auth-endpoint :as auth.endpoint]
   [app.auth :as auth]
   [app.health.health-endpoint :as health]
   [app.routes.helpers :as route.helpers]
   [app.features :as f]
   [reitit.ring :as ring]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]))

(defn routes [system]
  (let [session-interceptor (auth/session-interceptor system)]
    ["" {:coercion     route.helpers/default-coercion
         :muuntaja     route.helpers/formats-instance
         :interceptors (conj  route.helpers/default-interceptors
                              (route.helpers/system-interceptor system)
                              (route.helpers/datomic-interceptor system)
                              (route.helpers/i18n-interceptor system))}

     (members-routes)
     (home-routes)
     (songs-routes)
     (events-routes)
     (login-routes)
     ["" {:interceptors insurance-interceptors}
      (insurance-routes)]
     ;["/index.html" (index-route frontend-index-adapter index-csp)]
     ["/login-backend"
      {:get {:handler (partial #'auth.endpoint/login-page system)}}]

     ;; Api
     ["/api" {:coercion     route.helpers/default-coercion
              :muuntaja     route.helpers/formats-instance
              :swagger      {:id ::api}
              :interceptors (conj route.helpers/default-interceptors
                                  auth/roles-authorization-interceptor)}
      ["/swagger.json"
       {:get {:no-doc  true
              :swagger {:info {:title "app API"}}
              :handler (swagger/create-swagger-handler)}}]
      ["/health"
       {:summary "Retrieve the current health status of the system"
        :get health/healthcheck!}]
      ["/features"
       {:get {:swagger {:info {:title "Get a list of active features"}}
              :handler f/list-features-handler}}]
      ["/features/enable" {:post {:swagger {:info {:title "Enable a feature"}}
                                  :parameters {:body [:map [:feature :string]]}
                                  :responses {200 {:body [:map [:feature :string]
                                                          [:value :boolean]]}
                                              400 {:body  [:map [:msg :string]]}}
                                  :handler f/enable-feature-handler}}]
      ["/features/disable" {:post {:swagger {:info {:title "Disable a feature"}}
                                   :responses {200 {:body [:map [:feature :string]
                                                           [:value :boolean]]}
                                               400 {:body  [:map [:msg :string]]}}
                                   :handler f/disable-feature-handler}}]
      ["/logged-in"
       {:get {:summary "Check if a user is currently authenticated"
              :responses {200 {:body [:map [:loggedIn :boolean]]}}
              :handler auth.endpoint/logged-in-handler}}]
      ;["/login"
      ; {:post {:summary "Authenticate a user"
      ;         :parameters {:body [:map
      ;                             [:username  :string]
      ;                             [:password  :string]]}
      ;         :handler (partial #'auth.endpoint/login-handler! system)}}]

      ["/login-form"
       {:get {:no-doc true
              :handler (partial #'auth.endpoint/login-page system)}
        :post {:no-doc true
               :form [:map [:username  :string]
                      [:password  :string]]
               :handler (partial #'auth.endpoint/login-form-handler! system)}}]
      ["/logout"
       {:post {:summary "Logs out a user" :handler (partial #'auth.endpoint/logout-handler! system)}
        :get {:summary "Logs out a user" :handler (partial #'auth.endpoint/logout-handler! system)}}]]]))

(defn default-handler [{:keys [] :as system}]
  (ring/routes
   (ring/create-resource-handler {:path "/"})
   (ring/create-default-handler)))
