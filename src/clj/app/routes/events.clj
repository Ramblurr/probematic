(ns app.routes.events
  (:require
   [app.routes.shared :as ui]
   [app.render :as render]
   [app.icons :as icon]
   [ctmx.core :as ctmx]
   [tick.core :as t]
   [clojure.string :as clojure.string]
   [app.db :as db]))

(defn event-row [{:gig/keys [status title location date]}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"]
    [:a {:href "#", :class "block hover:bg-gray-50"}
     [:div {:class "px-4 py-4 sm:px-6"}
      [:div {:class "flex items-center justify-between"}
       [:p {:class "truncate text-sm font-medium text-indigo-600"}
        title]
       [:div {:class "ml-2 flex flex-shrink-0"}
        (ui/gig-status status)]]
      [:div {:class "mt-2 sm:flex sm:justify-between"}
       [:div {:class "flex"}
        [:p {:class "flex items-center text-sm text-gray-500"}
         (icon/location-dot {:class style-icon})
         location]
        [:p {:class "mt-2 flex items-center text-sm text-gray-500 mt-0 ml-6"}

         (icon/calendar {:class style-icon})
         (ui/datetime date)]]
       [:div {:class "mt-2 flex items-center text-sm text-gray-500 sm:mt-0"}
        ;(icon/calendar {:class style-icon})
        ;[:p "Last Played "]
        ]]]]))

(defn event-list [events]
  (if (empty? events)
    "No events"
    [:ul {:role "list", :class "divide-y divide-gray-200"}
     (map (fn [event]
            [:li
             (event-row event)]) events)]))

(defn events-list-routes []
  (ctmx/make-routes
   "/events"
   (fn [req]
     (let [events (db/gigs-future @(-> req :system :conn))]
       (tap> (:gig/date (first events)))

       (render/html5-response
        [:div
         [:div {:class "px-4 py-4 sm:flex sm:items-center sm:justify-between sm:px-6 lg:px-8"}
          [:div {:class "min-w-0 flex-1"}
           [:h1 {:class "text-lg font-medium leading-6 text-gray-900 sm:truncate"} "Gigs/Probes"]]
          [:div {:class "mt-4 flex sm:mt-0 sm:ml-4"}
           (comment [:a {:href "/events/new" :class "sm:order-0 order-1 ml-3 inline-flex items-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 sm:ml-0"}
                     (icon/plus {:class "-ml-1 mr-2 h-5 w-5"})
                     "Gig/Probe"])
           [:a {:href "/events/new"  :class "order-0 inline-flex items-center rounded-md border border-transparent bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 sm:order-1 sm:ml-3"}
            (icon/plus {:class "-ml-1 mr-2 h-5 w-5"})
            "Gig/Probe"]]]

         [:div {:class "mt-6 px-4 sm:px-6 lg:px-8"}
          (ui/divider-left "Upcoming")
          [:div {:class "overflow-hidden bg-white shadow sm:rounded-md mb-8"
                 :id "songs-list"}
           (event-list events)]
          (ui/divider-left "Past")
          [:div {:class "overflow-hidden bg-white shadow sm:rounded-md"
                 :id "songs-list"}
           (event-list [])]]])))))

(defn events-routes []
  [""
   (events-list-routes)])
