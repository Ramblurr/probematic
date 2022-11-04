(ns app.members.views
  (:require
   [app.views.shared :as ui]
   [app.members.controller :as controller]
   [ctmx.response :as response]
   [app.render :as render]
   [app.icons :as icon]
   [ctmx.core :as ctmx]
   [tick.core :as t]
   [app.debug :as debug]
   [clojure.string :as str]
   [ctmx.rt :as rt]
   [medley.core :as m]))

(defn members-index-page [{:keys [db] :as req}]
  [:div
   (render/page-header :title "Insurance & Instruments")

   (let [members (controller/members db)]
     [:div {:class "mt-6 px-4 sm:px-6 lg:px-8"}
      (ui/divider-left "Policies"
                       (render/link-button :label "Insurance Policy"
                                           :priority :white-rounded
                                           :centered? true
                                           :attr {:href "/insurance-new/"} :icon icon/plus))
      [:div {:class "overflow-hidden bg-white shadow sm:rounded-md mb-8"
             :id "songs-list"}
       (if (empty? members)
         "No Policies"
         [:ul {:role "list", :class "divide-y divide-gray-200"}
          (map (fn [member]
                 [:li]) members)])]])])
