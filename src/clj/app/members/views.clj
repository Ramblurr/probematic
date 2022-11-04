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
   [medley.core :as m]
   [app.util :as util]))

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

(defn member-by-id [req gigo-key]
  (m/find-first #(= gigo-key (:member/gigo-key %)) (:members req)))

(ctmx/defcomponent ^:endpoint member-row-rw [{:keys [db] :as req} ^:long idx gigo-key]
  (let [td-class "px-3 py-4"
        comp-name  (-> #'member-row-rw meta :name str)
        post? (util/post? req)
        {:member/keys [name email active? phone]} (cond
                                                    post? (:member (controller/toggle-active-state! req gigo-key))
                                                    :else (member-by-id req gigo-key))]
    [:tr {:id id}
     (list
      [:td {:class (render/cs "w-full max-w-0 py-4 pl-4 pr-3 sm:w-auto   sm:max-w-none sm:pl-6")}
       name]
      [:td {:class td-class} email]
      [:td {:class td-class} phone]
      [:td {:class td-class :hx-include (str "#member" idx " input") :id (str "member" idx)}
       [:input {:type "hidden" :name "idx" :value idx}]
       [:input {:type "hidden" :name "gigo-key" :value gigo-key}]
       (let [icon (if active? icon/xmark icon/plus)
             class (if active? "text-red-500" "text-green-500")]
         (render/button :icon icon :size :small
                        :class class
                        :attr {:hx-post comp-name :hx-target (hash ".")}))]
      ;;
      )]))

(ctmx/defcomponent ^:endpoint member-row-ro [{:keys [db] :as req} idx gigo-key]
  (let [{:member/keys [name email active? phone]} (member-by-id req gigo-key)
        td-class "px-3 py-4"]
    [:tr {:id id}
     (list
      [:td {:class (render/cs "w-full max-w-0 py-4 pl-4 pr-3 sm:w-auto   sm:max-w-none sm:pl-6")}
       name]
      [:td {:class td-class} email]
      [:td {:class td-class} phone]
      [:td {:class td-class} (ui/bool-bubble active?)]
      ;;
      )]))

(ctmx/defcomponent ^:endpoint member-table-rw [{:keys [db] :as req}]
  (ctmx/with-req req
    (let [members (-> req :members)
          table-headers (member-table-headers members)]
      (list
       (render/table-row-head table-headers)
       (render/table-body
        (rt/map-indexed member-row-rw req (map :member/gigo-key members)))))))

(ctmx/defcomponent ^:endpoint member-table-ro [{:keys [db] :as req}]
  (ctmx/with-req req
    (let [members (-> req :members)
          table-headers (member-table-headers members)]
      (list
       (render/table-row-head table-headers)
       (render/table-body
        (rt/map-indexed member-row-ro req (map :member/gigo-key members)))))))

(ctmx/defcomponent ^:endpoint members-index-page [{:keys [db] :as req}]
  (ctmx/with-req req
    (let [edit? (util/qp-bool req :edit)
          add? (util/qp-bool req :add)
          comp-name (fn [s] (str (-> #'members-index-page meta :name) s))]
      [:div {:id id}
       (render/page-header :title "Member Admin")

       (let [_ 1]
         [:div {:class "mt-2"}
          [:div {:class "px-4 sm:px-6 lg:px-8"}
           [:div {:class "flex items-center justify-center bg-white p-8"}]
           [:div {:class "sm:flex sm:items-center"}
            [:div {:class "sm:flex-auto"}
             [:h1 {:class "text-2xl font-semibold text-gray-900"} "Members"]
             [:p {:class "mt-2 text-sm text-gray-700"} ""]]
            [:div {:class "mt-4 sm:mt-0 sm:ml-16 flex sm:flex-row sm:space-x-4"}
             (render/toggle :label "Edit" :active? edit? :id "member-table-edit-toggle" :hx-target (hash ".") :hx-get (if edit? (comp-name "?edit=false") (comp-name "?edit=true")))
             (render/button :label "Add" :priority :white :class "" :icon icon/plus :centered? true
                            :attr {:hx-target (hash ".") :hx-get (comp-name "?add=true")})]]

           [:div {:class "mt-4"}
            [:table {:class "min-w-full divide-y divide-gray-300"}
             (if edit?
               (member-table-rw req)
               (member-table-ro req))]]]])])))
