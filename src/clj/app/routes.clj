(ns app.routes
  (:require
   [app.routes.home :refer [home-routes]]
   [app.routes.songs :refer [songs-routes]]
   [app.routes.events :refer [events-routes]]
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

(defn routes [{:keys [conn] :as system}]
  (let [session-interceptor (auth/session-interceptor system)]
    ["" {:coercion     route.helpers/default-coercion
         :muuntaja     route.helpers/formats-instance
         :interceptors route.helpers/default-interceptors}

     (home-routes)
     (songs-routes system)
     (events-routes system)
     (login-routes)
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

(defn wrap-swagger-ui [api-path swagger-url]
  (let [handler (swagger-ui/create-swagger-ui-handler
                 {:path   api-path
                  :url    swagger-url
                  :config {:validatorUrl     nil
                           :operationsSorter "alpha"}})]
    (fn [req]
      (when-let [resp (handler req)]
        (assoc-in resp [:headers "Content-Security-Policy"] route.helpers/relaxed-csp-header-value)))))

(defn default-handler [{:keys [] :as system}]
  (ring/routes
   (wrap-swagger-ui "/api" "/api/swagger.json")
   (ring/create-resource-handler {:path "/"})
   (ring/create-default-handler)))
