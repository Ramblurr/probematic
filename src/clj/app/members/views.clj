(ns app.members.views
  (:require
   [app.util :as util]
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

(def link-member (partial util/link-helper "/member/" :member/gigo-key))

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
   {:label "Section" :priority :medium :key :value}
   {:label "Active?" :priority :medium :key :value}
   ;;
   ])

(defn member-by-id [req gigo-key]
  (m/find-first #(= gigo-key (:member/gigo-key %)) (:members req)))

(ctmx/defcomponent ^:endpoint members-detail-page [{:keys [db] :as req}]
  (let [comp-name (util/comp-namer #'members-detail-page)
        post? (util/post? req)
        edit? (util/qp-bool req :edit)
        member (cond post?
                     (:member (controller/update-member! req))
                     :else
                     (:member req))
        {:member/keys [name email phone active? section]} member
        sections (controller/sections db)
        section-name (:section/name section)]

    [(if edit? :form :div)
     (if edit?
       {:id id :hx-post (comp-name)}
       {:id id})
     [:div {:class "mt-8"}
      [:div {:class "mx-auto max-w-3xl px-4 sm:px-6 md:flex md:items-center md:justify-between md:space-x-5 lg:max-w-7xl lg:px-8"}
       [:div {:class "flex items-center space-x-5"}
        [:div {:class "flex-shrink-0"}
         [:div {:class "relative"}
          [:img {:class "h-16 w-16 rounded-full" :src "https://images.unsplash.com/photo-1463453091185-61582044d556?ixlib=rb-=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=facearea&facepad=8&w=1024&h=1024&q=80"}]
          [:span {:class "absolute inset-0 rounded-full shadow-inner" :aria-hidden "true"}]]]
        [:div
         [:h1 {:class "text-2xl font-bold text-gray-900"}
          (if edit?
            (render/text :label "Name" :name (path "name") :value name)
            [:h1 {:class "text-2xl font-bold text-gray-900"}
             name])]
         [:p {:class "text-sm font-medium text-gray-500"}
          (if edit?
            (render/section-select :label "Section" :id (path "section-name") :value section-name :class "mt-4" :sections sections)
            (if section-name section-name "No Section"))]]]
       [:div {:class "justify-stretch mt-6 flex flex-col-reverse space-y-4 space-y-reverse sm:flex-row-reverse sm:justify-end sm:space-y-0 sm:space-x-3 sm:space-x-reverse md:mt-0 md:flex-row md:space-x-3"}
        (if edit?
          (list
           (render/button :label "Cancel" :priority :white :centered? true
                          :attr {:hx-get (comp-name "?edit=false") :hx-target (hash ".")})
           (render/button :label "Save" :priority :primary :centered? true))
          (render/button :label "Edit" :priority :white :centered? true
                         :attr {:hx-get (comp-name "?edit=true") :hx-target (hash ".")}))]]
      [:div {:class "mx-auto mt-8 grid max-w-3xl grid-cols-1 gap-6 sm:px-6 lg:max-w-7xl lg:grid-flow-col-dense lg:grid-cols-3"}
       [:div {:class "space-y-6 lg:col-span-2 lg:col-start-1"}]
       [:dl {:class "grid grid-cols-1 gap-x-4 gap-y-8 sm:grid-cols-3"}
        [:div {:class "sm:col-span-1"}
         [:dt {:class "text-sm font-medium text-gray-500"} "E-Mail"]
         [:dd {:class "mt-1 text-sm text-gray-900"}
          (if edit?
            (render/input :name (path "email") :label "" :value email :type :email)
            email)]]
        [:div {:class "sm:col-span-1"}
         [:dt {:class "text-sm font-medium text-gray-500"} "Phone"]
         [:dd {:class "mt-1 text-sm text-gray-900"}
          (if edit?
            (render/input :type :tel :name (path "phone") :label "" :value phone :pattern "\\+[\\d-]+" :title "Phone number starting with +country code. Only spaces, dashes, and numbers")
            phone)]]
        [:div {:class "sm:col-span-1"}
         [:dt {:class "text-sm font-medium text-gray-500"} "Aktiv?"]
         [:dd {:class "mt-1 text-sm text-gray-900"}
          (if edit?
            (render/toggle-checkbox  :checked? active? :name (path "active?"))
            (ui/bool-bubble active?))]]]]]]))

(ctmx/defcomponent ^:endpoint member-row-rw [{:keys [db] :as req} ^:long idx gigo-key]
  (let [td-class "px-3 py-4"
        comp-name  (util/comp-namer #'member-row-rw)
        member (cond
                 (util/post? req) (:member (controller/update-active-and-section! req))
                 :else (member-by-id req gigo-key))
        {:member/keys [name email active? phone section]} member
        sections (controller/sections db)
        section-name (:section/name section)]
    [:tr {:id id :hx-include (str "#" id " input, #"  id " select")}
     (list
      [:td {:class (render/cs "w-full max-w-0 py-4 pl-4 pr-3 sm:w-auto   sm:max-w-none sm:pl-6")}
       name]
      [:td {:class td-class} email]
      [:td {:class td-class} phone]
      [:td {:class td-class}
       (render/section-select :label "" :id "section-name" :value section-name :class "mt-4" :sections sections :extra-attrs {:hx-post (comp-name) :hx-target (hash ".")})]
      [:td {:class td-class :_hx-include (str "#member" idx " input") :id (str "member" idx)}
       [:input {:type "hidden" :name "idx" :value idx}]
       [:input {:type "hidden" :name "gigo-key" :value gigo-key}]
       (let [icon (if active? icon/xmark icon/plus)
             class (if active? "text-red-500" "text-green-500")]
         (render/button :icon icon :size :small
                        :class class
                        :attr {:hx-post (comp-name) :hx-target (hash ".")}))]
      ;;
      )]))

(ctmx/defcomponent ^:endpoint member-row-ro [{:keys [db] :as req} idx gigo-key]
  (let [{:member/keys [name email active? phone section] :as member} (member-by-id req gigo-key)
        section-name (:section/name section)
        td-class "px-3 py-4"]
    [:tr {:id id :hx-boost true}
     (list
      [:td {:class (render/cs "w-full max-w-0 py-4 pl-4 pr-3 sm:w-auto   sm:max-w-none sm:pl-6")}
       [:a {:href (link-member member) :class "font-medium text-blue-600 hover:text-blue-500"}
        name]]
      [:td {:class td-class} email]
      [:td {:class td-class} phone]
      [:td {:class td-class} section-name]
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
          comp-name (util/comp-namer #'members-index-page)]
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
