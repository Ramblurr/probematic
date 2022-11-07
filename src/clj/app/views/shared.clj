(ns app.views.shared
  (:require
   [tick.core :as t]
   [app.icons :as icon]
   [app.render :as render]
   [app.humanize :as humanize]))

(defn divider-center [title]
  [:div {:class "relative"}
   [:div {:class "absolute inset-0 flex items-center", :aria-hidden "true"}
    [:div {:class "w-full border-t border-gray-300"}]]
   [:div {:class "relative flex justify-center"}
    [:span {:class "bg-white px-3 text-lg font-medium text-gray-900"}
     title]]])

(defn divider-left
  ([title] (divider-left title nil))
  ([title button]

   [:div {:class "relative"}
    [:div {:class "absolute inset-0 flex items-center", :aria-hidden "true"}
     [:div {:class "w-full border-t border-gray-300"}]]
    [:div {:class "relative flex items-center justify-between"}
     [:span {:class "bg-white pr-3 text-lg font-medium text-gray-900 bg-white pr-3 text-lg font-medium text-gray-900"}
      title]
     (when button
       button)]]))

(defn bool-bubble
  ([is-active]
   (bool-bubble is-active {true "Aktiv" false "Inaktiv"}))
  ([is-active labels]
   [:span {:class
           (render/cs "px-2 inline-flex text-xs leading-5 font-semibold rounded-full"
                      (when is-active "text-green-800 bg-green-100")
                      (when (not is-active) "text-red-800 bg-red-100"))}
    (get labels is-active)]))

(def gig-status-colors
  {:status/confirmed {:text-color "text-green-800"
                      :bg-color "bg-green-100"
                      :label "Confirmed"}
   :status/unconfirmed {:text-color "text-orange-800"
                        :bg-color "bg-orange-100"
                        :label "Unconfirmed"}

   :status/cancelled {:text-color "text-red-800"
                      :bg-color "bg-red-100"
                      :label "Cancelled"}})

(def gig-status-icons
  {:status/confirmed {:icon icon/circle-check
                      :color "text-green-500"}
   :status/unconfirmed {:icon icon/circle-question
                        :color "text-orange-500"}
   :status/cancelled {:icon icon/circle-xmark
                      :color "text-red-500"}})

(defn gig-status-bubble [status]
  (let [{:keys [text-color bg-color label]} (gig-status-colors status)]
    [:span {:class
            (render/cs "px-2 inline-flex text-xs leading-5 font-semibold rounded-full"
                       text-color bg-color)}
     label]))

(defn gig-status-icon [status]
  (let [{:keys [icon color]} (gig-status-icons status)]
    (icon {:class (str "mr-1.5 h-5 w-5 inline " color)})))

(defn format-dt [dt]
  (t/format (t/formatter "dd-MMM-yyyy") dt))

(defn datetime [dt]
  (if dt
    [:time {:dateetime (str dt)}
     (format-dt dt)]
    "never"))

(defn time [t]
  (when t
    (t/format (t/formatter "HH:mm") t)))

(defn humanize-dt [dt]
  (when dt
    [:time {:datetime (str dt)}
     (humanize/from dt)]))
