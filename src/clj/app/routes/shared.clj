(ns app.routes.shared
  (:require
   [tick.core :as t]
   [app.render :as render]))

(defn divider-center [title]
  [:div {:class "relative"}
   [:div {:class "absolute inset-0 flex items-center", :aria-hidden "true"}
    [:div {:class "w-full border-t border-gray-300"}]]
   [:div {:class "relative flex justify-center"}
    [:span {:class "bg-white px-3 text-lg font-medium text-gray-900"}
     title]]])

(defn divider-left [title]

  [:div {:class "relative"}
   [:div {:class "absolute inset-0 flex items-center", :aria-hidden "true"}
    [:div {:class "w-full border-t border-gray-300"}]]
   [:div {:class "relative flex justify-start"}
    [:span {:class "bg-white pr-3 text-lg font-medium text-gray-900"}
     title]]])

(defn bool-bubble
  ([is-active]
   (bool-bubble is-active {true "Active" false "Inactive"}))
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

(defn gig-status [status]
  (let [{:keys [text-color bg-color label]} (gig-status-colors status)]
    [:span {:class
            (render/cs "px-2 inline-flex text-xs leading-5 font-semibold rounded-full"
                       text-color bg-color)}
     label]))

(defn format-dt [dt]
  (t/format (t/formatter "dd-MMM-yyyy") dt))

(defn datetime [dt]
  (if dt
    [:time {:dateetime (str dt)}
     (format-dt dt)]
    "never"))
