(ns app.insurance.views
  (:require
   [app.i18n :as i18n]
   [app.icons :as icon]
   [app.insurance.controller :as controller]
   [app.queries :as q]
   [app.ui :as ui]
   [app.urls :as url]
   [app.util :as util]
   [clojure.string :as str]
   [ctmx.core :as ctmx]
   [ctmx.response :as response]
   [ctmx.rt :as rt]
   [tick.core :as t]
   [medley.core :as m]))

(defn instrument-row [{:instrument/keys [name instrument-id category owner]}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"]
    [:a {:href  (url/link-instrument instrument-id) , :class "block hover:bg-gray-50"}
     [:div {:class "px-4 py-4 sm:px-6"}
      [:div {:class "flex items-center justify-between"}
       [:p {:class "truncate text-sm font-medium text-sno-orange-600"}
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

(ctmx/defcomponent ^:endpoint insurance-policy-delete [{:keys [db] :as req}]
  (when (util/delete? req)
    (controller/delete-policy! req)
    (response/hx-redirect (url/link-insurance))))

(ctmx/defcomponent ^:endpoint insurance-policy-duplicate [{:keys [db] :as req}]
  (when (util/post? req)
    (let [new-policy-id (->  (controller/duplicate-policy! req) :policy :insurance.policy/policy-id)]
      (response/hx-redirect (url/link-policy new-policy-id)))))

(defn policy-row [tr {:insurance.policy/keys [policy-id name effective-at effective-until]}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"]
    [:div {:class "block"}
     [:div {:class "px-4 py-4 sm:px-6"}
      [:div {:class "flex items-center justify-between"}
       [:a {:href  (url/link-policy policy-id) :class "truncate text-sm font-medium text-sno-orange-600 hover:text-sno-orange-900"}
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
       [:div {:class "ml-2 flex flex-shrink-0 gap-4"}
        (ui/button :label (tr [:action/delete])  :priority :white-destructive :size :small
                   :hx-delete (util/comp-name #'insurance-policy-delete)
                   :hx-confirm (tr [:action/confirm-delete-policy] [name])
                   :hx-vals {:policy-id (str policy-id)})
        (ui/button :label (tr [:action/duplicate])  :priority :white :size :small
                   :hx-post (util/comp-name #'insurance-policy-duplicate)
                   :hx-vals {:policy-id (str policy-id)})]]]]))

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
       [:p {:class "truncate text-sm font-medium text-sno-orange-600"}
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
                   [:dl {:class "grid grid-cols-3 gap-x-4 gap-y-8 sm:grid-cols-3"}
                    (ui/dl-item (tr [:insurance/effective-at])
                                (if edit?
                                  [:input {:type "date" :name "effective-at" :id "effective-at"
                                           :value (t/date effective-at)
                                           :required true
                                           :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-sno-orange-500 focus:ring-sno-orange-500 sm:max-w-xs sm:text-sm"}]
                                  (ui/datetime effective-at)))
                    (ui/dl-item (tr [:insurance/effective-until])
                                (if edit?
                                  [:input {:type "date" :name "effective-until" :id "effective-until"
                                           :value (t/date effective-until)
                                           :required true
                                           :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-sno-orange-500 focus:ring-sno-orange-500 sm:max-w-xs sm:text-sm"}]
                                  (ui/datetime effective-until)))
                    (ui/dl-item (tr [:insurance/premium-base-factor])
                                (if edit?
                                  [:input {:type "number" :name "base-factor" :id "base-factor"
                                           :value premium-factor
                                           :step "0.00000001" :min "0" :max "2.0"
                                           :required true
                                           :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-sno-orange-500 focus:ring-sno-orange-500 sm:max-w-xs sm:text-sm"}]
                                  premium-factor))])]))))

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
          [:input {:name delete-id :id delete-id :type "checkbox" :class "h-4 w-4 rounded border-gray-300 text-sno-orange-600 focus:ring-sno-orange-500"}]]
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
                      [:dl {:class "grid grid-cols-3 gap-x-4 gap-y-8"}
                       (rt/map-indexed (if edit? insurance-coverage-types-item-rw insurance-coverage-types-item-ro) req coverage-types)]])
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
          [:input {:name delete-id :id delete-id :type "checkbox" :class "h-4 w-4 rounded border-gray-300 text-sno-orange-600 focus:ring-sno-orange-500"}]]
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
                      [:dl {:class "grid grid-cols-3 gap-x-4 gap-y-8 sm:grid-cols-3"}
                       (rt/map-indexed (if edit? insurance-category-factors-item-rw  insurance-category-factors-item-ro) req category-factors)]])
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

(defn band-or-private [private?]
  (if private?
    [:span {:class "text-red-500" :title "Privat"} "P"]
    [:span {:class "text-green-500" :title "Band"} "B"]))

(defn coverage-create-form [{:keys [tr]} non-covered-instruments coverage-types]
  [:div {:class "flex flex-1 flex-col justify-between"}
   [:div {:class "divide-y divide-gray-200 px-4 sm:px-6"}
    [:div {:class "space-y-6 pt-6 pb-5"}
     [:div
      (ui/instrument-select :id "instrument-id" :instruments non-covered-instruments)]

     [:div
      (ui/money-input :id "value" :label "Value" :required? true)]

     [:fieldset
      [:legend {:class "text-sm font-medium text-gray-900"} (tr [:insurance/coverage-types])]
      [:div {:class "mt-2 space-y-5"}
       (map-indexed (fn [type-idx {:insurance.coverage.type/keys [type-id name]}]
                      [:div {:class "relative flex items-start"}
                       [:div {:class "absolute flex h-5 items-center"}
                        [:input {:id type-id :name "coverage-types" :type "checkbox"
                                 :value type-id
                                 :class "h-4 w-4 rounded border-gray-300 text-sno-orange-600 focus:ring-sno-orange-500"}]]
                       [:div {:class "pl-7 text-sm"}
                        [:label {:for type-id :class "font-medium text-gray-900"} name]
                        [:p {:class "text-gray-500"} ""]]])

                    coverage-types)]]

     [:fieldset
      [:legend {:class "text-sm font-medium text-gray-900"} (tr [:band-private])]
      [:div {:class "mt-2 space-y-5"}
       [:div {:class "relative flex items-start"}
        [:div {:class "absolute flex h-5 items-center"}
         [:input {:id "bandprivate-band" :value "band" :name "band-or-private" :type "radio" :class "h-4 w-4 border-gray-300 text-sno-orange-600 focus:ring-sno-orange-500"}]]
        [:div {:class "pl-7 text-sm"}
         [:label {:for "bandprivate-band" :class "font-medium text-gray-900"} (tr [:band-instrument])]
         [:p {:class "text-gray-500"} (tr [:band-instrument-description])]]]
       [:div
        [:div {:class "relative flex items-start"}
         [:div {:class "absolute flex h-5 items-center"}
          [:input {:id "bandprivate-private" :value "private" :name "band-or-private" :type "radio" :class "h-4 w-4 border-gray-300 text-sno-orange-600 focus:ring-sno-orange-500"}]]
         [:div {:class "pl-7 text-sm"}
          [:label {:for "bandprivate-private" :class "font-medium text-gray-900"} (tr [:private-instrument])]
          [:p {:class "text-gray-500"} (tr [:private-instrument-description])]]]]]]]]])

(defn coverage-edit-form [{:keys [tr]} coverage-types coverage]
  (let [{:instrument/keys [name make model build-year serial-number] :as instrument} (:instrument.coverage/instrument coverage)]
    [:div {:class "flex flex-1 flex-col justify-between"}
     [:div {:class "divide-y divide-gray-200 px-4 sm:px-6"}
      [:div {:class "space-y-6 pt-6 pb-5"}
       [:div
        [:label {:class "block text-sm font-medium text-gray-900"} (tr [:instrument/name])]
        [:div {:class "font-normal mt-2"}
         [:p  name]
         [:p
          (str/join ", "
                    (util/remove-nils
                     (util/remove-empty-strings
                      [make
                       model
                       build-year
                       serial-number])))]]]

       [:div
        [:label {:class "block text-sm font-medium text-gray-900"} (tr [:instrument/owner])]
        [:div {:class "mt-2 space-y-5 font-normal"}
         (-> instrument :instrument/owner :member/name)]]

       [:div
        (ui/money-input :id "value" :label "Value" :required? true :value (:instrument.coverage/value coverage))]

       [:fieldset
        [:legend {:class "text-sm font-medium text-gray-900"} (tr [:insurance/coverage-types])]
        [:div {:class "mt-2 space-y-5"}
         [:input {:type "hidden" :name "coverage-id" :value (:instrument.coverage/coverage-id coverage)}]
         (map-indexed (fn [type-idx {:insurance.coverage.type/keys [type-id name]}]
                        [:div {:class "relative flex items-start"}
                         [:div {:class "absolute flex h-5 items-center"}
                          [:input {:id type-id :name "coverage-types" :type "checkbox"
                                   :value type-id
                                   :checked (some? (controller/get-coverage-type-from-coverage coverage type-id))
                                   :class "h-4 w-4 rounded border-gray-300 text-sno-orange-600 focus:ring-sno-orange-500"}]]
                         [:div {:class "pl-7 text-sm"}
                          [:label {:for type-id :class "font-medium text-gray-900"} name]
                          [:p {:class "text-gray-500"} ""]]])

                      coverage-types)]]

       [:fieldset
        [:legend {:class "text-sm font-medium text-gray-900"} (tr [:band-private])]
        [:div {:class "mt-2 space-y-5"}
         [:div {:class "relative flex items-start"}
          [:div {:class "absolute flex h-5 items-center"}
           [:input {:id "bandprivate-band" :value "band" :name "band-or-private" :type "radio" :class "h-4 w-4 border-gray-300 text-sno-orange-600 focus:ring-sno-orange-500"
                    :checked (not (:instrument.coverage/private? coverage))}]]
          [:div {:class "pl-7 text-sm"}
           [:label {:for "bandprivate-band" :class "font-medium text-gray-900"} (tr [:band-instrument])]
           [:p {:class "text-gray-500"} (tr [:band-instrument-description])]]]
         [:div
          [:div {:class "relative flex items-start"}
           [:div {:class "absolute flex h-5 items-center"}
            [:input {:id "bandprivate-private" :value "private" :name "band-or-private" :type "radio" :class "h-4 w-4 border-gray-300 text-sno-orange-600 focus:ring-sno-orange-500"
                     :checked (:instrument.coverage/private? coverage)}]]
           [:div {:class "pl-7 text-sm"}
            [:label {:for "bandprivate-private" :class "font-medium text-gray-900"} (tr [:private-instrument])]
            [:p {:class "text-gray-500"} (tr [:private-instrument-description])]]]]]]]]]))

(defn coverage-status-icon [status]
  (get {:instrument.coverage.status/needs-review  (icon/circle-question-outline {:class "mr-1.5 h-5 w-5 flex-shrink-0 text-orange-400"})
        :instrument.coverage.status/reviewed (icon/circle-dot-outline {:class "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"})
        :instrument.coverage.status/coverage-active (icon/circle-check-outline {:class "mr-1.5 h-5 w-5 flex-shrink-0 text-green-400"})}
       status))

(ctmx/defcomponent ^:endpoint insurance-instrument-coverage-table [{:keys [db] :as req}]
  (let [policy (:policy req)
        coverage-types (:insurance.policy/coverage-types policy)
        ;; _ (tap> {:types coverage-types})
        covered-instruments (:insurance.policy/covered-instruments policy)
        grouped-by-owner (->>  covered-instruments
                               (util/group-by-into-list :coverages (fn [c] (get-in c [:instrument.coverage/instrument :instrument/owner])))
                               (mapv (fn [r] (update r :coverages  #(sort-by (fn [c] (get-in c [:instrument.coverage/instrument :instrument/name])) %))))
                               (mapv (fn [r] (update r :coverages (fn [coverages]
                                                                    (mapv (fn [coverage]
                                                                            (let [coverage (controller/update-total-coverage-price policy coverage)]
                                                                              (assoc coverage :types
                                                                                     (mapv (fn [{:insurance.coverage.type/keys [type-id]}]
                                                                                             (when-let [coverage-type (controller/get-coverage-type-from-coverage coverage type-id)]
                                                                                               coverage-type))
                                                                                           coverage-types))))
                                                                          coverages)))))

                               (mapv (fn [{:keys [coverages] :as person}]
                                       (assoc person :total (controller/sum-by coverages :instrument.coverage/cost))))
                               (sort-by :member/name))
        total-cost (controller/sum-by grouped-by-owner :total)
        total-instruments (count covered-instruments)
        total-private-count (count (filter :instrument.coverage/private? covered-instruments))
        total-band-count (- total-instruments total-private-count)
        total-needs-review (count (filter #(= :instrument.coverage.status/needs-review (:instrument.coverage/status %)) covered-instruments))
        total-reviewed (count (filter #(= :instrument.coverage.status/reviewed (:instrument.coverage/status %)) covered-instruments))
        total-coverage-active (count (filter #(= :instrument.coverage.status/coverage-active (:instrument.coverage/status %)) covered-instruments))
        grid-class "grid instrgrid--grid"
        col-all ""
        col-sm "hidden sm:block"
        col-md "hidden md:block"
        spacing "pl-2 md:pl-4 pr-2 md:pr-4 py-1"
        center-all "flex items-center justify-center"
        center-vertical "flex items-center"
        number "text-right"
        number-total "border-double border-t-4 border-gray-300"]
    ;; (tap> {:g grouped-by-owner})
    (list
     [:div {:class "instrgrid border-collapse overflow-hidden m-w-full "}
      [:div {:class (ui/cs "instrgrid--header min-w-full py-4 bg-gray-100  text-sm truncate gap-1 grid grid-cols-[22px_minmax(0,_1fr)_minmax(0,_1fr)]"  spacing)}
       [:div {:class (ui/cs col-all center-all)}
        [:input {:type "checkbox" :id "instr-select-all"
                 :_ "on checkboxChanged
                     if length of <div.instrgrid--body input[type=checkbox]:checked/> > 0
                       set .status-selected.innerHTML to `${length of <div.instrgrid--body input[type=checkbox]:checked/>} selected`
                       then add .hidden to .status-totals
                       then remove .hidden from .status-selected
                       then remove .hidden from .actions-selected
                       then set my.indeterminate to true
                     else
                       set .status-selected.innerHTML to ''
                       then remove .hidden from .status-totals
                       then add .hidden to .status-selected
                       then add .hidden to .actions-selected
                       then set my.indeterminate to false
                     then
                       if length of <div.instrgrid--body input[type=checkbox]:checked/> == <div.instrgrid--body input[type=checkbox]/>
                         set my.indeterminate to false
                         then set my.checked to true
                       end
                     end
                     on click set the checked of <div.instrgrid--body input[type=checkbox]/> to my.checked
                       then if length of <div.instrgrid--body input[type=checkbox]:checked/> == <div.instrgrid--body input[type=checkbox]/>
                            set my.checked to true
                            end
                       then trigger checkboxChanged on me"

                 :class "h-4 w-4 rounded border-gray-300 text-sno-orange-600 focus:ring-sno-orange-500"}]]

       [:div {:class (ui/cs  col-all "flex gap-4")}
        [:div {:class (ui/cs  "status-selected hidden py-2 pl-2" center-vertical)}]
        [:div {:class (ui/cs  "status-totals" center-vertical "gap-6")}
         [:span  {:class (ui/cs "py-2 flex" (when (> total-needs-review 0) "font-medium"))}
          (coverage-status-icon :instrument.coverage.status/needs-review) total-needs-review " Todo"]
         [:span  {:class "flex"}
          (coverage-status-icon :instrument.coverage.status/reviewed) total-reviewed " Reviewed"]
         [:span  {:class "flex"}
          (coverage-status-icon :instrument.coverage.status/coverage-active) total-coverage-active " Active"]]]
       [:div {:class (ui/cs  col-all "flex justify-end gap-4 actions-selected hidden")}
        (ui/action-menu
         :label "Mark As"
         :sections [{:items [{:label "Todo" :href "foo" :active? false}
                             {:label "Reviewed" :href "foo" :active? false}
                             {:label "Active" :href "foo" :active? false}]}]
         :id "member-table-actions")]]

      [:div {:class (ui/cs "instrgrid--header min-w-full bg-gray-100 border-b-4 text-sm truncate gap-1 " grid-class spacing)}
       [:div {:class (ui/cs col-all center-all)}]

       [:div {:class (ui/cs col-all)} ""]
       [:div {:class (ui/cs col-all "truncate")} "Instrument"]
       [:div {:class (ui/cs col-all)} "Band?"]
       [:div {:class (ui/cs col-sm number)} "Count"]
       [:div {:class (ui/cs col-sm number)} "Value"]
       (map (fn [ct] [:div {:class (ui/cs col-sm number)} (:insurance.coverage.type/name ct)]) coverage-types)
       [:div {:class (ui/cs col-all number)} "Total"]]
      [:div {:class "instrgrid--body divide-y"}
       (map (fn [{:member/keys [name] :keys [coverages total]}]
              [:div {:class "instrgrid--group"}
               [:div {:class (ui/cs  "instrgrid--group-header gap-2 flex bg-white font-medium text-lg " spacing)}
                [:span name]
                [:span {:class "inline-flex items-center rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-800"} (count coverages)]]
               [:div {:class "divide-y"}
                (map-indexed (fn [idx {:instrument.coverage/keys [status private? value instrument cost] :keys [types]}]
                               [:div {:class (ui/cs "instrgrid--row bg-white py-2  text-sm truncate gap-1 hover:bg-gray-100" grid-class spacing)}
                                [:div {:class (ui/cs col-all center-all)}
                                 [:input {:type "checkbox" :id id :name id :class "h-4 w-4 rounded border-gray-300 text-sno-orange-600 focus:ring-sno-orange-500"
                                          :_ "on click trigger checkboxChanged on #instr-select-all"
                                          ;; :checked (if (= 0 idx) true false)
                                          }]]
                                [:div {:class (ui/cs col-all)}
                                 (coverage-status-icon :instrument.coverage.status/needs-review)]

                                [:div {:class (ui/cs col-all "truncate")}
                                 [:a {:href "#" :class "text-medium"}
                                  (:instrument/name instrument)]]
                                [:div {:class (ui/cs col-all)} (band-or-private private?)]
                                [:div {:class (ui/cs col-sm number)} 2]
                                [:div {:class (ui/cs col-sm number)}  (ui/money value :EUR)]
                                (map (fn [ct] [:div {:class (ui/cs col-sm number)}
                                               (when ct (ui/money  (:insurance.coverage.type/cost ct) :EUR))]) types)
                                [:div {:class (ui/cs col-all number)} (ui/money cost :EUR)]])

                             coverages)]
               [:div {:class (ui/cs grid-class "min-w-full  text-sm gap-1" spacing)}
                [:div {:class (ui/cs col-all)}]
                [:div {:class (ui/cs col-all)}]
                [:div {:class (ui/cs col-all)}]
                [:div {:class (ui/cs col-all)}]
                [:div {:class (ui/cs col-sm)}]
                [:div {:class (ui/cs col-sm)}]
                (map (fn [ct] [:div {:class (ui/cs col-md)}]) coverage-types)
                [:div {:class (ui/cs col-all number number-total)} (ui/money total :EUR)]]])

            grouped-by-owner)]
      [:div {:class "instragrid--footer"}
       [:div {:class (ui/cs grid-class "min-w-full bg-gray-100  text-sm gap-1" spacing)}
        [:div {:class (ui/cs col-all)}]
        [:div {:class (ui/cs col-all)}]
        [:div {:class (ui/cs col-all number number-total)}
         [:span {:class "text-red-500" :title "Privat"} (str  "P" total-private-count " ")]  [:span {:class "text-green-500" :title "Band"} (str  "B" total-band-count)]]
        [:div {:class (ui/cs col-sm number number-total)} total-instruments]
        [:div {:class (ui/cs col-sm)}]
        (map (fn [ct] [:div {:class (ui/cs col-md)}]) coverage-types)
        [:div {:class (ui/cs col-all number number-total)} (ui/money total-cost :EUR)]]]])))

(ctmx/defcomponent ^:endpoint insurance-instrument-coverage [{:keys [db] :as req}]
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
       (ui/slideover-panel-form {:title "Edit Instrument Coverage" :id (path "slideover")
                                 :form-attrs {:hx-put (comp-name) :hx-target (hash ".")}
                                 :buttons (list
                                           (ui/button {:label (tr [:action/delete]) :priority :white-destructive :hx-delete (comp-name) :hx-target (hash ".") :hx-confirm (tr [:action/confirm-generic])})
                                           (ui/button {:label (tr [:action/cancel]) :priority :white :attr {:_ (ui/slideover-extra-close-script (path "slideover")) :type "button"}})
                                           (ui/button {:label (tr [:action/save]) :priority :primary-orange}))}
                                (coverage-create-form {:tr tr :path path}  non-covered-instruments (:insurance.policy/coverage-types policy)))

       [:div {:class "mt-8 grid w-full grid-cols-1 gap-6  lg:grid-flow-col-dense lg:grid-cols-3" :id id}
        [:div {:class "space-y-6 lg:col-span-3 lg:col-start-1"}
         [:section
          [:div {:class "bg-white shadow"}
           [:div {:class "px-4 py-5 px-6  flex items-center justify-between "}
            [:div
             [:h2 {:class "text-lg font-medium leading-6 text-gray-900"} (tr [:insurance/covered-instruments])]]
            [:div {:class "space-x-2 flex"}
             (list
              (ui/button :label (tr [:action/add]) :priority :white :class "" :icon icon/plus :centered? true
                         :attr {:data-flyout-trigger (hash "slideover")}))]]
           [:div {:class "border-t border-gray-200 py-5"}
            [:div {:class "mt-8 overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:mx-0 md:rounded-lg"}
             (insurance-instrument-coverage-table req)]]]]]]

       [:div {:class "mt-2 pt-8 bg-white w-full"}
        [:div {:class ""}
         [:div {:class "px-4 sm:px-6 lg:px-8 sm:flex sm:items-center"}
          [:div {:class "sm:flex-auto"}
           [:h1 {:class "text-2xl font-semibold text-gray-900"}]
           [:p {:class "mt-2 text-sm text-gray-700"} ""]]
          [:div {:class "mt-4 sm:mt-0 sm:ml-16 flex sm:flex-row sm:space-x-4"}]]]

        [:div {:class "mx-auto mt-6 max-w-5xl px-4 sm:px-6 lg:px-8"}
         (when (empty? instrument-coverages)
           [:div
            [:div
             "You should add a coverage to an instrument"]
            [:div
             (ui/button :label (tr [:insurance/instrument-coverage])
                        :priority :primary :icon icon/plus
                        :attr {:data-flyout-trigger (hash "slideover")})]])]]])))

(ctmx/defcomponent insurance-detail-page [{:keys [db] :as req}]
  [:div
   (insurance-detail-page-header req false)
   (insurance-coverage-types req false false)
   (insurance-category-factors req false false)
   (insurance-instrument-coverage req)])

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
                                :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-sno-orange-500 focus:ring-sno-orange-500 sm:max-w-xs sm:text-sm"}]]]
                     [:div {:class "sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:border-t sm:border-gray-200 sm:pt-5"}
                      [:label {:for "first-name" :class "block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"}
                       (tr [:insurance/effective-at])]
                      [:div {:class "mt-1 sm:col-span-2 sm:mt-0"}
                       [:input {:type "date" :name "effective-at" :id "effective-at"
                                :value (str this-year "-05-01")
                                :required true
                                :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-sno-orange-500 focus:ring-sno-orange-500 sm:max-w-xs sm:text-sm"}]]]
                     [:div {:class "sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:border-t sm:border-gray-200 sm:pt-5"}
                      [:label {:for "first-name" :class "block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"}
                       (tr [:insurance/effective-until])]
                      [:div {:class "mt-1 sm:col-span-2 sm:mt-0"}
                       [:input {:type "date" :name "effective-until" :id "effective-until"
                                :value (str next-year "-04-30")
                                :required true
                                :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-sno-orange-500 focus:ring-sno-orange-500 sm:max-w-xs sm:text-sm"}]]]
                     [:div {:class "sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:border-t sm:border-gray-200 sm:pt-5"}
                      [:label {:for "first-name" :class "block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"}
                       (tr [:insurance/premium-base-factor])]
                      [:div {:class "mt-1 sm:col-span-2 sm:mt-0"}
                       (ui/factor-input :name "base-factor" :value (* (bigdec 1.07) (bigdec 0.00447)))]]]]]
                  [:div {:class "pt-5"}
                   [:div {:class "flex justify-end"}
                    [:a {:href "/insurance" :class "rounded-md border border-gray-300 bg-white py-2 px-4 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-sno-orange-500 focus:ring-offset-2"}
                     (tr [:action/cancel])]
                    [:button {:type "submit" :class "ml-3 inline-flex justify-center rounded-md border border-transparent bg-sno-orange-600 py-2 px-4 text-sm font-medium text-white shadow-sm hover:bg-sno-orange-700 focus:outline-none focus:ring-2 focus:ring-sno-orange-500 focus:ring-offset-2"}
                     (tr [:action/create])]]]])])))

(ctmx/defcomponent ^:endpoint instrument-create-page [{:keys [tr db] :as req}]
  (if (util/post? req)
    (do
      (response/hx-redirect (url/link-instrument
                             (:instrument (controller/create-instrument! req)))))

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
                   (ui/member-select :label (tr [:instrument/owner])  :id (path "owner-member-id") :members (q/members-for-select db) :variant :left)
                   (ui/instrument-category-select :variant :left :id (path "category-id") :categories (controller/instrument-categories db))
                   (ui/text-left :label (tr [:instrument/name]) :id (path "name"))
                   (ui/text-left :label (tr [:instrument/make]) :id (path "make"))
                   (ui/text-left :label (tr [:instrument/model]) :id (path "model"))
                   (ui/text-left :label (tr [:instrument/serial-number]) :id (path "serial-number") :required? false)
                   (ui/text-left :label (tr [:instrument/build-year]) :id (path "build-year") :required? false)]]]

                [:div {:class "pt-5"}
                 [:div {:class "flex justify-end"}
                  [:a {:href "/insurance" :class "rounded-md border border-gray-300 bg-white py-2 px-4 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-sno-orange-500 focus:ring-offset-2"}
                   (tr [:action/cancel])]
                  [:button {:type "submit" :class "ml-3 inline-flex justify-center rounded-md border border-transparent bg-sno-orange-600 py-2 px-4 text-sm font-medium text-white shadow-sm hover:bg-sno-orange-700 focus:outline-none focus:ring-2 focus:ring-sno-orange-500 focus:ring-offset-2"}
                   (tr [:action/create])]]]])]))

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
              (ui/member-select :variant :inline-no-label :id (path "owner-member-id") :members (q/members-for-select db))
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

(ctmx/defcomponent ^:endpoint  insurance-index-page [{:keys [db] :as req}]
  insurance-policy-duplicate
  insurance-policy-delete
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
