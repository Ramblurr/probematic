(ns app.members.views
  (:require
   [app.i18n :as i18n]
   [app.icons :as icon]
   [app.keycloak :as keycloak]
   [app.layout :as layout]
   [app.members.controller :as controller]
   [app.ui :as ui]
   [app.urls :as url]
   [app.util :as util]
   [ctmx.core :as ctmx]
   [ctmx.response :as response]
   [ctmx.rt :as rt]
   [app.queries :as q]
   [clojure.string :as str]
   [clojure.set :as set]
   [medley.core :as m]
   [app.auth :as auth]))

(def query-param-field-mapping
  {"name" :member/name
   "active" :member/active?
   "phone" :member/phone
   "email" :member/email
   "section" :section})

(defn parse-sort-param [v]
  (let [[param order] (str/split v #":")
        order (if (= "desc" order) :desc :asc)
        field (get query-param-field-mapping param nil)]
    (when field
      {:field  field
       :order order})))

(defn sort-param [{:keys [query-params] :as req}]
  (let [sort-spec (->> (util/ensure-coll (get query-params "sort" []))
                       (remove str/blank?)
                       (mapv parse-sort-param))]
    (when (seq sort-spec)
      sort-spec)))

(defn order-invert [o]
  (get {:asc :desc
        :desc :asc} o))

(defn serialize-sort-param [{:keys [field order]}]
  (when field
    (str "?sort=" (get (set/map-invert query-param-field-mapping) field)
         (order-invert (or order :desc)))))

(defn sort-param-by-field [sort-spec field]
  (or
   (m/find-first #(= field (:field %)) sort-spec)
   {:field field :order :asc}))

(defn member-table-headers-ro [req tr]
  (let [sort-spec (sort-param req)
        sort-param-maker (fn [k]
                           {:hx-boost "true"
                            :href (serialize-sort-param (sort-param-by-field sort-spec k))
                            :class "link-blue"})]

    [{:label
      [:a (sort-param-maker :member/name)
       (tr [:member/name])] :priority :important :key :name
      :render-fn (fn [_ instrument]
                   (list
                    (:name instrument)
                    [:dl {:class "font-normal sm:hidden"}
                     [:dt {:class "sr-only"} (:name instrument)]
                     [:dd {:class "mt-1 truncate text-gray-700"} (:owner instrument)]]))}

     {:label [:a (sort-param-maker :member/email) (tr [:Email])] :priority :low :key :owner}
     {:label [:a (sort-param-maker :member/phone) (tr [:Phone])] :priority :important :key :value}
     {:label [:a (sort-param-maker :section) (tr [:section])] :priority :medium :key :value}
     {:label [:a (sort-param-maker :member/active?) (tr [:Active])] :priority :low :key :value}
     ;;
     ]))
(defn member-table-headers-rw [tr]
  [{:label (tr [:member/name]) :priority :important :key :name
    :render-fn (fn [_ instrument]
                 (list
                  (:name instrument)
                  [:dl {:class "font-normal sm:hidden"}
                   [:dt {:class "sr-only"} (:name instrument)]
                   [:dd {:class "mt-1 truncate text-gray-700"} (:owner instrument)]]))}

   {:label (tr [:Email]) :priority :low :key :owner}
   {:label (tr [:Phone]) :priority :low :key :value}
   {:label (tr [:section]) :priority :important :key :value}
   {:label (tr [:Active]) :priority :important :key :value}
   ;;
   ])

(ctmx/defcomponent ^:endpoint members-detail-page [{:keys [db] :as req}  ^:boolean edit?]
  (let [comp-name (util/comp-namer #'members-detail-page)
        post? (util/post? req)
        tr (i18n/tr-from-req req)
        member (cond post?
                     (:member (controller/update-member! req))
                     :else
                     (:member req))
        {:member/keys [name nick email username phone active? section keycloak-id]} member
        coverages (controller/member-current-insurance-info req member)
        private-instruments (filter :instrument.coverage/private? coverages)
        band-instruments (filter #(not (:instrument.coverage/private? %)) coverages)
        sections (controller/sections db)
        section-name (:section/name section)
        current-user-admin? (auth/current-user-admin? req)
        account-enabled? (keycloak/user-account-enabled? (keycloak/kc-from-req req) keycloak-id)]
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
                      :attr {:hx-get (comp-name) :hx-target (hash ".") :hx-vals {:edit? false}})
           (ui/button :label (tr [:action/save])  :priority :primary :centered? true))
          (ui/button :label (tr [:action/edit]) :priority :white :centered? true
                     :attr {:hx-get (comp-name) :hx-target (hash ".") :hx-vals {:edit? true}}))]]
      (ui/panel {:title
                 (tr [:Contact-Information])}
                (ui/dl
                 (ui/dl-item (tr [:member/active?]) (if edit?
                                                      (ui/toggle-checkbox  :checked? active? :name (path "active?"))
                                                      (ui/bool-bubble active?)))
                 (ui/dl-item (tr [:member/email]) (if edit?
                                                    (ui/input :name (path "email") :label "" :value email :type :email)
                                                    email))
                 (ui/dl-item (tr [:member/phone]) (if edit?
                                                    (ui/input :type :tel :name (path "phone") :label "" :value phone :pattern "\\+[\\d-]+" :title
                                                              (tr [:member/phone-validation]))
                                                    phone))
                 (ui/dl-item (tr [:member/username])
                             (if (and edit? current-user-admin?)
                               (ui/text :label "" :name (path "username") :value username :required? false)
                               username))
                 (ui/dl-item (tr [:member/keycloak-id])
                             (if (and edit? current-user-admin?)
                               (ui/text :label "" :name (path "keycloak-id") :value keycloak-id :required? false)
                               [:a {:href (keycloak/link-user-edit (-> req :system :env)  keycloak-id)
                                    :class "link-blue"}
                                keycloak-id]))
                 (ui/dl-item (tr [:member/sno-id-enabled-disabled])
                             (if (and edit? current-user-admin?)
                               (ui/toggle-checkbox  :checked? account-enabled? :name (path "sno-id-enabled?"))
                               (ui/bool-bubble account-enabled?
                                               {true (tr [:member/sno-id-enabled])
                                                false (tr [:member/sno-id-disabled])})))))

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
                                                                                  (ui/rich-li-action-a :href "#" :label
                                                                                                       (tr [:action/view]))))
                                                                    coverages))) "sm:col-span-3")))
      (ui/panel {:title (tr ["Gigs & Probes"])
                 :subtitle (tr ["Fun stats!"])}
                (ui/dl
                 (ui/dl-item (tr [:member/gigs-attended]) "coming soon")
                 (ui/dl-item (tr [:member/probes-attended]) "coming soon")))]]))

