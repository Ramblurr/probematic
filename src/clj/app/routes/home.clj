(ns app.routes.home
  (:require
   [app.ui :as ui]
   [ctmx.core :as ctmx]
   [app.auth :as auth]))

(defn home-routes []
  ["" {:app.auth/roles #{:Mitglieder}
       :interceptors [auth/roles-authorization-interceptor]}
   (ctmx/make-routes
    "/"
    (fn [req]
      (ui/app-shell req
                    [:div.min-h-screen.bg-white
                     [:div.py-10 [:main [:div.max-w-7xl.mx-auto.sm:px-6.lg:px-8]
                                  [:div {:class "content"}
                                   [:ul
                                    [:li
                                     [:a {:href "/members"} "Members"]]
                                    [:li
                                     [:a {:href "/songs"} "Songs"]]
                                    [:li
                                     [:a {:href "/events"} "Gigs/Probes"]]
                                    [:li
                                     [:a {:href "/insurance"} "Instrument Insurance"]]]]]]])))])
