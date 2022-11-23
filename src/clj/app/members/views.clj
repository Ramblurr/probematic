(ns app.members.views
  (:require
   [app.util :as util]
   [app.urls :as url]
   [app.ui :as ui]
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
   [app.i18n :as i18n]))

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
        tr (i18n/tr-from-req req)
        member (cond post?
                     (:member (controller/update-member! req))
                     :else
                     (:member req))
        {:member/keys [name nick email phone active? section]} member
        coverages (controller/member-current-insurance-info req member)
        private-instruments (filter :instrument.coverage/private? coverages)
        band-instruments (filter #(not (:instrument.coverage/private? %)) coverages)
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
          (ui/avatar-img member :class "h-16 w-16 rounded-full")
          [:span {:class "absolute inset-0 rounded-full shadow-inner" :aria-hidden "true"}]]]
        [:div
         [:h1 {:class "text-2xl font-bold text-gray-900"}
          (if edit?
            [:div {:class "flex flex-col sm:flex-row"}
             (ui/text :label (tr [:member/name])  :name (path "name") :value name :class "mb-2 sm:mb-0 sm:mr-2")
             (ui/text :label (tr [:member/nick])  :name (path "nick") :value nick)]
            [:h1 {:class "text-2xl font-bold text-gray-900"}
             name
             (when nick
               [:sup {:class "text-gray-500 text-lg"}
                (str " (" nick ")")])])]
         [:p {:class "text-sm font-medium text-gray-500"}
          (if edit?
            (ui/section-select :label (tr [:section])  :id (path "section-name") :value section-name :class "mt-4" :sections sections)
            (if section-name section-name
                (tr [:section-none])))]]]
       [:div {:class "justify-stretch mt-6 flex flex-col-reverse space-y-4 space-y-reverse sm:flex-row-reverse sm:justify-end sm:space-y-0 sm:space-x-3 sm:space-x-reverse md:mt-0 md:flex-row md:space-x-3"}
        (if edit?
          (list
           (ui/button :label (tr [:action/cancel]) :priority :white :centered? true
                      :attr {:hx-get (comp-name "?edit=false") :hx-target (hash ".")})
           (ui/button :label (tr [:action/save])  :priority :primary :centered? true))
          (ui/button :label (tr [:action/edit]) :priority :white :centered? true
                     :attr {:hx-get (comp-name "?edit=true") :hx-target (hash ".")}))]]
      (ui/panel {:title
                 (tr [:Contact-Information])}
                (ui/dl
                 (ui/dl-item (tr [:Email]) (if edit?
                                             (ui/input :name (path "email") :label "" :value email :type :email)
                                             email))
                 (ui/dl-item (tr [:Phone]) (if edit?
                                             (ui/input :type :tel :name (path "phone") :label "" :value phone :pattern "\\+[\\d-]+" :title "Phone number starting with +country code. Only spaces, dashes, and numbers")
                                             phone))
                 (ui/dl-item (tr [:Active]) (if edit?
                                              (ui/toggle-checkbox  :checked? active? :name (path "active?"))
                                              (ui/bool-bubble active?)))))

      (ui/panel {:title
                 (tr [:member/insurance-title])
                 :subtitle
                 (tr [:member/insurance-subtitle])}
                (ui/dl

                 (ui/dl-item (tr [:band-instruments])  (count band-instruments))
                 (ui/dl-item (tr [:private-instruments]) (count private-instruments))
                 (ui/dl-item (tr [:outstanding-payments]) (ui/money 0 :EUR))
                 (ui/dl-item (tr [:instruments]) (if (empty? coverages)
                                                   (tr [:none])
                                                   (ui/rich-ul {}
                                                               (map (fn [{:instrument.coverage/keys [private? value] {:instrument/keys [name category]} :instrument.coverage/instrument}]
                                                                      (ui/rich-li {:icon icon/trumpet}
                                                                                  (ui/rich-li-text {} name)
                                                                                  (ui/rich-li-text {} (:instrument.category/name category))
                                                                                  (ui/rich-li-text {} (ui/money value :EUR))
                                                                                  (ui/rich-li-text {} (ui/bool-bubble (not private?) {false "Private" true "Band"}))
                                                                                  (ui/rich-li-action-a :href "#" :label "View")))
                                                                    coverages))) "sm:col-span-3")))
      (ui/panel {:title (tr ["Gigs & Probes"])
                 :subtitle (tr ["Fun stats!"])}
                (ui/dl
                 (ui/dl-item (tr ["Gigs Attended"]) "coming soon")
                 (ui/dl-item (tr ["Probes Attended"]) "coming soon")))]]))

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
      [:td {:class (ui/cs "w-full max-w-0 py-4 pl-4 pr-3 sm:w-auto   sm:max-w-none sm:pl-6")}
       name]
      [:td {:class td-class} email]
      [:td {:class td-class} phone]
      [:td {:class td-class}
       (ui/section-select :label "" :id "section-name" :value section-name :class "mt-4" :sections sections :extra-attrs {:hx-post (comp-name) :hx-target (hash ".")})]
      [:td {:class td-class :_hx-include (str "#member" idx " input") :id (str "member" idx)}
       [:input {:type "hidden" :name "idx" :value idx}]
       [:input {:type "hidden" :name "gigo-key" :value gigo-key}]
       (let [icon (if active? icon/xmark icon/plus)
             class (if active? "text-red-500" "text-green-500")]
         (ui/button :icon icon :size :small
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
      [:td {:class (ui/cs "w-full max-w-0 py-4 pl-4 pr-3 sm:w-auto   sm:max-w-none sm:pl-6")}
       [:a {:href (url/link-member member) :class "font-medium text-blue-600 hover:text-blue-500"}
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
       (ui/table-row-head table-headers)
       (ui/table-body
        (rt/map-indexed member-row-rw req (map :member/gigo-key members)))))))

(ctmx/defcomponent ^:endpoint member-table-ro [{:keys [db] :as req}]
  (ctmx/with-req req
    (let [members (-> req :members)
          table-headers (member-table-headers members)]
      (list
       (ui/table-row-head table-headers)
       (ui/table-body
        (rt/map-indexed member-row-ro req (map :member/gigo-key members)))))))

(ctmx/defcomponent ^:endpoint members-index-page [{:keys [db] :as req} ^:boolean edit?]
  (ctmx/with-req req
    (let [add? (util/qp-bool req :add)
          comp-name (util/comp-namer #'members-index-page)]
      [:div {:id id}
       (ui/page-header :title "Member Admin")
       [:div {:class "mt-2"}
        [:div {:class "px-4 sm:px-6 lg:px-8"}
         [:div {:class "flex items-center justify-end"}
          [:div {:class "mt-4 sm:mt-0 sm:ml-16 flex sm:flex-row space-x-4"}
           (ui/toggle :label "Edit" :active? edit? :id "member-table-edit-toggle" :hx-target (hash ".") :hx-get (comp-name) :hx-vals {"edit?" (not edit?)})
           (ui/button :label "Add" :priority :white :class "" :icon icon/plus :centered? true
                      :attr {:hx-target (hash ".") :hx-get (comp-name "?add=true")})]]

         [:div {:class "mt-4"}
          [:table {:class "min-w-full divide-y divide-gray-300"}
           (if edit?
             (member-table-rw req)
             (member-table-ro req))]]]]])))
