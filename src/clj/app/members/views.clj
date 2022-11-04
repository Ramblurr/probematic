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

(defn member-table-headers [members]
  [{:label "Name" :priority :normal :key :name
    :render-fn (fn [_ instrument]
                 (list
                  (:name instrument)
                  [:dl {:class "font-normal sm:hidden"}
                   [:dt {:class "sr-only"} (:name instrument)]
                   [:dd {:class "mt-1 truncate text-gray-700"} (:owner instrument)]]))}

   {:label "Email" :priority :medium :key :owner}
   {:label "Phone" :priority :medium :key :value}
   {:label "Active?" :priority :medium :key :value}
    ;;
   ])

(ctmx/defcomponent ^:endpoint member-table-rw [{:keys [db] :as req}])

(defn member-by-id [req gigo-key]
  (m/find-first #(= gigo-key (:member/gigo-key %)) (:members req)))

(ctmx/defcomponent ^:endpoint member-row-ro [{:keys [db] :as req} idx gigo-key]
  (let [{:member/keys [name email active? phone]} (member-by-id req gigo-key)
        td-class "px-3 py-4"]
    gigo-key
    [:tr {:id id}
     (list
      [:td {:class (render/cs "w-full max-w-0 py-4 pl-4 pr-3 sm:w-auto   sm:max-w-none sm:pl-6")}
       name]
      [:td {:class td-class} email]
      [:td {:class td-class} phone]
      [:td {:class td-class} active?]
      ;;
      )]))

(ctmx/defcomponent ^:endpoint member-table-ro [{:keys [db] :as req}]
  (ctmx/with-req req
    (let [members (-> req :members)
          table-headers (member-table-headers members)]
      (list
       (render/table-row-head table-headers)
       (render/table-body
        (rt/map-indexed member-row-ro req (map :member/gigo-key members)))))))

(ctmx/defcomponent ^:endpoint members-index-page [{:keys [db] :as req}]
  (let [edit? false]
    [:div
     (render/page-header :title "Member Admin")

     (let [_ 1]
       [:div {:class "mt-6 px-4 sm:px-6 lg:px-8"}
        (ui/divider-left "Members"
                         (render/link-button :label "Member"
                                             :priority :white-rounded
                                             :centered? true
                                             :attr {:href "/member-new/"} :icon icon/plus))
        [:div {:class ""}
         [:div {:class "mt-4"}
          [:table {:class "min-w-full divide-y divide-gray-300"}
           (if edit?
             (member-table-rw req)
             (member-table-ro req))]]]])]))
