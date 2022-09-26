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

(def _events [{:event/title "Probe 2022-09-29"
               :event/date (t/date "2022-09-29")
               :event/type :event/probe
               :event/location "Proberaum"
               :event/id "A"
               :event/status :event/confirmed}

              {:event/title "Probe 2022-10-01"
               :event/date (t/date "2022-10-01")
               :event/type :event/probe
               :event/location "Proberaum"
               :event/id "B"
               :event/status :event/unconfirmed}])

(defn events-list-routes [{:keys [conn]}]
  (ctmx/make-routes
   "/events"
   (fn [req]
     (let [events (db/gigs-future @conn)]
       (tap> (:gig/date (first events)))

       (render/html5-response
        [:div {:class "mt-6"}
         [:div {:class ""}
          (ui/divider-left "Upcoming")
          [:div {:class "overflow-hidden bg-white shadow sm:rounded-md"
                 :id "songs-list"}
           (event-list events)]
          (ui/divider-left "Past")
          [:div {:class "overflow-hidden bg-white shadow sm:rounded-md"
                 :id "songs-list"}
           (event-list [])]

                                        ;
          [:a {:href "/events/new" :class "flex-initial inline-flex items-center rounded-md border border-transparent bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}  "<!-- Heroicon name: mini/envelope -->"
           (icon/plus {:class "-ml-1 mr-2 h-5 w-5"})
           "Gig/Probe"]]

                                        ;
         ])))))

(defn events-routes [conn]
  [""
   (events-list-routes conn)])
