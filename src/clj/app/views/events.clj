(ns app.views.events
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

(ctmx/defcomponent ^:endpoint events-list-page [req]
  (let [events (db/gigs-future @(-> req :system :conn))]
    [:div
     (render/page-header :title "Gigs/Probes"
                         :buttons  (list (render/button :label "Share2"
                                                        :priority :white
                                                        :centered? true
                                                        :attr {:href "/events/new"})
                                         (render/button :label "Gig/Probe"
                                                        :priority :primary
                                                        :centered? true
                                                        :attr {:href "/events/new"} :icon icon/plus)))

     [:div {:class "mt-6 px-4 sm:px-6 lg:px-8"}
      (ui/divider-left "Upcoming")
      [:div {:class "overflow-hidden bg-white shadow sm:rounded-md mb-8"
             :id "songs-list"}
       (event-list events)]
      (ui/divider-left "Past")
      [:div {:class "overflow-hidden bg-white shadow sm:rounded-md"
             :id "songs-list"}
       (event-list [])]]]))
