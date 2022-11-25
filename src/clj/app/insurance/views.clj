(ns app.insurance.views
  (:require
   [app.util :as util]
   [app.urls :as url]
   [app.ui :as ui]
   [app.insurance.controller :as controller]
   [ctmx.response :as response]
   [app.icons :as icon]
   [ctmx.core :as ctmx]
   [tick.core :as t]
   [ctmx.rt :as rt]
   [medley.core :as m]
   [app.queries :as q]
   [app.i18n :as i18n]))

(defn instrument-row [{:instrument/keys [name instrument-id category owner]}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"]
    [:a {:href  (url/link-instrument instrument-id) , :class "block hover:bg-gray-50"}
     [:div {:class "px-4 py-4 sm:px-6"}
      [:div {:class "flex items-center justify-between"}
       [:p {:class "truncate text-sm font-medium text-indigo-600"}
        name]]

      [:div {:class "mt-2 sm:flex sm:justify-between"}
       [:div {:class "flex"}
        [:p {:class "flex items-center text-sm text-gray-500"}
         (icon/user {:class style-icon})
         (:member/name owner)]
        [:p {:class "mt-2 flex items-center text-sm text-gray-500 mt-0 ml-6"}
         (icon/trumpet {:class style-icon})
         (:instrument.category/name category)]]

       [:div {:class "mt-2 flex items-center text-sm text-gray-500 sm:mt-0"}
                                        ;(icon/calendar {:class style-icon})
                                        ;[:p "Last Played "]
        ]]]]))
(defn policy-row [tr {:insurance.policy/keys [policy-id name effective-at effective-until]}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"]
    [:a {:href  (url/link-policy policy-id) , :class "block hover:bg-gray-50"}
     [:div {:class "px-4 py-4 sm:px-6"}
      [:div {:class "flex items-center justify-between"}
       [:p {:class "truncate text-sm font-medium text-indigo-600"}
        name]]

      [:div {:class "mt-2 sm:flex sm:justify-between"}
       [:div {:class "flex"}
        [:p {:class "flex items-center text-sm text-gray-500"}
         (icon/calendar {:class style-icon})
         (ui/datetime effective-at) " - " (ui/datetime effective-until)]]

       [:div {:class "mt-2 flex items-center text-sm text-gray-500 sm:mt-0"}
                                        ;(icon/calendar {:class style-icon})
                                        ;[:p "Last Played "]
        ]
       [:div {:class "ml-2 flex flex-shrink-0"}
        [:form {:hx-post "/insurance-policy-duplicate/insurance-policy-duplicate"}
         [:input {:type :hidden :name "policy-id" :value policy-id}]
         (ui/button :attr {:href "#"} :label (tr [:action/duplicate])  :priority :white-rounded :size :small)]]]]]))

(ctmx/defcomponent ^:endpoint insurance-policy-duplicate [{:keys [db] :as req}]
  (let [new-policy-id (controller/duplicate-policy req)]
    ;; (response/hx-redirect (url/link-policy new-policy-id))
    nil))

(ctmx/defcomponent ^:endpoint policy-edit [{:keys [db] :as req}]
  (let [policy-id (-> req :path-params :policy-id parse-uuid)
        {:insurance.policy/keys [name policy-id]} (controller/retrieve-policy db policy-id)]

    [:div {:hx-get (str (url/link-policy policy-id "/policy-name")) :hx-target "this" :class "cursor-pointer"}
     [:h1 {:class "text-2xl font-bold text-gray-900"}]]))

(defn coverage-type-row [{:insurance.coverage.type/keys [name type-id premium-factor]}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"]
    [:div {:class "block hover:bg-gray-50"}
     [:div {:class "px-4 py-4 sm:px-6"}
      [:div {:class "flex items-center justify-between"}
       [:p {:class "truncate text-sm font-medium text-indigo-600"}
        name]]

      [:div {:class "mt-2 sm:flex sm:justify-between"}
       [:div {:class "flex"}
        [:p {:class "flex items-center text-sm text-gray-500"}
         premium-factor]]]]]))

