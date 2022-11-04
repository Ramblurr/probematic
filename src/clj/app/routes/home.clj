(ns app.routes.home
  (:require
   [app.render :as render]
   [ctmx.core :as ctmx]))

(defn home-routes []
  (ctmx/make-routes
   "/"
   (fn [req]
     (render/html5-response
      [:div.min-h-screen.bg-white
       [:div.py-10 [:main [:div.max-w-7xl.mx-auto.sm:px-6.lg:px-8]
                    [:div {:class "content"}
                     [:ul
                      [:li
                       [:a {:href "/songs"} "Songs"]]
                      [:li
                       [:a {:href "/events"} "Gigs/Probes"]]
                      [:li
                       [:a {:href "/insurance"} "Instrument Insurance"]]]]]]]))))