(ctmx/defcomponent ^:endpoint member-row-rw [{:keys [db] :as req} ^:long idx member-id]
  (let [td-class "px-3 py-4"
        comp-name  (util/comp-namer #'member-row-rw)
        member (cond
                 (util/post? req) (:member (controller/update-active-and-section! req))
                 :else (q/retrieve-member db member-id))
        {:member/keys [name email active? phone section]} member
        sections (controller/sections db)
        section-name (:section/name section)]
    [:tr {:id id}
     (list
      [:td {:class (ui/cs "w-full max-w-0 py-4 pl-4 pr-3 sm:w-auto   sm:max-w-none sm:pl-6")}
       name]
      [:td {:class (ui/cs td-class (ui/table-row-priorities :low))} email]
      [:td {:class (ui/cs td-class (ui/table-row-priorities :low))} phone]
      [:td {:class td-class :hx-include (str "#" id " input, #"  id " select")}
       (ui/section-select :label "" :id "section-name" :value section-name :class "mt-4" :sections sections
                          :extra-attrs {:hx-post (comp-name) :hx-target (hash ".")
                                        :hx-vals {:member-id (str member-id)}})]
      [:td {:class td-class :id (str "member" idx)}
       (ui/toggle :active? active? :hx-post (util/endpoint-path member-row-rw)
                  :hx-vals {:active? (not active?)
                            :member-id (str member-id)}
                  :hx-target (hash "."))]

;;
      )]))

(ctmx/defcomponent ^:endpoint member-row-ro [{:keys [db tr] :as req} idx member-id]
  (let [{:member/keys [name email active? phone section] :as member} (q/retrieve-member db member-id)
        section-name (:section/name section)
        td-class "px-3 py-4"]
    [:tr {:id id :hx-boost "true"}
     (list
      [:td {:class (ui/cs "w-full max-w-0 py-4 pl-4 pr-3 sm:w-auto   sm:max-w-none sm:pl-6"
                          (ui/table-row-priorities :important))}
       [:a {:href (url/link-member member) :class "font-medium text-blue-600 hover:text-blue-500"} name]
       [:dl {:class "font-normal xl:hidden"}
        [:dt {:class "sr-only sm:hidden"} (tr [:member/email])]
        [:dd {:class "mt-1 truncate text-gray-500"} email]
        [:dt {:class "sr-only sm:hidden"} (tr [:section])]
        [:dd {:class "mt-1 truncate text-gray-500 sm:hidden"} section-name]
        [:dt {:class "sr-only sm:hidden"} (tr [:member/active?])]
        [:dd {:class "mt-1 truncate text-gray-500"} (ui/bool-bubble active?)]]]

      [:td {:class (ui/cs td-class (ui/table-row-priorities :low))} email]
      [:td {:class (ui/cs td-class (ui/table-row-priorities :important))} phone]
      [:td {:class (ui/cs td-class (ui/table-row-priorities :medium))} section-name]
      [:td {:class (ui/cs td-class (ui/table-row-priorities :low))} (ui/bool-bubble active?)]
      ;;
      )]))

(ctmx/defcomponent ^:endpoint member-table-rw [{:keys [db] :as req}]
  (let [members (controller/members db nil)
        tr (i18n/tr-from-req req)
        table-headers (member-table-headers-rw tr)]
    (list
     (ui/table-row-head table-headers)
     (ui/table-body
      (rt/map-indexed member-row-rw req (map :member/member-id members))))))

(ctmx/defcomponent ^:endpoint member-table-ro [{:keys [tr db] :as req}]
  (let [members (controller/members db (sort-param req))
        table-headers (member-table-headers-ro req tr)]
    (list
     (ui/table-row-head table-headers)
     (ui/table-body
      (rt/map-indexed member-row-ro req (map :member/member-id members))))))

(defn member-add-form [{:keys [tr path sections]}]
  (let [required-label [:span  {:class "text-red-300 float-right"} " required"]]
    [:div {:class "flex flex-1 flex-col justify-between"}
     [:div {:class "divide-y divide-gray-200 px-4 sm:px-6"}
      [:div {:class "space-y-6 pt-6 pb-5"}
       [:div
        [:label {:for (path "name")  :class "block text-sm font-medium text-gray-900"} (tr [:member/name]) required-label]
        [:div {:class "mt-2 space-y-5"}
         (ui/text :id (path "name") :name "name" :value "" :class "mb-2 sm:mb-0 sm:mr-2" :required? true)]]
       [:div
        [:label {:for (path "nick") :class "block text-sm font-medium text-gray-900"} (tr [:member/nick])]
        [:div {:class "mt-2 space-y-5"}
         (ui/text :id (path "nick") :name "nick" :value "" :required? false)]]
       [:div
        [:label {:for (path "email") :class "block text-sm font-medium text-gray-900"} (tr [:Email]) required-label]
        [:div {:class "mt-2 space-y-5"}
         (ui/input :id (path "email") :name "email" :label ""  :type :email  :required? true)]]
       [:div
        [:label {:for "username" :class "block text-sm font-medium text-gray-900"} (tr [:member/username]) required-label]
        [:div {:class "mt-2 space-y-5"}
         (ui/input :name "username" :label ""  :pattern (str controller/username-regex) :title (tr [:member/username-validation]) :required? true)]]
       [:div
        [:label {:for (path "phone") :class "block text-sm font-medium text-gray-900"} (tr [:Phone]) required-label]
        [:div {:class "mt-2 space-y-5"}
         (ui/input :id (path "phone") :type :tel :name "phone" :label ""  :pattern "\\+[\\d-]+" :title "Phone number starting with +country code. Only spaces, dashes, and numbers"  :required? true)]]
       [:div
        [:label {:for "section-name" :class "block text-sm font-medium text-gray-900"} (tr [:section]) required-label]
        [:div {:class "mt-2 space-y-5"}
         (ui/section-select  :id "section-name" :class "mt-4" :sections sections  :required? true)]]
       [:div
        [:div {:class "relative flex items-start"}
         [:div {:class "flex h-5 items-center"}
          (ui/checkbox :id "create-sno-id" :checked? true)]
         [:div {:class "ml-3 text-sm"}
          [:label {:for "create-sno-id" :class "font-medium text-gray-700"}
           (tr [:member/create-sno-id])]
          [:p {:class "text-gray-500"} (tr [:member/create-sno-id-description])]]]]
       [:div
        [:div {:class "relative flex items-start"}
         [:div {:class "flex h-5 items-center"}
          (ui/checkbox :id "active?" :checked? true)]
         [:div {:class "ml-3 text-sm"}
          [:label {:for "active?" :class "font-medium text-gray-700"}
           (tr [:Active])]]]]
       [:div {:id "new-member-form-errors"}]]]]))

(defn -member-create [req]
  (try
    (let [new-member (controller/create-member! req)]
      (response/hx-redirect (url/link-member new-member)))
    (catch Exception e
      (if-let [error-msg (when (:validation/error (ex-data e))
                           (:validation/error (ex-data e)))]
        (ui/retarget-response
         "#new-member-form-errors"
         [:div {:id "new-member-form-errors" :class "text-red-500"}
          [:p [:span "⚠ "] error-msg]])
        (throw e)))))

(ctmx/defcomponent ^:endpoint member-create-endpoint [req]
  (when (util/post? req)
    (-member-create req)))

(ctmx/defcomponent ^:endpoint members-index-page [{:keys [db] :as req} ^:boolean edit?]
  (when (util/delete? req) (controller/delete-invitation req))
  (let [tr (i18n/tr-from-req req)
        comp-name (util/comp-namer #'members-index-page)
        sections (controller/sections db)
        open-invitations (controller/members-with-open-invites req)]
    [:div {:id id}
     (ui/page-header :title "Member Admin")
     (ui/slideover-panel-form {:title (tr [:member/new-member]) :id (path "slideover")
                               :form-attrs {:hx-post (util/comp-name #'member-create-endpoint) :hx-target (hash ".")}
                               :buttons (list
                                         (ui/button {:label (tr [:action/cancel]) :priority :white :attr {:_ (ui/slideover-extra-close-script (path "slideover")) :type "button"}})
                                         (ui/button {:label (tr [:action/save]) :priority :primary-orange}))}
                              (member-add-form {:tr tr :path path :sections sections}))

     [:div {:class "mt-2"}
      (when (seq open-invitations)
        [:div {:class "px-4 sm:px-6 lg:px-8 py-10"}
         (ui/divider-left (tr [:member/open-invitations]) nil)
         [:div {:class "mt-4"}
          [:table {:class "min-w-full divide-y divide-gray-300"}

           (ui/table-row-head [{:label (tr [:member/name]) :priority :medium :key :owner}
                               {:label (tr [:member/email]) :priority :medium :key :owner}
                               {:label "" :variant :action :key :action}])

           (ui/table-body
            (rt/map-indexed (fn [req idx {:member/keys [name email invite-code]}]
                              [:tr
                               [:td {:class "px-3 py-4"} name]
                               [:td {:class "px-3 py-4"} email]
                               [:td {:class "py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6"}
                                [:span {:class "flex flex-row space-x-2"}
                                 [:button {:type "submit" :class "text-red-600 hover:text-sno-orange-900 cursor-pointer"
                                           :hx-target (hash ".")
                                           :hx-delete (comp-name) :hx-vals {:invite-code invite-code}} (tr [:action/delete])]]]]) req open-invitations))]]])

      [:div {:class "px-4 sm:px-6 lg:px-8 mt-4"}
       (when (seq open-invitations) (ui/divider-left (tr [:nav/members]) nil))
       [:div {:class "flex items-center justify-end"}
        [:div {:class "mt-4 sm:mt-0 sm:ml-16 flex sm:flex-row space-x-4"}
         (ui/toggle :label (tr [:action/quick-edit]) :active? edit? :id "member-table-edit-toggle" :hx-target (hash ".") :hx-get (comp-name) :hx-vals {"edit?" (not edit?)})
         (when-not edit?
           (ui/button :label (tr [:action/add]) :priority :primary :class "" :icon icon/plus :centered? true
                      :attr {:data-flyout-trigger (hash "slideover")}))]]

       #_[:div {:class "flex items-center justify-between pb-4"}
          [:div
           [:button {:id "dropdownRadioButton", :data-dropdown-toggle "dropdownRadio", :class "inline-flex items-center text-gray-500 bg-white border border-gray-300 focus:outline-none hover:bg-gray-100 focus:ring-4 focus:ring-gray-200 font-medium rounded-lg text-sm px-3 py-1.5 dark:bg-gray-800 dark:text-white dark:border-gray-600 dark:hover:bg-gray-700 dark:hover:border-gray-600 dark:focus:ring-gray-700", :type "button"}
            [:svg {:class "w-4 h-4 mr-2 text-gray-400", :aria-hidden "true", :fill "currentColor", :viewbox "0 0 20 20", :xmlns "http://www.w3.org/2000/svg"}
             [:path {:fill-rule "evenodd", :d "M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z", :clip-rule "evenodd"}]] "Last 30 days"
            [:svg {:class "w-3 h-3 ml-2", :aria-hidden "true", :fill "none", :stroke "currentColor", :viewbox "0 0 24 24", :xmlns "http://www.w3.org/2000/svg"}
             [:path {:stroke-linecap "round", :stroke-linejoin "round", :stroke-width "2", :d "M19 9l-7 7-7-7"}]]]
         ;; "<!-- Dropdown menu -->"
           [:div {:id "dropdownRadio", :class "z-10 hidden w-48 bg-white divide-y divide-gray-100 rounded-lg shadow dark:bg-gray-700 dark:divide-gray-600", :data-popper-reference-hidden , :data-popper-escaped , :data-popper-placement "top", :style "position: absolute; inset: auto auto 0px 0px; margin: 0px; transform: translate3d(522.5px, 3847.5px, 0px);"}
            [:ul {:class "p-3 space-y-1 text-sm text-gray-700 dark:text-gray-200", :aria-labelledby "dropdownRadioButton"}
             [:li
              [:div {:class "flex items-center p-2 rounded hover:bg-gray-100 dark:hover:bg-gray-600"}
               [:input {:id "filter-radio-example-1", :type "radio",  :name "filter-radio", :class "w-4 h-4 text-blue-600 bg-gray-100 border-gray-300 focus:ring-blue-500 dark:focus:ring-blue-600 dark:ring-offset-gray-800 dark:focus:ring-offset-gray-800 focus:ring-2 dark:bg-gray-700 dark:border-gray-600"}]
               [:label {:for "filter-radio-example-1", :class "w-full ml-2 text-sm font-medium text-gray-900 rounded dark:text-gray-300"} "Last day"]]]
             [:li
              [:div {:class "flex items-center p-2 rounded hover:bg-gray-100 dark:hover:bg-gray-600"}
               [:input {:checked true , :id "filter-radio-example-2", :type "radio",  :name "filter-radio", :class "w-4 h-4 text-blue-600 bg-gray-100 border-gray-300 focus:ring-blue-500 dark:focus:ring-blue-600 dark:ring-offset-gray-800 dark:focus:ring-offset-gray-800 focus:ring-2 dark:bg-gray-700 dark:border-gray-600"}]
               [:label {:for "filter-radio-example-2", :class "w-full ml-2 text-sm font-medium text-gray-900 rounded dark:text-gray-300"} "Last 7 days"]]]
             [:li
              [:div {:class "flex items-center p-2 rounded hover:bg-gray-100 dark:hover:bg-gray-600"}
               [:input {:id "filter-radio-example-3", :type "radio",  :name "filter-radio", :class "w-4 h-4 text-blue-600 bg-gray-100 border-gray-300 focus:ring-blue-500 dark:focus:ring-blue-600 dark:ring-offset-gray-800 dark:focus:ring-offset-gray-800 focus:ring-2 dark:bg-gray-700 dark:border-gray-600"}]
               [:label {:for "filter-radio-example-3", :class "w-full ml-2 text-sm font-medium text-gray-900 rounded dark:text-gray-300"} "Last 30 days"]]]
             [:li
              [:div {:class "flex items-center p-2 rounded hover:bg-gray-100 dark:hover:bg-gray-600"}
               [:input {:id "filter-radio-example-4", :type "radio",  :name "filter-radio", :class "w-4 h-4 text-blue-600 bg-gray-100 border-gray-300 focus:ring-blue-500 dark:focus:ring-blue-600 dark:ring-offset-gray-800 dark:focus:ring-offset-gray-800 focus:ring-2 dark:bg-gray-700 dark:border-gray-600"}]
               [:label {:for "filter-radio-example-4", :class "w-full ml-2 text-sm font-medium text-gray-900 rounded dark:text-gray-300"} "Last month"]]]
             [:li
              [:div {:class "flex items-center p-2 rounded hover:bg-gray-100 dark:hover:bg-gray-600"}
               [:input {:id "filter-radio-example-5", :type "radio",  :name "filter-radio", :class "w-4 h-4 text-blue-600 bg-gray-100 border-gray-300 focus:ring-blue-500 dark:focus:ring-blue-600 dark:ring-offset-gray-800 dark:focus:ring-offset-gray-800 focus:ring-2 dark:bg-gray-700 dark:border-gray-600"}]
               [:label {:for "filter-radio-example-5", :class "w-full ml-2 text-sm font-medium text-gray-900 rounded dark:text-gray-300"} "Last year"]]]]]]
          [:label {:for "table-search", :class "sr-only"} "Search"]
          [:div {:class "relative"}
           [:div {:class "absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none"}
            [:svg {:class "w-5 h-5 text-gray-500 dark:text-gray-400", :aria-hidden "true", :fill "currentColor", :viewbox "0 0 20 20", :xmlns "http://www.w3.org/2000/svg"}
             [:path {:fill-rule "evenodd", :d "M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z", :clip-rule "evenodd"}]]]
           [:input {:type "text", :id "table-search", :class "block p-2 pl-10 text-sm text-gray-900 border border-gray-300 rounded-lg w-80 bg-gray-50 focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white dark:focus:ring-blue-500 dark:focus:border-blue-500", :placeholder "Search for items"}]]]

       [:div {:class "mt-4"}
        [:table {:class "min-w-full divide-y divide-gray-300"}
         (if edit?
           (member-table-rw req)
           (member-table-ro req))]]]]]))

(defn invite-accept-form [req {:keys [member invite-code]} error-msg]
  (let [tr (i18n/tr-from-req req)]
    (layout/centered-content req
                             [:form {:class "space-y-6" :action "/invite-accept" :method "POST"}
                              [:input {:type :hidden :name "invite-code" :value invite-code}]
                              [:h2 {:class "font-medium text-2xl"} (tr [:account/create-sno-id-title])]
                              [:p {:class "text-lg text-gray-700"} (tr [:account/create-sno-id-subtitle])]
                              [:div
                               [:label {:for "email" :class "block text-sm font-medium text-gray-700"} (tr [:member/email])]
                               [:div {:class "mt-1"}
                                [:input {:disabled true :value (:member/email member) :id "email" :name "email" :type "email" :autocomplete "email" :required true :class "block w-full appearance-none rounded-md border border-gray-300 px-3 py-2 placeholder-gray-400 shadow-sm focus:border-sno-orange-500 focus:outline-none focus:ring-sno-orange-500 sm:text-sm  disabled:cursor-not-allowed disabled:border-gray-200 disabled:bg-gray-50 disabled:text-gray-500"}]]]
                              [:div
                               [:label {:for "password" :class "block text-sm font-medium text-gray-700"} (tr [:account/password])]
                               [:div {:class "mt-1"}
                                [:input {:id "password" :name "password" :type "password" :autocomplete "new-password" :required true :class "block w-full appearance-none rounded-md border border-gray-300 px-3 py-2 placeholder-gray-400 shadow-sm focus:border-sno-orange-500 focus:outline-none focus:ring-sno-orange-500 sm:text-sm"}]]
                               [:p {:class "text-xs text-gray-700"} (tr [:account/password-instructions])]]
                              [:div
                               [:label {:for "password-confirm" :class "block text-sm font-medium text-gray-700"} (tr [:account/password-confirm])]
                               [:div {:class "mt-1"}
                                [:input {:id "password-confirm" :name "password-confirm" :autocomplete "new-password" :type "password" :required true :class "block w-full appearance-none rounded-md border border-gray-300 px-3 py-2 placeholder-gray-400 shadow-sm focus:border-sno-orange-500 focus:outline-none focus:ring-sno-orange-500 sm:text-sm"}]]
                               [:p {:class "text-xs text-gray-700"} (tr [:account/password-confirm-instructions])]]
                              (when error-msg
                                [:div
                                 [:p {:class "text-medium text-red-500"} error-msg]])
                              [:div
                               (ui/button :centered? true :priority :primary :label (tr [:account/create-account]) :class "w-full")]])))

(defn invite-invalid [req]
  (let [tr (i18n/tr-from-req req)]
    (layout/centered-content req
                             [:div
                              [:div (tr [:email/invite-expired])]])))

(defn invite-accept [req]
  (let [tr (i18n/tr-from-req req)
        invite-data (controller/load-invite req)]
    (if invite-data
      (invite-accept-form req invite-data nil)
      (invite-invalid req))))

(defn invite-accept-post [req]
  (try

    (let [member (controller/setup-account req)
          tr (i18n/tr-from-req req)]
      (layout/centered-content req
                               [:div {:class "space-y-6"}
                                [:h2 {:class "font-medium text-2xl"} (tr [:account/account-created-title])]
                                [:p {:class "text-lg text-gray-700"} (tr [:account/account-created-subtitle])]
                                [:div
                                 (ui/link-button :priority :primary :centered? true
                                                 :class "w-full"
                                                 :label (tr [:login]) :attr {:href (str "/login?login_hint=" (util/url-encode (:member/email member)))})]]))

    (catch Throwable e
      (let [reason (-> e ex-data :reason)]
        (condp = reason
          nil (throw e)
          :code-expired (invite-invalid req)
          (invite-accept-form req (->  e ex-data :invite-data) ((i18n/tr-from-req req) [reason])))))))