(ctmx/defcomponent ^:endpoint insurance-detail-page-header [{:keys [db] :as req} ^:boolean edit?]
  (ctmx/with-req req
    (let [policy-id (-> req :path-params :policy-id)
          tr (i18n/tr-from-req req)
          comp-name (util/comp-namer #'insurance-detail-page-header)
          {:insurance.policy/keys [name effective-at effective-until premium-factor] :as policy} (controller/retrieve-policy db (parse-uuid policy-id))
          result (and post? (controller/update-policy! req (parse-uuid policy-id)))]
      (if (:policy result)
        (response/hx-redirect (url/link-policy policy-id))
        [(if edit? :form :div)
         (if edit?
           {:id id :hx-post (comp-name) :hx-target (hash ".")}
           {:id id})

         (ui/panel {:title (if edit?
                             (ui/text :label "Name" :name (path "name") :value name)
                             name)
                    :buttons  (if edit?
                                (list
                                 (ui/button :label "Cancel"
                                            :priority :white
                                            :centered? true
                                            :attr {:hx-get (comp-name) :hx-target (hash ".") :hx-vals {:edit? false}})
                                 (ui/button :label "Save"
                                            :priority :primary
                                            :centered? true))
                                (ui/button :label "Edit"
                                           :priority :white
                                           :centered? true
                                           :attr {:hx-get (comp-name) :hx-target (hash ".") :hx-vals {:edit? true}}))}
                   (ui/dl
                    (ui/dl-item (tr [:insurance/effective-at])
                                (if edit?
                                  [:input {:type "date" :name "effective-at" :id "effective-at"
                                           :value (t/date effective-at)
                                           :required true
                                           :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:max-w-xs sm:text-sm"}]
                                  (ui/datetime effective-at)))
                    (ui/dl-item (tr [:insurance/effective-until])
                                (if edit?
                                  [:input {:type "date" :name "effective-until" :id "effective-until"
                                           :value (t/date effective-until)
                                           :required true
                                           :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:max-w-xs sm:text-sm"}]
                                  (ui/datetime effective-until)))
                    (ui/dl-item (tr [:insurance/premium-base-factor])
                                (if edit?
                                  [:input {:type "number" :name "base-factor" :id "base-factor"
                                           :value premium-factor
                                           :step "0.00000001" :min "0" :max "2.0"
                                           :required true
                                           :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:max-w-xs sm:text-sm"}]
                                  premium-factor))))]))))

(ctmx/defcomponent  insurance-coverage-types-item-ro [{:keys [db] :as req} idx {:insurance.coverage.type/keys [name premium-factor type-id]}]
  (ui/dl-item name premium-factor))

(ctmx/defcomponent insurance-coverage-types-item-rw [{:keys [db] :as req} idx {:insurance.coverage.type/keys [name premium-factor type-id]}]
  (let [tr (i18n/tr-from-req req)]
    (ui/dl-item
     (ui/text :label "Coverage Name" :name (path "name") :value name)
     [:div {:class "mt-2"}
      (ui/factor-input :label  (tr [:insurance/premium-factor]) :name (path "premium-factor") :value premium-factor)
      (let [delete-id (path "delete")]
        [:div {:class "mt-2 relative flex items-start"}
         [:input {:type "hidden" :value type-id :name (path "type-id")}]
         [:div {:class "flex h-5 items-center"}
          [:input {:name delete-id :id delete-id :type "checkbox" :class "h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"}]]
         [:div {:class "ml-3 text-sm"}
          [:label {:for delete-id :class "font-medium text-gray-700"} (tr [:action/delete])]]])])))

(ctmx/defcomponent ^:endpoint insurance-coverage-types [{:keys [db] :as req} ^:boolean edit? ^:boolean add?]
  (let [comp-name (util/comp-namer #'insurance-coverage-types)
        post? (util/post? req)
        put? (util/put? req)
        policy-id (-> req :path-params :policy-id parse-uuid)
        tr (i18n/tr-from-req req)
        policy (cond
                 put?
                 (:policy (controller/create-coverage-type! req policy-id))
                 post?
                 (:policy
                  (controller/update-coverage-types! req policy-id))
                 :else
                 (controller/retrieve-policy db policy-id))
        coverage-types (:insurance.policy/coverage-types policy)

        body-result
        [:div {:id id}
         (ui/panel {:title (tr [:insurance/coverage-types])
                    :buttons (cond
                               edit?
                               (list
                                (ui/button :label "Save" :priority :primary  :centered? true :attr {:form (path "editform")})
                                (ui/button :label "Cancel" :priority :white :centered? true
                                           :attr {:hx-get (comp-name) :hx-vals {:edit? false} :hx-target (hash ".")}))
                               add?
                               (ui/button :label "Cancel" :priority :white :centered? true
                                          :attr {:hx-get (comp-name) :hx-vals {:add? false} :hx-target (hash ".")})
                               (empty? coverage-types) nil
                               :else
                               (list
                                (ui/button :label "Edit" :priority :white :centered? true
                                           :attr {:hx-get (comp-name) :hx-vals {:edit? true} :hx-target (hash ".")})
                                (ui/button :label "Add" :priority :white :centered? true :icon icon/plus
                                           :attr {:hx-get (comp-name) :hx-vals {:add? true}  :hx-target (hash ".")})))}
                   (when (seq coverage-types)
                     [:form {:hx-post (comp-name) :hx-target (hash ".") :id (path "editform")}
                      (ui/dl (rt/map-indexed (if edit? insurance-coverage-types-item-rw insurance-coverage-types-item-ro) req coverage-types))])
                   (when add?
                     [:form {:hx-put (comp-name) :hx-target (hash ".")}
                      (ui/dl
                       (ui/dl-item (ui/text :label (tr [:insurance/coverage-name])  :name (path "coverage-name") :placeholder "Over Night")
                                   [:div {:class "mt-2"}
                                    (ui/factor-input :label (tr [:insurance/premium-factor]) :name (path "premium-factor") :value "0.2")
                                    (ui/button :label "Add" :priority :primary :class "mt-2")]))])
                   (when (and (not add?) (empty? coverage-types))
                     [:div {:class "text-gray-600 flex flex-col items-center"}
                      [:div
                       "You need to create a coverage type."]
                      [:div
                       (ui/button :label (tr [:action/create]) :priority :white :icon icon/plus :size :small
                                  :attr {:hx-get (comp-name) :hx-vals {:add? true}  :hx-target (hash ".")})]]))]]

    (if post?
      (ui/trigger-response "refreshCoverages" body-result)
      body-result)))

(defn unused-categories
  "Given a list of all instrument categories and an insurance policy, this function returns the list of categories that are not defined in the insurance policy's category factorsl"
  [all-categories policy]
  (let [used-category-ids (map (fn [{:insurance.category.factor/keys [category]}]
                                 (:instrument.category/category-id category)) (:insurance.policy/category-factors policy))]
    (remove
     #(some (fn [used-cat-id]
              (= (:instrument.category/category-id %) used-cat-id))
            used-category-ids)
     all-categories)))

(ctmx/defcomponent  insurance-category-factors-item-ro [{:keys [db] :as req} idx {:insurance.category.factor/keys [category factor category-factor-id]}]
  (ui/dl-item (:instrument.category/name category) factor))

(ctmx/defcomponent insurance-category-factors-item-rw [{:keys [db] :as req} idx {:insurance.category.factor/keys [category factor category-factor-id]}]
  (let [tr (i18n/tr-from-req req)]
    (ui/dl-item
     (:instrument.category/name category)
     [:div {:class "mt-2"}
      (ui/factor-input :label (tr [:insurance/premium-factor]) :name (path "premium-factor") :value factor)
      (let [delete-id (path "delete")]
        [:div {:class "mt-2 relative flex items-start"}
         [:input {:type "hidden" :value category-factor-id :name (path "category-id")}]
         [:div {:class "flex h-5 items-center"}
          [:input {:name delete-id :id delete-id :type "checkbox" :class "h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"}]]
         [:div {:class "ml-3 text-sm"}
          [:label {:for delete-id :class "font-medium text-gray-700"} (tr [:action/delete])]]])])))

(ctmx/defcomponent ^:endpoint insurance-category-factors [{:keys [db] :as req} ^:boolean edit? ^:boolean add?]
  (let [policy-id (-> req :path-params :policy-id parse-uuid)
        comp-name (util/comp-namer #'insurance-category-factors)
        post? (util/post? req)
        put? (util/put? req)
        policy (cond
                 put?
                 (:policy (controller/create-category-factor! req policy-id))
                 post?
                 (:policy
                  (controller/update-category-factors! req policy-id))
                 :else
                 (controller/retrieve-policy db policy-id))
        tr (i18n/tr-from-req req)
        category-factors (:insurance.policy/category-factors policy)
        all-categories (controller/instrument-categories db)
        not-used-instrument-categories (unused-categories all-categories policy)
        no-more-categories? (empty? not-used-instrument-categories)
        body-result

        [:div {:id id}
         (ui/panel {:title (tr [:insurance/category-factors])
                    :buttons (cond
                               edit?
                               (list
                                (ui/button :label "Save" :priority :primary :centered? true
                                           :attr {:form (path "editform")})
                                (ui/button :label "Cancel" :priority :white :centered? true
                                           :attr {:hx-get (comp-name) :hx-vals {:edit? false} :hx-target (hash ".")}))
                               add?
                               (list
                                (ui/button :label "Cancel" :priority :white :centered? true
                                           :attr {:hx-get (comp-name) :hx-vals {:add? false} :hx-target (hash ".")}))
                               :else
                               (list
                                (ui/button :label "Edit" :priority :white :centered? true
                                           :attr {:hx-get (comp-name) :hx-vals {:edit? true} :hx-target (hash ".")})
                                (when-not no-more-categories?
                                  (ui/button :label "Add" :priority :white :icon icon/plus :centered? true
                                             :attr {:hx-get (comp-name) :hx-vals {:add? true} :hx-target (hash ".")}))))}

                   (when (seq category-factors)
                     [:form {:hx-post (comp-name) :hx-target (hash ".") :id (path "editform")}
                      (ui/dl (rt/map-indexed (if edit? insurance-category-factors-item-rw  insurance-category-factors-item-ro) req category-factors))])
                   (when add?
                     [:form {:hx-put (comp-name) :hx-target (hash ".")}
                      (ui/dl
                       (ui/dl-item
                        (ui/instrument-category-select :variant :inline :id (path "category-id") :categories not-used-instrument-categories)
                        [:div {:class "mt-2"}
                         (ui/factor-input :label (tr [:insurance/premium-factor]) :name (path "premium-factor") :value "0.2")
                         (ui/button :label "Add" :priority :primary :class "mt-2")]))]))]]

    (if post?
      (ui/trigger-response "refreshCoverages" body-result)
      body-result)))

(defn remove-covered-instruments [all-instruments instrument-coverages]
  (let [covered-instruments (map :instrument.coverage/instrument instrument-coverages)]
    (remove
     #(some (fn [covered-i]
              (= (:instrument/instrument-id %) (:instrument/instrument-id covered-i)))
            covered-instruments)
     all-instruments)))

(defn coverage-table-headers [{:insurance.policy/keys [coverage-types]}]
  (concat
   [{:label "Name" :priority :normal :key :name
     :render-fn (fn [_ instrument]
                  (list
                   (:name instrument)
                   [:dl {:class "font-normal sm:hidden"}
                    [:dt {:class "sr-only"} (:name instrument)]
                    [:dd {:class "mt-1 truncate text-gray-700"} (:owner instrument)]]))}

    {:label "Owner" :priority :medium :key :owner}
    {:label "Make" :priority :medium :key :value}
    {:label "Model" :priority :medium :key :value}
    {:label "Build Year" :priority :medium :key :value}
    {:label "Serial Number" :priority :medium :key :value}
    {:label "Band/Private" :priority :medium :key :value}
    {:label "Value" :priority :medium :key :value :variant :number}
    ;;
    ]
   (map (fn [ct]
          {:label (:insurance.coverage.type/name ct)
           :variant :number
           :key (:insurance.coverage.type/name ct)
           :priority :normal}) coverage-types)
   [{:label "Total Cost" :priority :medium :key :value :variant :number}]
   [{:label "Edit" :variant :action :key :action}]))

(defn get-coverage [policy coverage-id]
  (->> policy
       :insurance.policy/covered-instruments
       (m/find-first #(= coverage-id (:instrument.coverage/coverage-id %)))))

(defn shared-static-columns [coverage]
  (let [{:instrument/keys [name owner make model build-year serial-number]} (:instrument.coverage/instrument coverage)]
    (list
     [:td {:class (ui/cs "w-full max-w-0 py-4 pl-4 pr-3 sm:w-auto   sm:max-w-none sm:pl-6")}
      name]
     [:td {:class "px-3 py-4"}
      (-> owner :member/name)]
     [:td {:class "px-3 py-4"} make]
     [:td {:class "px-3 py-4"} model]
     [:td {:class "px-3 py-4"} build-year]
     [:td {:class "px-3 py-4"} serial-number]
      ;;
     )))

(defn shared-static-columns-end [coverage]
  (let [{:instrument/keys [name owner make model build-year serial-number]} (:instrument.coverage/instrument coverage)]
    (list
     [:td {:class "px-3 py-4 text-right"}
      (ui/money (:instrument.coverage/cost coverage) :EUR)]
      ;;
     )))

(ctmx/defcomponent ^:endpoint coverage-table-row-rw [{:keys [db] :as req} idx coverage-id]
  (let [policy (:policy req)
        post? (util/post? req)
        delete? (util/delete? req)
        coverage-types (:insurance.policy/coverage-types policy)
        coverage (cond post?
                       (:coverage (controller/toggle-instrument-coverage-type! req coverage-id (-> req :params :type-id)))
                       delete?
                       (:policy (controller/remove-instrument-coverage! req coverage-id))

                       :else
                       (controller/retrieve-coverage db coverage-id))]
    (if (and delete? coverage)
      ""
      (into [] (concat
                [:tr {:id id}
                 (shared-static-columns coverage)
                 [:td {:class "px-3 py-4"}
                  (if (:instrument.coverage/private? coverage)
                    "private"
                    "band")]
                 [:td {:class "px-3 py-4 text-right"}
                  (ui/money (:instrument.coverage/value coverage) :EUR)]]
                (map-indexed  (fn [type-idx {:insurance.coverage.type/keys [type-id name]}]
                                [:td {:class "px-3 py-4" :hx-include (str "#coverage" idx "type" type-idx  " input") :id (str "coverage" idx "type" type-idx)}
                                 [:input {:type "hidden" :name "coverage-id" :value (:instrument.coverage/coverage-id coverage)}]
                                 [:input {:type "hidden" :name "type-id" :value type-id}]
                                 (let [has? (controller/get-coverage-type-from-coverage coverage type-id)
                                       icon (if has? icon/xmark icon/plus)
                                       class (if has? "text-red-500" "text-green-500")]
                                   (ui/button :icon icon :size :small
                                              :class class
                                              :attr {:hx-post "coverage-table-row-rw" :hx-target (hash ".")}))])
                              coverage-types)
                (shared-static-columns-end coverage)
                (list
                 [:td {:class "py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6"}
                  [:form {:hx-delete "coverage-table-row-rw" :hx-target (hash ".") :hx-confirm "Are you sure?"}
                   [:input {:type "hidden" :name "coverage-id" :value (:instrument.coverage/coverage-id coverage)}]
                   [:span {:class "flex flex-row space-x-2"}
                    [:button {:type "submit" :class "text-red-600 hover:text-indigo-900 cursor-pointer"} "Delete"]]]]))))))

(ctmx/defcomponent ^:endpoint coverage-table-row-ro [{:keys [db] :as req} idx coverage-id]
  (let [policy (:policy req)
        coverage-types (:insurance.policy/coverage-types policy)
        coverage (controller/retrieve-coverage db coverage-id)
        coverage (controller/update-total-coverage-price policy coverage)]
    (into [] (concat
              [:tr {:id id}
               (shared-static-columns coverage)

               [:td {:class "px-3 py-4"}
                (if (:instrument.coverage/private? coverage)
                  "private"
                  "band")]
               [:td {:class "px-3 py-4 text-right"}
                (ui/money (:instrument.coverage/value coverage) :EUR)]]
              (mapv (fn [{:insurance.coverage.type/keys [type-id]}]
                      [:td {:class "px-3 py-4 text-right"}
                       (if-let [coverage-type (controller/get-coverage-type-from-coverage coverage type-id)]
                         [:div (ui/money (:insurance.coverage.type/cost coverage-type) :EUR)]
                         (icon/xmark {:class "w-5 h-5 inline"}))])

                    coverage-types)

              (shared-static-columns-end coverage)
              (list
               [:td {:class "py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6"}
                [:span {:class "flex flex-row space-x-2"}
                 [:button {:type "submit" :class "text-white hover:text-indigo-900 cursor-pointer"} "Delete"]]])))))

(ctmx/defcomponent ^:endpoint insurance-instrument-coverage-table-rw [{:keys [db] :as req}]
  (let [policy (:policy req)
        instrument-coverages (:insurance.policy/covered-instruments policy)
        table-headers (coverage-table-headers policy)]

    (list
     (ui/table-row-head table-headers)
     (ui/table-body
      (rt/map-indexed coverage-table-row-rw req (map :instrument.coverage/coverage-id instrument-coverages))))))

(ctmx/defcomponent ^:endpoint insurance-instrument-coverage-table-ro [{:keys [db] :as req}]
  (let [policy (:policy req)
        instrument-coverages (:insurance.policy/covered-instruments policy)
        table-headers (coverage-table-headers policy)]

    (list
     (ui/table-row-head table-headers)
     (ui/table-body
      (rt/map-indexed coverage-table-row-ro req (map :instrument.coverage/coverage-id instrument-coverages))))))

(ctmx/defcomponent ^:endpoint insurance-instrument-coverage [{:keys [db] :as req} ^:boolean edit? ^:boolean add?]
  (let [policy-id (-> req :policy :insurance.policy/policy-id)
        comp-name (util/comp-namer #'insurance-instrument-coverage)
        post? (util/post? req)
        put? (util/put? req)
        tr (i18n/tr-from-req req)
        policy (cond
                 put?
                 (:policy (controller/create-instrument-coverage! req policy-id))
                 post?
                 (:policy
                  (controller/update-coverage-types! req policy-id))
                 :else
                 (:policy req))
        instrument-coverages (:insurance.policy/covered-instruments policy)
        non-covered-instruments (remove-covered-instruments (controller/instruments db) instrument-coverages)]
    (if (and put? policy)
      ctmx.response/hx-refresh
      [:div {:id id :hx-trigger "refreshCoverages from:body" :hx-get (comp-name)}
       [:div {:class "mt-2"}
        [:div {:class "px-4 sm:px-6 lg:px-8"}
         [:div {:class "sm:flex sm:items-center"}
          [:div {:class "sm:flex-auto"}
           [:h1 {:class "text-2xl font-semibold text-gray-900"} (tr [:insurance/covered-instruments])]
           [:p {:class "mt-2 text-sm text-gray-700"} ""]]
          [:div {:class "mt-4 sm:mt-0 sm:ml-16 flex sm:flex-row sm:space-x-4"}
           (ui/toggle :label "Edit" :active? edit? :id "instrument-table-edit-toggle" :hx-target (hash ".") :hx-get (comp-name) :hx-vals {:edit? (not edit?)})
           (ui/button :label "Add" :priority :white :class "" :icon icon/plus :centered? true
                      :attr {:hx-target (hash ".") :hx-get (comp-name) :hx-vals {:add? true}})]]
         (when add?
           [:div {:class "w-96"}
            [:form
             (ui/instrument-select :id (path "instrument-id") :instruments non-covered-instruments :variant :inline)
             (ui/money-input :id (path "value") :label "Value" :required? true)
             (ui/checkbox :id (path "private?") :label "Private?")

             (ui/button :label "Cancel" :priority :white :icon icon/plus :attr {:hx-target (hash ".") :hx-get (comp-name) :hx-vals {:add? false}})
             (ui/button :label "Add" :priority :primary :attr {:hx-target (hash ".") :hx-put (comp-name)})]])
         [:div {:class "-mx-4 mt-8 overflow-hidden shadow ring-1 ring-black ring-opacity-5 sm:-mx-6 md:mx-0 md:rounded-lg"}
          [:table {:class "min-w-full divide-y divide-gray-300"}
           (if edit?
             (insurance-instrument-coverage-table-rw req)
             (insurance-instrument-coverage-table-ro req))]]]

        [:div {:class "mx-auto mt-6 max-w-5xl px-4 sm:px-6 lg:px-8"}
         (when (and (not add?) (empty? instrument-coverages))
           [:div
            [:div
             "You should add a coverage to an instrument"]
            [:div
             (ui/button :label (tr [:insurance/instrument-coverage]) :priority :primary :icon icon/plus :attr {:hx-get (comp-name) :hx-vals {:add? true} :hx-target (hash ".")})]])]]])))

(ctmx/defcomponent insurance-detail-page [{:keys [db] :as req}]
  [:div
   (insurance-detail-page-header req false)
   (insurance-coverage-types req false false)
   (insurance-category-factors req false false)
   (insurance-instrument-coverage req false false)])

(ctmx/defcomponent ^:endpoint insurance-create-page [{:keys [db] :as req}]
  (let [this-year (t/year (t/now))
        next-year (t/year (t/>> (t/now) (t/new-duration 365 :days)))
        tr (i18n/tr-from-req req)
        result (and (util/post? req) (controller/create-policy! req))]
    (if (:policy result)
      (response/hx-redirect (url/link-policy (:policy result)))

      [:div
       (ui/page-header :title  (tr [:insurance/create-title])
                       :subtitle (tr [:insurance/create-subtitle]))
       (ui/panel {}
                 [:form {:hx-post (path ".") :class "space-y-8 divide-y divide-gray-200"}
                  [:div {:class "space-y-8 divide-y divide-gray-200 sm:space-y-5"}
                   [:div {:class "space-y-6 sm:space-y-5"}

                    [:div {:class "space-y-6 sm:space-y-5"}
                     [:div {:class "sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:border-t sm:border-gray-200 sm:pt-5"}
                      [:label {:for "first-name" :class "block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"}
                       (tr [:insurance/name])]
                      [:div {:class "mt-1 sm:col-span-2 sm:mt-0"}
                       [:input {:type "text" :name "name" :id "name"
                                :value (str "Band Instruments " this-year " - " next-year)
                                :required true
                                :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:max-w-xs sm:text-sm"}]]]
                     [:div {:class "sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:border-t sm:border-gray-200 sm:pt-5"}
                      [:label {:for "first-name" :class "block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"}
                       (tr [:insurance/effective-at])]
                      [:div {:class "mt-1 sm:col-span-2 sm:mt-0"}
                       [:input {:type "date" :name "effective-at" :id "effective-at"
                                :value (str this-year "-05-01")
                                :required true
                                :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:max-w-xs sm:text-sm"}]]]
                     [:div {:class "sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:border-t sm:border-gray-200 sm:pt-5"}
                      [:label {:for "first-name" :class "block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"}
                       (tr [:insurance/effective-until])]
                      [:div {:class "mt-1 sm:col-span-2 sm:mt-0"}
                       [:input {:type "date" :name "effective-until" :id "effective-until"
                                :value (str next-year "-04-30")
                                :required true
                                :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:max-w-xs sm:text-sm"}]]]
                     [:div {:class "sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:border-t sm:border-gray-200 sm:pt-5"}
                      [:label {:for "first-name" :class "block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"}
                       (tr [:insurance/premium-base-factor])]
                      [:div {:class "mt-1 sm:col-span-2 sm:mt-0"}
                       (ui/factor-input :name "base-factor" :value (* (bigdec 1.07) (bigdec 0.00447)))]]]]]
                  [:div {:class "pt-5"}
                   [:div {:class "flex justify-end"}
                    [:a {:href "/insurance" :class "rounded-md border border-gray-300 bg-white py-2 px-4 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}
                     (tr [:action/cancel])]
                    [:button {:type "submit" :class "ml-3 inline-flex justify-center rounded-md border border-transparent bg-indigo-600 py-2 px-4 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}
                     (tr [:action/create])]]]])])))

(ctmx/defcomponent ^:endpoint instrument-create-page [{:keys [db] :as req}]
  (let [result (and (util/post? req) (controller/create-instrument! req))
        tr (i18n/tr-from-req req)]
    (if-let [instrument  (:instrument result)]
      (response/hx-redirect (url/link-instrument instrument))
      [:div
       (ui/page-header :title (tr [:instrument/create-title])
                       :subtitle (tr [:instrument/create-subtitle]))
       (ui/panel {}
                 [:form {:hx-post (path ".") :class "space-y-8 divide-y divide-gray-200"}
                  [:div {:class "space-y-8 divide-y divide-gray-200 sm:space-y-5"}
                   [:div {:class "space-y-6 sm:space-y-5"}
                    [:div
                     [:h3 {:class "text-lg font-medium leading-6 text-gray-900"}]
                     [:p {:class "mt-1 max-w-2xl text-sm text-gray-500"}]]
                    [:div {:class "space-y-6 sm:space-y-5"}
                     (ui/member-select :label (tr [:instrument/owner])  :id (path "owner-gigo-key") :members (q/members-for-select db) :variant :left)
                     (ui/instrument-category-select :variant :left :id (path "category-id") :categories (controller/instrument-categories db))
                     (ui/text-left :label (tr [:instrument/name]) :id (path "name"))
                     (ui/text-left :label (tr [:instrument/make]) :id (path "make"))
                     (ui/text-left :label (tr [:instrument/model]) :id (path "model"))
                     (ui/text-left :label (tr [:instrument/serial-number]) :id (path "serial-number") :required? false)
                     (ui/text-left :label (tr [:instrument/build-year]) :id (path "build-year") :required? false)]]]

                  [:div {:class "pt-5"}
                   [:div {:class "flex justify-end"}
                    [:a {:href "/insurance" :class "rounded-md border border-gray-300 bg-white py-2 px-4 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}
                     (tr [:action/cancel])]
                    [:button {:type "submit" :class "ml-3 inline-flex justify-center rounded-md border border-transparent bg-indigo-600 py-2 px-4 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}
                     (tr [:action/create])]]]])])))

(ctmx/defcomponent ^:endpoint instrument-detail-page [{:keys [db] :as req} ^:boolean edit?]
  (let [post? (util/post? req)
        comp-name (util/comp-namer #'instrument-detail-page)
        tr (i18n/tr-from-req req)
        instrument-id  (parse-uuid  (-> req :path-params :instrument-id))
        {:instrument/keys [name make build-year model serial-number] :as instrument} (controller/retrieve-instrument db instrument-id)
        result (and post? (controller/update-instrument! req instrument-id))]
    (if (:instrument result)
      (response/hx-redirect (url/link-instrument instrument-id))
      [(if edit? :form :div)
       (if edit?
         {:hx-post (comp-name) :hx-target (hash ".") :id id}
         {:hx-target "this" :id id})
       (ui/page-header :title (if edit?
                                (ui/text :label "Name" :name (path "name") :value name)
                                name)
                       :buttons (if edit?
                                  (list
                                   (ui/button :label "Cancel"
                                              :priority :white
                                              :centered? true
                                              :attr {:hx-get (comp-name) :hx-target (hash ".") :hx-vals {:edit? false}})
                                   (ui/button :label "Save"
                                              :priority :primary
                                              :centered? true))
                                  (ui/button :label "Edit"
                                             :priority :white
                                             :centered? true
                                             :attr {:hx-get (comp-name) :hx-target (hash ".") :hx-vals {:edit? true}})))

       [:div {:class "mt-6 sm:px-6 lg:px-8"}
        [:div {:class "mx-auto mt-6 max-w-5xl px-4 py-4 sm:px-6 lg:px-8 bg-white rounded-md"}
         [:dl {:class "grid grid-cols-1 gap-x-4 gap-y-8 sm:grid-cols-3"}
          [:div {:class "sm:col-span-1"}
           [:dt {:class "text-sm font-medium text-gray-500"} (tr [:instrument/owner])]
           [:dd {:class "mt-1 text-sm text-gray-900"}
            (if edit?
              (ui/member-select :variant :inline-no-label :id (path "owner-gigo-key") :members (q/members-for-select db))
              (-> instrument :instrument/owner :member/name))]]
          [:div {:class "sm:col-span-1"}
           [:dt {:class "text-sm font-medium text-gray-500"} (tr [:instrument/category])]
           [:dd {:class "mt-1 text-sm text-gray-900"}
            (if edit?
              (ui/instrument-category-select :variant :inline-no-label :id (path "category-id") :categories (controller/instrument-categories db))
              (-> instrument :instrument/category :instrument.category/name))]]
          [:div {:class "sm:col-span-1"}
           [:dt {:class "text-sm font-medium text-gray-500"} (tr [:instrument/make])]
           [:dd {:class "mt-1 text-sm text-gray-900"}
            (if edit?
              (ui/text :label "" :name (path "make") :value make)
              make)]]
          [:div {:class "sm:col-span-1"}
           [:dt {:class "text-sm font-medium text-gray-500"} (tr [:instrument/model])]
           [:dd {:class "mt-1 text-sm text-gray-900"}
            (if edit?
              (ui/text :label "" :name (path "model") :value model :required? false)
              model)]]
          [:div {:class "sm:col-span-1"}
           [:dt {:class "text-sm font-medium text-gray-500"} (tr [:instrument/build-year])]
           [:dd {:class "mt-1 text-sm text-gray-900"}
            (if edit?
              (ui/text :label "" :name (path "build-year") :value build-year :required? false)
              build-year)]]
          [:div {:class "sm:col-span-1"}
           [:dt {:class "text-sm font-medium text-gray-500"} (tr [:instrument/serial-number])]
           [:dd {:class "mt-1 text-sm text-gray-900"}
            (if edit?
              (ui/text :label "" :name (path "serial-number") :value serial-number  :required? false)
              serial-number)]]]]]])))

(defn insurance-index-page [{:keys [db] :as req}]
  (let [tr (i18n/tr-from-req req)]
    [:div
     (ui/page-header :title (tr [:insurance/title]))

     (let [policies (controller/policies db)]
       [:div {:class "mt-6 sm:px-6 lg:px-8"}
        (ui/divider-left (tr [:insurance/policies])
                         (ui/link-button :label (tr [:insurance/insurance-policy])
                                         :priority :white-rounded
                                         :centered? true
                                         :attr {:href "/insurance-new/"} :icon icon/plus))
        [:div {:class "overflow-hidden bg-white shadow sm:rounded-md mb-8"
               :id "songs-list"}
         (if (empty? policies)
           "No Policies"
           [:ul {:role "list", :class "divide-y divide-gray-200"}
            (map (fn [policy]
                   [:li
                    (policy-row tr policy)]) policies)])]])

     (let [instruments (controller/instruments db)]
       [:div {:class "mt-6 sm:px-6 lg:px-8"}
        (ui/divider-left (tr [:instruments]) (ui/link-button :label (tr [:instrument/instrument])
                                                             :priority :white-rounded
                                                             :centered? true
                                                             :attr {:href "/instrument-new/"} :icon icon/plus))
        [:div {:class "overflow-hidden bg-white shadow sm:rounded-md mb-8"
               :id "songs-list"}
         (if (empty? instruments)
           "No Instruments"
           [:ul {:role "list", :class "divide-y divide-gray-200"}
            (map (fn [instrument]
                   [:li
                    (instrument-row instrument)]) instruments)])]])]))
