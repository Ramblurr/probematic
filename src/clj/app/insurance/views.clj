(ns app.insurance.views
  (:require
   [app.auth :as auth]
   [app.config :as config]
   [app.email :as email]
   [app.filestore.image :as filestore.image]
   [app.i18n :as i18n]
   [app.icons :as icon]
   [app.insurance.controller :as controller]
   [app.insurance.domain :as domain]
   [app.layout :as layout]
   [app.queries :as q]
   [app.render :as render]
   [app.ui :as ui]
   [app.urls :as urls]
   [app.util :as util]
   [app.util.http :as util.http]
   [clojure.set :as set]
   [clojure.string :as str]
   [ctmx.core :as ctmx]
   [ctmx.response :as response]
   [ctmx.rt :as rt]
   [hiccup.util :as hiccup.util]
   [medley.core :as m]
   [tick.core :as t]))

(def coverage-status-data
  {:instrument.coverage.status/needs-review {:icon  icon/circle-question-outline :class "text-orange-400"}
   :instrument.coverage.status/reviewed {:icon icon/circle-dot-outline :class "text-gray-400"}
   :instrument.coverage.status/coverage-active {:icon icon/circle-check-outline :class "text-green-400"}})

(def coverage-change-data
  {:instrument.coverage.change/new {:icon icon/circle-plus-solid :class "text-green-400"}
   :instrument.coverage.change/removed {:icon icon/circle-xmark :class "text-red-400"}
   :instrument.coverage.change/changed {:icon icon/circle-exclamation :class "text-orange-400"}
   :instrument.coverage.change/none {:icon icon/circle :class "text-gray-400"}})

(def policy-status-data
  {:insurance.policy.status/active {:icon icon/circle-check-outline :class "text-green-400"}
   :insurance.policy.status/sent {:icon icon/envelope :class "text-orange-400"}
   :insurance.policy.status/draft {:icon icon/circle-dot-outline :class "text-gray-400"}})

(defn coverage-status-icon [status]
  (let [{:keys [icon class]} (get coverage-status-data status)]
    (when icon
      (icon {:class (ui/cs class "mr-1.5 h-5 w-5 flex-shrink-0")}))))

(defn coverage-change-icon [change]
  (let [change (or change :instrument.coverage.change/none)
        {:keys [icon class]} (get coverage-change-data change)]
    (when icon
      (icon {:class (ui/cs class "mr-1.5 h-5 w-5 flex-shrink-0")}))))

(defn coverage-status-icon-span [tr status]
  [:span {:title (tr [status])}
   (coverage-status-icon status)])

(defn coverage-change-icon-span [tr change]
  [:span {:title (tr [change])}
   (coverage-change-icon change)])

(defn policy-status-icon [status]
  (let [{:keys [icon class]} (get policy-status-data status)]
    (when icon
      (icon {:class (ui/cs class "inline mr-1.5 h-5 w-5 flex-shrink-0")}))))

(defn breadcrumb-index [tr]
  (ui/breadcrumb-contained {:class "mt-6 sm:px-6 lg:px-8"}
                           {:label (tr [:nav/insurance]) :href (urls/link-insurance) :icon icon/shield-check-solid}))

(defn breadcrumb-policy [tr policy]
  (ui/breadcrumb-contained {}
                           {:label (tr [:nav/insurance]) :href (urls/link-insurance) :icon icon/shield-check-solid}
                           {:label (:insurance.policy/name policy) :href (urls/link-policy policy)}))

(defn breadcrumb-payments [tr policy]
  (ui/breadcrumb-contained {}
                           {:label (tr [:nav/insurance]) :href (urls/link-insurance) :icon icon/shield-check-solid}
                           {:label (:insurance.policy/name policy) :href (urls/link-policy policy)}
                           {:label "Payments" :href nil}))

(defn breadcrumb-coverage [tr policy coverage]
  (ui/breadcrumb-contained {}
                           {:label (tr [:nav/insurance]) :href (urls/link-insurance) :icon icon/shield-check-solid}
                           {:label (:insurance.policy/name policy) :href (urls/link-policy policy)}
                           {:label (-> coverage :instrument.coverage/instrument :instrument/name) :href nil}))

(defn instrument-row [{:instrument/keys [name instrument-id category owner]}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"]
    [:a {:href  (urls/link-instrument instrument-id) , :class "block hover:bg-gray-50"}
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
    (response/hx-redirect (urls/link-insurance))))

(ctmx/defcomponent ^:endpoint insurance-policy-duplicate [{:keys [db] :as req}]
  (when (util/post? req)
    (let [new-policy-id (->  (controller/duplicate-policy! req) :policy :insurance.policy/policy-id)]
      (response/hx-redirect (urls/link-policy new-policy-id)))))

(defn policy-row [tr {:insurance.policy/keys [policy-id name status] :as policy}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"
        p-class "flex items-center text-sm text-gray-500 mr-6 tooltip"
        {:keys [total-needs-review total-changed total-removed total-new]} (controller/policy-totals policy)]
    [:div {:class "block"}
     [:div {:class "px-4 py-4 sm:px-6"}
      [:div {:class "flex items-center justify-between"}
       [:a {:href  (urls/link-policy policy-id) :class "truncate text-sm font-medium text-sno-orange-600 hover:text-sno-orange-900"}
        [:span {:class "tooltip" :data-tooltip (tr [status])}
         (policy-status-icon status)]
        name]]

      [:div {:class "mt-2 sm:flex sm:justify-between"}
       [:div {:class "flex"}
        (when (> total-needs-review 0)
          [:p {:class p-class :data-tooltip (tr [:insurance/total-needs-review-tooltip])} (coverage-status-icon-span tr :instrument.coverage.status/needs-review) total-needs-review])
        (when (> total-changed 0)
          [:p {:class p-class :data-tooltip (tr [:insurance/total-total-changed-tooltip])} (coverage-change-icon-span tr :instrument.coverage.change/changed) total-changed])
        (when (> total-removed 0)
          [:p {:class p-class :data-tooltip (tr [:insurance/total-total-removed-tooltip])} (coverage-change-icon-span tr :instrument.coverage.change/removed) total-removed])
        (when (> total-new 0)
          [:p {:class p-class :data-tooltip (tr [:insurance/total-total-new-tooltip])} (coverage-change-icon-span tr :instrument.coverage.change/new) total-new])]

       [:div {:class "mt-2 flex items-center text-sm text-gray-500 sm:mt-0"}
                                        ;(icon/calendar {:class style-icon})
                                        ;; [:p "Last Played "]
        ]
       [:div {:class "ml-2 flex flex-shrink-0 gap-4"}
        (ui/button :label (tr [:action/delete])  :priority :white-destructive :size :small
                   :hx-delete (util/comp-name #'insurance-policy-delete)
                   :hx-vals {:policy-id (str policy-id)}
                   :attr {:_ (ui/confirm-modal-script
                              (tr [:action/confirm-generic])
                              (tr [:action/confirm-delete-policy] [name])
                              (tr [:action/confirm-delete])
                              (tr [:action/cancel]))})
        (ui/button :label (tr [:action/duplicate])  :priority :white :size :small
                   :hx-post (util/comp-name #'insurance-policy-duplicate)
                   :hx-vals {:policy-id (str policy-id)})]]]]))

(ctmx/defcomponent ^:endpoint policy-edit [{:keys [db] :as req}]
  (let [policy-id (-> req :path-params :policy-id parse-uuid)
        {:insurance.policy/keys [name policy-id]} (q/retrieve-policy db policy-id)]

    [:div {:hx-get (str (urls/link-policy policy-id "/policy-name")) :hx-target "this" :class "cursor-pointer"}
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

(defn insurance-policy-changes-file [{:keys [db] :as req}]
  (let [policy-id (util.http/path-param-uuid! req :policy-id)
        {:keys [action] :as p} (util.http/unwrap-params req)]
    (condp = action
      "preview"
      (controller/download-changes-excel req)
      "send"
      (do
        (controller/send-changes! req)
        (if (:htmx? req)
          (response/hx-redirect (urls/link-policy policy-id))
          (response/redirect (urls/link-policy policy-id))))
      "confirm-skip-send"
      (do
        (controller/confirm-changes! req)
        (if (:htmx? req)
          (response/hx-redirect (urls/link-policy policy-id))
          (response/redirect (urls/link-policy policy-id)))))))

(ctmx/defcomponent ^:endpoint insurance-policy-changes-review [{:keys [db tr system] :as req}]
  (let [policy-id (util.http/path-param-uuid! req :policy-id)
        policy (q/retrieve-policy db policy-id)
        {:keys [recipient-name recipient-title recipient-email policy-number]} (config/external-insurance-policy (:env system))
        member (auth/get-current-member req)
        sender (:member/name member)
        subject (format "Aktualisierung %s" policy-number)
        attachment-filename (format "AnlageÄnderungen-%s.xls" (t/format (t/formatter "yyyy-M-d") (t/today)))]

    ;; (excel/generate-excel-changeset! policy nil)

    [:div {:class "bg-white shadow py-4 px-6 space-y-12"}
     [:form {:action (urls/link-policy-changes-confirm policy) :method :post}
      [:div {:class "border-b border-gray-900/10 pb-12"}
       [:h2 {:class "text-lg font-semibold leading-7 text-gray-900"} "Benachrichtigung über Änderungen"]
       [:p {:class "mt-1 text-sm leading-6 text-gray-600"}
        "Hier können Sie die folgende Nachricht mit der beigefügten Excel-Tabelle an den Versicherer senden, um ihn über die Änderungen zu informieren."]
       [:div {:class "mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6"}
        [:div {:class "sm:col-span-4"}
         [:label {:for "recipient", :class "block text-sm font-medium leading-6 text-gray-900"} "Empfänger"]
         [:div {:class "mt-2"}
          [:div {:class "flex rounded-md shadow-sm ring-1 ring-inset ring-gray-300 focus-within:ring-2 focus-within:ring-inset focus-within:ring-sno-orange-600 sm:max-w-md"}

           [:input {:type :text :name "recipient" :value (format "%s <%s>" recipient-name recipient-email)
                    :id "recipient" :class "block flex-1 border-0 bg-transparent py-1.5 pl-1 text-gray-900 placeholder:text-gray-400 focus:ring-0 sm:text-sm sm:leading-6"}]]]]
        [:div {:class "sm:col-span-4"}
         [:label {:for "subject", :class "block text-sm font-medium leading-6 text-gray-900"} "Betreff"]
         [:div {:class "mt-2"}
          [:div {:class "flex rounded-md shadow-sm ring-1 ring-inset ring-gray-300 focus-within:ring-2 focus-within:ring-inset focus-within:ring-sno-orange-600 sm:max-w-md"}

           [:input {:type :text :name "subject" :value subject
                    :id "subject" :class "block flex-1 border-0 bg-transparent py-1.5 pl-1 text-gray-900 placeholder:text-gray-400 focus:ring-0 sm:text-sm sm:leading-6"}]]]]
        [:div {:class "col-span-full"}
         [:label {:for "about", :class "block text-sm font-medium leading-6 text-gray-900"} "About"]
         [:div {:class "mt-2"}
          [:textarea {:id "body", :name "body", :rows "9", :class "block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-sno-orange-600 sm:text-sm sm:leading-6"}
           (format "Sehr geehrter %s %s,

Anbei finden Sie die neuesten Änderungen unserer Versicherungspolice für die Instrumente unserer Band. Bitte nehmen Sie sich einen Moment Zeit, um die Aktualisierungen durchzugehen.

Für Rückfragen oder weitere Informationen stehe ich Ihnen gerne zur Verfügung.

Mit freundlichen Grüßen,
%s
"
                   recipient-title recipient-name sender)]]]

        [:div {:class "col-span-full"}
         [:label {:for "photo", :class "block text-sm font-medium leading-6 text-gray-900"} "Anhang"]
         [:div {:class "mt-2 flex items-center gap-x-3"}
          (icon/file-excel-outline {:class "h-12 w-12 text-gray-300"})
          [:div {:class "mt-2 w-full space-y-2"}
           [:div {:class "flex rounded-md shadow-sm ring-1 ring-inset ring-gray-300 focus-within:ring-2 focus-within:ring-inset focus-within:ring-sno-orange-600 sm:max-w-lg"}
            [:input {:type :text :name "attachment-filename" :value attachment-filename
                     :id "attachment-filename" :class "block flex-1 border-0 bg-transparent py-1.5 pl-1 text-gray-900 placeholder:text-gray-400 focus:ring-0 sm:text-sm sm:leading-6"}]]
           [:button {:type :submit :class "rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                     :name "action"
                     :value "preview"}
            "Vorschau"]]]]]

       [:div {:class "mt-6 flex items-center justify-end gap-x-6"}
        (ui/button :label (tr [:action/cancel]) :priority :white
                   :tag :a :href (urls/link-policy policy-id))
        (ui/button :label (tr [:insurance/confirm-skip-send])
                   :priority :secondary
                   :name "action"
                   :value "confirm-skip-send"
                   :type :submit
                   :hx-post (urls/link-policy-changes-confirm policy)
                   :attr {:_ (ui/confirm-modal-script
                              "Skip sending email?"
                              "This will skip sending the email, but mark all the changes as confirmed."
                              "Yes, mark all as confirmed"
                              (tr [:action/cancel]))})
        (ui/button :label (tr [:insurance/confirm-and-send]) :icon icon/envelope :priority :primary
                   :name "action"
                   :value "send"
                   :type :submit
                   :hx-post (urls/link-policy-changes-confirm policy)
                   :attr {:_ (ui/confirm-modal-script
                              "Send email?"
                              "This will send an email and the excel attachment to the recipient"
                              "Yes, send the email and confirm changes."
                              (tr [:action/cancel]))})]]]]))

(defn send-changes-button [{:keys [tr] :as req} {:insurance.policy/keys [status] :as policy} oob?]
  (let [policy-draft? (= status :insurance.policy.status/draft)
        {:keys [total-needs-review]} (controller/policy-totals policy)
        has-todos? (> total-needs-review 0)]
    (when policy-draft?
      [:div {:id "send-changes-button" :hx-swap-oob (when oob? "true")
             :class "tooltip" :data-tooltip (tr [:insurance/send-changes-disabled-hint])}
       (ui/link-button :label (tr [:insurance/send-changes])
                       :disabled? has-todos?
                       :priority :primary
                       :centered? true
                       :href (urls/link-policy-changes policy))])))

(defn send-notifications-button [{:keys [tr] :as req} {:insurance.policy/keys [status] :as policy} oob?]
  (when (= status :insurance.policy.status/active)
    [:div {:id "send-notifications-button" :hx-swap-oob (when oob? "true")}
     (ui/button :tag :a :label (tr [:insurance/send-payment-notifications])
                :priority :primary
                :centered? true
                :href (urls/link-policy-send-notifications policy))]))

(ctmx/defcomponent ^:endpoint insurance-detail-page-header [{:keys [db tr] :as req} ^:boolean edit?]
  (ctmx/with-req req
    (let [policy-id (util.http/path-param-uuid! req :policy-id)
          comp-name (util/comp-namer #'insurance-detail-page-header)
          {:insurance.policy/keys [name effective-at effective-until premium-factor status] :as policy} (q/retrieve-policy db policy-id)
          result (and post? (controller/update-policy! req policy-id))]
      (if (:policy result)
        (response/hx-redirect (urls/link-policy policy-id))
        [(if edit? :form :div)
         (if edit?
           {:id id :hx-post (comp-name) :hx-target (hash ".")}
           {:id id})
         (breadcrumb-policy tr policy)
         (ui/panel {:title (if edit?
                             (ui/text :label "Name" :name "name" :value name)
                             [:span {:title (tr [status])}
                              (policy-status-icon status) name])
                    :buttons  (if edit?
                                (list
                                 (ui/button :label "Cancel"
                                            :priority :white
                                            :centered? true
                                            :hx-get (comp-name) :hx-target (hash ".") :hx-vals {:edit? false})
                                 (ui/button :label "Save"
                                            :priority :primary
                                            :centered? true))
                                (list
                                 (ui/button :label "Edit"
                                            :priority :white
                                            :centered? true
                                            :hx-get (comp-name) :hx-target (hash ".") :hx-vals {:edit? true})
                                 (send-notifications-button req policy false)
                                 (send-changes-button req policy false)))}

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

(ctmx/defcomponent insurance-coverage-types-item-rw [{:keys [db] :as req} idx {:insurance.coverage.type/keys [name description premium-factor type-id]}]
  (let [tr (i18n/tr-from-req req)]
    (ui/dl-item
     ""
     [:div {:class "mt-2 flex flex-col gap-4"}
      (ui/text :label "Coverage Name" :name (path "name") :value name)
      (ui/factor-input :label  (tr [:insurance/premium-factor]) :name (path "premium-factor") :value premium-factor)
      (ui/text :label (tr [:insurance/coverage-type-description]) :name (path "description") :value description)
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
                 (q/retrieve-policy db policy-id))
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
                      [:dl {:class (ui/cs "grid gap-x-4 gap-y-8" (if edit? "grid-cols-1" "grid-cols-3"))}
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
                 (q/retrieve-policy db policy-id))
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

#_(defn remove-covered-instruments [all-instruments instrument-coverages]
    (let [covered-instruments (map :instrument.coverage/instrument instrument-coverages)]
      (remove
       #(some (fn [covered-i]
                (= (:instrument/instrument-id %) (:instrument/instrument-id covered-i)))
              covered-instruments)
       all-instruments)))

(defn band-or-private [tr private?]
  (if private?
    [:span {:class "text-red-500" :title (tr [:private-instrument])} "P"]
    [:span {:class "text-green-500" :title (tr [:band-instrument])} "B"]))

(defn instrument-form
  ([req error instrument]
   (instrument-form req error instrument {}))
  ([{:keys [tr db] :as req} error {:instrument/keys [name owner make model build-year serial-number description category] :as instrument} {:keys [hide-owner?] :as opts}]

   (let [owner-id (if (:member/member-id owner)
                    (:member/member-id owner)
                    (:member/member-id (auth/get-current-member req)))]

     (list
      (ui/text-left :label (tr [:instrument/name]) :id  "instrument-name" :value name :error error)
      (when-not hide-owner?
        (ui/member-select :variant :left :label (tr [:instrument/owner]) :id "owner-member-id" :value owner-id :members (q/members-for-select db) :error error))
      (ui/instrument-category-select :variant :left :label (tr [:instrument/category]) :id "category-id" :value (:instrument.category/category-id category) :categories (controller/instrument-categories db) :error error)
      (ui/text-left :label (tr [:instrument/make]) :id  "make" :value make  :error error)
      (ui/text-left :label (tr [:instrument/model]) :hint (tr [:instrument/if-available]) :id  "model" :value model :required? false :error error)
      (ui/text-left :label (tr [:instrument/serial-number]) :hint (tr [:instrument/if-available]) :id  "serial-number" :value serial-number :required? false :error error)
      (ui/text-left :label (tr [:instrument/build-year]) :hint (tr [:instrument/if-available]) :id  "build-year" :value build-year :required? false :error error)
      (ui/textarea-left :label (tr [:instrument/description]) :hint (tr [:instrument/description-hint]) :name "description" :id "description" :value description :required? false :error error)))))

(defn coverage-form
  ([req error coverage coverage-types]
   (coverage-form req error coverage coverage-types {}))
  ([{:keys [tr]} error coverage coverage-types {:keys [hide-private?] :as opts}]
   (let [{:instrument.coverage/keys [value private? instrument item-count types]} coverage
         {:instrument/keys [name]} instrument]
     (list
      (ui/text-left :type :number :attr {:step 1 :min 1} :label (tr [:insurance/item-count]) :hint (tr [:insurance/item-count-hint]) :id  "item-count" :value (or item-count 1) :error error)
      (ui/money-input-left :id "value" :label (tr [:insurance/value]) :hint (tr [:insurance/value-hint]) :required? true :value value :error error :integer? true)
      (when-not hide-private?
        (ui/checkbox-group-left :label (tr [:band-private]) :id "label-private-band"
                                :label-hint (tr [:private-instrument-payment])
                                :checkboxes (list
                                             (ui/radio-left :name "private-band" :id "private" :value "private" :label (tr [:private-instrument]) :checked? private?
                                                            :hint (tr [:private-instrument-description]))
                                             (ui/radio-left :name "private-band" :id "band" :value "band" :label (tr [:band-instrument]) :checked? (not  private?)
                                                            :hint (tr [:band-instrument-description])))))
      [:input {:type :hidden :name "coverage-types" :value "00000000-0000-0000-0000-000000000000"}]
      (ui/checkbox-group-left :label (tr [:insurance/coverage-types]) :id "coverage-type"

                              :checkboxes (map-indexed (fn [type-idx {:insurance.coverage.type/keys [type-id name description]}]

                                                         (let [checked? (m/find-first (fn [assigned-type]
                                                                                        (= (:insurance.coverage.type/type-id assigned-type) type-id)) types)]

                                                           (list
                                                            (when (= 0 type-idx)
                                                              [:input {:type :hidden :name "coverage-types" :value type-id}])
                                                            (ui/checkbox-left :id type-id :label name :name "coverage-types"
                                                                              :value type-id  :hint description
                                                                              :checked? (if (= 0 type-idx) true checked?)
                                                                              :disabled? (= 0 type-idx)))))

                                                       coverage-types))))))

(declare insurance-instrument-coverage-table)

(ctmx/defcomponent ^:endpoint insurance-instrument-coverage-table-mark-as [req]
  (when (util/post? req)
    (let [{:keys [policy db-after]} (controller/mark-coverages-as! req)
          new-req (util/make-get-request req {:db db-after :policy policy})]
      (ui/multi-response
       [(insurance-instrument-coverage-table new-req)
        (send-notifications-button req policy true)
        (send-changes-button new-req policy true)]))))

(defn mark-coverage-as-menu [{:keys [tr]} endpoint-mark-as hx-target]
  (let [confirm #(ui/confirm-modal-script
                  (tr [:insurance/confirm-mark-as-title] [%])
                  (tr [:insurance/confirm-mark-as] [%])
                  (tr [:insurance/confirm-mark-as-button])
                  (tr [:action/cancel]))
        common-attrs {:hx-post endpoint-mark-as :hx-target hx-target}]
    (ui/action-menu
     :label (tr [:action/mark-as])
     :sections [{:label "Workflow Status"
                 :items [{:label (tr [:instrument.coverage.status/needs-review]) :active? false
                          :icon (coverage-status-icon :instrument.coverage.status/needs-review)
                          :tag :button
                          :attr (merge common-attrs {:hx-vals {"workflow-status" "needs-review"} :_ (confirm (tr [:instrument.coverage.status/needs-review]))})}
                         {:label (tr [:instrument.coverage.status/reviewed]) :href "foo" :active? false :icon (coverage-status-icon :instrument.coverage.status/reviewed)
                          :tag :button
                          :attr (merge common-attrs {:hx-vals {"workflow-status" "reviewed"} :_ (confirm (tr [:instrument.coverage.status/reviewed]))})}]}
                {:label "Change Status"
                 :items [{:label (tr [:instrument.coverage.change/removed]) :href "" :active? false :icon (coverage-change-icon :instrument.coverage.change/removed)
                          :tag :button
                          :attr (merge common-attrs {:hx-vals {"change-status" "removed"}})}
                         {:label (tr [:instrument.coverage.change/new]) :href "" :active? false :icon (coverage-change-icon :instrument.coverage.change/new)
                          :tag :button
                          :attr (merge common-attrs {:hx-vals {"change-status" "new"}})}
                         {:label (tr [:instrument.coverage.change/changed]) :href "" :active? false :icon (coverage-change-icon :instrument.coverage.change/changed)
                          :tag :button
                          :attr (merge common-attrs {:hx-vals {"change-status" "changed"}})}
                         {:label (tr [:instrument.coverage.change/none]) :href "" :active? false :icon (coverage-change-icon :instrument.coverage.change/none)
                          :tag :button
                          :attr (merge common-attrs {:hx-vals {"change-status" "none"}})}
                         #_{:label  (tr [:instrument.coverage.status/coverage-active]) :href "foo" :active? false :icon (coverage-status-icon :instrument.coverage.status/coverage-active)
                            :tag :button
                            :attr {:hx-vals {"mark-as" "coverage-active"} :hx-post endpoint-mark-as :hx-target (hash ".")}}]}]
     :id "coverage-mark-as-actions")))

(declare insurance-survey-table)
(declare survey-response-table-row)
(ctmx/defcomponent ^:endpoint create-survey-handler [req]
  (when (util/post? req)
    (controller/create-survey! req)
    (insurance-survey-table (util/make-get-request req))))

(ctmx/defcomponent ^:endpoint close-survey-handler [req]
  (when (util/post? req)
    (controller/close-survey! req)
    (insurance-survey-table (util/make-get-request req))))

(ctmx/defcomponent ^:endpoint toggle-survey-response-completion-handler [req]
  (when (util/post? req)
    (let [survey-response (controller/toggle-survey-response-completion! req)]
      (survey-response-table-row (util/make-get-request req) survey-response))))

(ctmx/defcomponent ^:endpoint send-survey-notifications-handler [req]
  (when (util/post? req)
    (controller/send-survey-notifications! req)
    (insurance-survey-table (util/make-get-request req))))

(def survey-col-classes {:$first-col     "py-2 text-left pl-2"
                         :$mobile-col    "py-2 text-right px-2"
                         :$no-mobile-col "hidden sm:table-cell py-2 text-right px-2"
                         :$action-col    "relative py-2 pl-2 pr-0 text-right h-12 w-28"})

(defn survey-response-table-row [{:keys [tr policy]} {:insurance.survey.response/keys [member response-id coverage-reports completed-at] :as r}]
  (let [{:member/keys [name member-id] :keys [total]} member
        {:keys [open completed finished?] :as summary} (domain/summarize-member-reports coverage-reports)
        row-id (str "response-row-" response-id)
        {:keys [$no-mobile-col $mobile-col $action-col $first-col]} survey-col-classes]
    [:tr {:class "even:bg-gray-100 odd:bg-white" :id row-id}
     [:td {:class $first-col}
      (if (> (count coverage-reports) 0)
        [:a {:href (urls/link-policy-table-member policy member) :class "link-blue"} name]
        name)
      [:div {:class "sm:hidden text-gray-800 text-sm"}
       (tr [:insurance.survey/completed-count] [completed (count coverage-reports)])]]

     [:td {:class $no-mobile-col} completed]
     [:td {:class $no-mobile-col} (count coverage-reports)]
     [:td {:class $mobile-col}
      (if completed-at
        (icon/checkmark {:class "w-5 h-6 text-sno-green-600 inline"})
        (icon/xmark {:class "w-5 h-6 text-red-600 inline"}))]
     [:td {:class $action-col}
      (ui/button :label (if completed-at "Uncomplete" "Complete") :priority :link :size :small
                 :hx-target (str "#" row-id)
                 :spinner? true
                 :hx-vals {:response-id (str response-id)}
                 :hx-post (util/endpoint-path toggle-survey-response-completion-handler))]]))

(defn insurance-survey-table [{:keys [tr db] :as req}]
  (let [policy (:policy req)
        {:keys [open-surveys closed-surveys]} (controller/survey-table-items req)
        has-open-surveys? (seq open-surveys)
        {:insurance.survey/keys [survey-name closes-at responses survey-id] :as active-survey} (first open-surveys)
        end-date (t/>> (t/instant) (t/new-period 4 :weeks))
        toggle-rw-script "on click toggle .hidden on .survey-ro then toggle .hidden on .survey-rw"
        {:keys [$no-mobile-col $mobile-col $action-col $first-col]} survey-col-classes]
    [:div {:id (util/id :comp/insurance-survey-table)}
     (ui/panel {:title  (tr [:insurance.survey/admin-title])
                :subtitle (tr [:insurance.survey/admin-subtitle])
                :buttons (list
                          (when-not has-open-surveys?
                            (ui/button :label (tr [:insurance.survey/start-survey]) :priority :primary
                                       :attr {:_ "on click add .hidden to me then remove .hidden from .create-survey-form then add .hidden to .no-surveys"})))}

               [:div {:class "mb-2"}
                [:form {:class "create-survey-form hidden" :hx-post (util/endpoint-path create-survey-handler) :hx-target (util/hash :comp/insurance-survey-table)}
                 (ui/text-left :label (tr [:insurance.survey/survey-name]) :id "survey-name" :value (:insurance.policy/name policy))
                 (ui/datetime-left :label (tr [:insurance.survey/closes-at]) :hint (tr [:insurance.survey/closes-at-hint]) :id "closes-at" :value end-date :required? true)
                 (ui/form-buttons
                  :buttons-right (list
                                  (ui/button {:label (tr [:action/open-insurance-survey]) :priority :primary-orange})))]]
               (when-not active-survey
                 [:p {:class "no-surveys"} (tr [:insurance.survey/no-surveys])])

               (when active-survey
                 [:div
                  [:div {:class "mb-2 sm:flex sm:items-center"}
                   [:div
                    {:class "sm:flex-auto"}
                    [:div {:class "survey-ro"}
                     (ui/dl (ui/dl-item (tr [:insurance.survey/survey-name]) survey-name)
                            (ui/dl-item (tr [:insurance.survey/closes-at]) (ui/format-dt closes-at))
                            (ui/dl-item (tr [:time-left]) (ui/humanize-dt closes-at)))]
                    [:div {:class "survey-rw hidden"}
                     (ui/dl (ui/dl-item ""
                                        (ui/text :label (tr [:insurance.survey/survey-name]) :id "survey-name" :value (:insurance.policy/name policy) :required? true))
                            (ui/dl-item ""
                                        (ui/input-datetime2 :value end-date :label (tr [:insurance.survey/closes-at]) :name "closes-at"))
                            (ui/dl-item ""
                                        [:div {:class "flex space-x-4"}
                                         (ui/button :label (tr [:action/cancel]) :priority :white :attr {:_ toggle-rw-script})
                                         (ui/button :label (tr [:action/save]) :priority :primary)]))]]

                   [:div {:class "mt-4 sm:ml-16 sm:mt-0 flex sm:flex-none space-x-4"}
                    (ui/button :label (tr [:insurance.survey/edit-survey]) :priority :white
                               :class "survey-ro"
                               :attr {:_ toggle-rw-script})
                    (ui/button :label (tr [:insurance.survey/close-survey]) :priority :white
                               :class "survey-ro"
                               :hx-post (util/endpoint-path close-survey-handler) :hx-target (util/hash :comp/insurance-survey-table)
                               :hx-vals {:survey-id (str survey-id)}
                               :attr {:_ (ui/confirm-modal-script
                                          (tr [:insurance.survey/confirm-close-title])
                                          (tr [:insurance.survey/confirm-close])
                                          (tr [:insurance.survey/close-survey])
                                          (tr [:action/cancel]))})
                    (ui/button :label (tr [:insurance.survey/send-notifications]) :priority :primary
                               :class "survey-ro"
                               :icon icon/envelope
                               :hx-post (util/endpoint-path send-survey-notifications-handler) :hx-target (util/hash :comp/insurance-survey-table)
                               :hx-vals {:survey-id (str survey-id)}
                               :attr {:_ (ui/confirm-modal-script
                                          (tr [:reminders/confirm-remind-all-title])
                                          (tr [:reminders/confirm-remind-all])
                                          (tr [:reminders/confirm])
                                          (tr [:action/cancel]))})]]
                  [:table {:class "pt-4 sm:pt-0 table-auto min-w-full"}

                   [:thead {:class "hidden sm:table-header-group"}
                    [:tr
                     [:th {:class $first-col} (tr [:col/member])]
                     [:th {:class $no-mobile-col} (tr [:col/num-reviewed])]
                     [:th {:class $no-mobile-col} (tr [:col/total-instruments])]
                     [:th {:class $mobile-col} (tr [:col/completed?])]
                     [:th {:class $action-col}]]]
                   [:tbody
                    (map (partial survey-response-table-row req)
                         responses)]]]))]))

(declare instrument-image-upload-button)

(defn validate-accepted-mime-types
  "Given a list of accepted mime types and a list of actual mime types, this function returns a set of invalid mime types."
  [accepted actual]
  (let [actual-set (set actual)
        accepted-set (set accepted)
        invalid (set/difference actual-set accepted-set)]
    invalid))

(defn instrument-image-upload-button-handler [{:keys [db] :as req}]
  (let [{:keys [instrument-id files]} (-> req :parameters :multipart)
        invalid-types (validate-accepted-mime-types filestore.image/supported-mime-types (map :content-type files))]
    (tap> invalid-types)
    (if (empty? invalid-types)
      (let [{:keys [db-after]} (controller/upload-instrument-images! req instrument-id files)
            instrument (q/retrieve-instrument db-after instrument-id)]
        (render/snippet-response
         (instrument-image-upload-button instrument)))
      (let [instrument (q/retrieve-instrument db instrument-id)]
        (render/snippet-response
         (instrument-image-upload-button instrument (str "Invalid image types: " (str/join ", " (map #(second (str/split % #"/")) invalid-types)))))))))

(defn instrument-image-upload-button
  ([instrument]
   (instrument-image-upload-button instrument nil))
  ([{:instrument/keys [instrument-id images]} error]
   (let [formId  (str "imageUpload-" instrument-id)
         inputId  (str "imageUploadInput-" instrument-id)]
     [:form
      {:hx-encoding "multipart/form-data"
       :id formId
       :hx-target (util/hash formId)
       :hx-post "/instrument-image-button/"
       :_ "on htmx:xhr:progress(loaded, total) set (the first <progress/> in me)'s value to (loaded/total)*100 then remove .hidden from the first <progress/> in me"}
      [:label {:for inputId}
       (if images
         (icon/images {:class (ui/cs "w-5 h-5" "text-gray-400 cursor-pointer hover:text-gray-900")})
         (icon/images-solid {:class (ui/cs "w-5 h-5" "text-red-500 cursor-pointer hover:text-red-800")}))
       [:input {:type :hidden :name "instrument-id" :value (str instrument-id)}]
       [:input
        {:id inputId :type "file" :name "files" :class "hidden" :multiple true
         :accept (str/join "," filestore.image/supported-extensions)
         :_ "on change trigger submit on the closest parent <form/>"}]]
      [:progress {:class "hidden progress-filled:transition-all progress-unfilled:duration-500 progress-filled:rounded-sm progress-unfilled:rounded-md progress-unfilled:bg-[#F2F2F2] progress-filled:bg-sno-green-500" :value "50" :max "100"}]
      (when error
        [:p {:class "text-red-500"} error])])))

(ctmx/defcomponent ^:endpoint insurance-instrument-coverage-table [{:keys [tr] :as req}]
  insurance-instrument-coverage-table-mark-as
  (let [policy (:policy req)
        endpoint-mark-as (util/endpoint-path insurance-instrument-coverage-table-mark-as)
        coverage-types (:insurance.policy/coverage-types policy)
        grouped-by-owner (controller/coverages-grouped-by-owner policy)
        {:keys [total-cost
                total-instruments
                total-private-count
                total-band-count
                total-needs-review
                total-reviewed
                total-coverage-active
                total-changed
                total-removed
                total-no-changes
                total-new]} (controller/policy-totals policy)
        grid-class "grid instrgrid--grid"
        col-all ""
        col-sm "hidden sm:block"
        col-md "hidden md:block"
        spacing "pl-2 md:pl-4 pr-2 md:pr-4 py-1"
        center-all "flex items-center justify-center"
        number "text-right"
        number-total "border-double border-t-4 border-gray-300"]
    [:div {:id id
           :class "instrgrid border-collapse m-w-full"}
     [:input {:type :hidden :name "policy-id" :value (str (:insurance.policy/policy-id policy))}]
     [:div {:class "overflow-x-auto"}
      [:table {:class "table-auto ml-3 sm:ml-4" :id "coverages-table-legend"}
       [:tr
        [:td]
        [:td [:span  {:class (ui/cs "py-2 flex px-2")}
              (coverage-change-icon-span tr :instrument.coverage.change/changed) total-changed " " (tr [:instrument.coverage.change/changed])]]
        [:td [:span  {:class "flex px-2"}
              (coverage-change-icon-span tr :instrument.coverage.change/removed) total-removed " " (tr [:instrument.coverage.change/removed])]]
        [:td [:span  {:class "flex px-2"}
              (coverage-change-icon-span tr :instrument.coverage.change/new) total-new " " (tr [:instrument.coverage.change/new])]]
        [:td [:span  {:class "flex px-2"}
              (coverage-change-icon-span tr :instrument.coverage.change/none) total-no-changes " " (tr [:instrument.coverage.change/none])]]]
       [:tr
        [:td
         [:div {:class (ui/cs (when-not (controller/policy-editable? policy) "hidden"))}
          [:input {:type "checkbox" :id "instr-select-all"
                   :_ (format "on checkboxChanged
                     if length of <div.instrgrid--body input[type=checkbox]:checked/> > 0
                       set .status-selected.innerHTML to `${length of <div.instrgrid--body input[type=checkbox]:checked/>} %s`
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
                       then trigger checkboxChanged on me" (tr [:selected]))

                   :class "h-4 w-4 rounded border-gray-300 text-sno-orange-600 focus:ring-sno-orange-500"}]]]

        [:td {:class "actions-selected hidden flex" :colspan 2}
         [:div {:class (ui/cs col-all "flex gap-4  ml-4 pb-4 sm:pb-1")}
          (mark-coverage-as-menu req endpoint-mark-as (hash "."))]
         [:div {:class (ui/cs  "status-selected hidden py-2 pl-2")}]]
        [:td {:class "status-totals"}
         [:span  {:class (ui/cs "py-2 flex px-2" (when (> total-needs-review 0) "font-medium"))}
          (coverage-status-icon-span tr :instrument.coverage.status/needs-review) total-needs-review " Todo"]]
        [:td {:class "status-totals"} [:span  {:class "flex px-2"}
                                       (coverage-status-icon-span tr :instrument.coverage.status/reviewed) total-reviewed " Reviewed"]]
        [:td {:class "status-totals"} [:span  {:class "flex px-2"}
                                       (coverage-status-icon-span tr :instrument.coverage.status/coverage-active) total-coverage-active " Active"]]]]]

     [:div {:class (ui/cs "overflow-hidden instrgrid--header min-w-full bg-gray-100 border-b-4 text-sm truncate gap-1 " grid-class spacing)}
      [:div {:class (ui/cs col-all center-all)}]

      [:div {:class (ui/cs col-all)}
       ;; status icon
       ""]

      [:div {:class (ui/cs col-all)}
       ;; change icon
       ""]
      [:div {:class (ui/cs col-all "truncate")} (tr [:instrument/instrument])]
      [:div {:class (ui/cs col-sm)} (icon/images {:class "w-5 h-5 text-gray-500"})]
      [:div {:class (ui/cs col-sm)} "Category"]
      [:div {:class (ui/cs col-all)} "Band?"]
      [:div {:class (ui/cs col-sm number)} (tr [:insurance/item-count])]
      [:div {:class (ui/cs col-sm number)} [:span {:title (tr [:insurance/value])} (tr [:insurance/value-abbrev])]]
      (map (fn [ct] [:div {:class (ui/cs col-sm number)} (:insurance.coverage.type/name ct)]) coverage-types)
      [:div {:class (ui/cs col-all number)} (tr [:insurance/total])]]
     [:div {:class "overflow-hidden instrgrid--body divide-y"}
      (map-indexed (fn [member-idx {:member/keys [name member-id] :keys [coverages total] :as member}]
                     [:div {:class "instrgrid--group"}
                      [:div {:class (ui/cs  "instrgrid--group-header gap-2 flex bg-white font-medium text-lg " spacing)}
                       [:span {:id (str "coverages-" member-id)} [:a {:href (urls/link-member member-id) :class "link-blue"} name]]
                       [:span {:class "inline-flex items-center rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-800"} (count coverages)]]
                      [:div {:class "divide-y"}
                       (map-indexed (fn [instr-idx {:instrument.coverage/keys [coverage-id change status private? value item-count instrument cost] :keys [types] :as coverage}]
                                      (let [{:instrument/keys [category] instrument-name :instrument/name} instrument]
                                        [:div {:class (ui/cs "instrgrid--row bg-white py-2  text-sm truncate gap-1 hover:bg-gray-300" grid-class spacing)}
                                         [:div {:class (ui/cs col-all center-all)}
                                          [:div {:class (ui/cs (when-not (controller/policy-editable? policy) "hidden"))}
                                           [:input {:type "checkbox" :id id :name "coverage-ids" :class "h-4 w-4 rounded border-gray-300 text-sno-orange-600 focus:ring-sno-orange-500"
                                                    :value (str coverage-id)
                                                    :_ "on click trigger checkboxChanged on #instr-select-all"}]]]
                                         [:div {:class (ui/cs col-all)}
                                          (coverage-status-icon-span tr status)]
                                         [:div {:class (ui/cs col-all)}
                                          (coverage-change-icon-span tr change)]

                                         [:div {:class (ui/cs col-all "truncate")}
                                          [:a {:href (urls/link-coverage coverage) :class "text-medium"}
                                           instrument-name]]
                                         [:div {:class (ui/cs col-sm)}
                                          (instrument-image-upload-button instrument)]
                                         [:div {:class (ui/cs col-sm "truncate")} (-> category :instrument.category/name)]
                                         [:div {:class (ui/cs col-all)} (band-or-private tr private?)]
                                         [:div {:class (ui/cs col-sm number)} item-count]
                                         [:div {:class (ui/cs col-sm number)}  (ui/money value :EUR)]
                                         (map (fn [ct] [:div {:class (ui/cs col-sm number)}
                                                        (when ct (ui/money  (:insurance.coverage.type/cost ct) :EUR))]) types)
                                         [:div {:class (ui/cs col-all number)} (ui/money cost :EUR)]]))

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
     [:div {:class "overflow-hidden instragrid--footer"}
      [:div {:class (ui/cs grid-class "min-w-full bg-gray-100  text-sm gap-1" spacing)}
       [:div {:class (ui/cs col-all)}]
       [:div {:class (ui/cs col-all)}]
       [:div {:class (ui/cs col-all number number-total)}
        [:span {:class "text-red-500" :title "Privat"} (str  "P" total-private-count " ")]  [:span {:class "text-green-500" :title "Band"} (str  "B" total-band-count)]]
       [:div {:class (ui/cs col-sm number number-total)} total-instruments]
       [:div {:class (ui/cs col-sm)}]
       (map (fn [_] [:div {:class (ui/cs col-md)}]) coverage-types)
       [:div {:class (ui/cs col-all number number-total)} (ui/money total-cost :EUR)]]]]))

(ctmx/defcomponent ^:endpoint insurance-coverage-delete [{:keys [db tr] :as req}]
  (when (and (q/insurance-team-member? db (auth/get-current-member req)) (util/delete? req))
    (response/hx-redirect (urls/link-policy (:policy (controller/delete-coverage! req))))))

(defn photo-upload-script [id url]
  [:script
   (hiccup.util/raw-string
    (format "
function initUpload() {
  try {
    Dropzone.options.%s = {
      %s
      paramName: 'file',
      acceptedFiles: '.jpeg,.jpg,.png,.gif,.heic,.heif',
      maxFileSize: 10, //MB
  //    addRemoveLinks: true,
    };
  } catch (e) {
    console.error(e);
  }
}
initUpload();
document.addEventListener('DOMContentLoaded', function() {
  initUpload();
}); "
            id
            (if url (format "url: \"%s\"," url) "")))])

(defn photo-upload-widget
  "Renders photo upload dropzone. Must be used inside a div/form with the dropzone class and an id of imageUpload"
  ([]
   (photo-upload-widget nil))
  ([url]
   [:div
    [:div {:class "mt-2 sm:col-span-2 sm:mt-0"}
     [:div {:class "dz-message flex justify-center rounded-md px-6 pt-5 pb-6"}
      [:div {:class "space-y-1 text-center"}
       [:svg {:class "mx-auto h-12 w-12 text-gray-400", :stroke "currentColor", :fill "none", :viewbox "0 0 48 48", :aria-hidden "true"}
        [:path {:d "M28 8H12a4 4 0 00-4 4v20m32-12v8m0 0v8a4 4 0 01-4 4H12a4 4 0 01-4-4v-4m32-4l-3.172-3.172a4 4 0 00-5.656 0L28 28M8 32l9.172-9.172a4 4 0 015.656 0L28 28m0 0l4 4m4-24h8m-4-4v8m-12 4h.02", :stroke-width "2", :stroke-linecap "round", :stroke-linejoin "round"}]]
       [:div {:class "flex text-sm text-gray-600 justify-center"}
        [:label {:for "file-upload" :class "relative rounded-md bg-white font-medium text-sno-orange-600 focus-within:outline-none focus-within:ring-2 focus-within:ring-sno-orange-500 focus-within:ring-offset-2 hover:text-sno-orange-500"}
         [:span "Upload a file"]]
        [:p {:class "pl-1 hidden md:block"} "or drag and drop"]]
       [:p {:class "text-xs text-gray-500"} "PNG, JPG, GIF up to 10MB"]]]]

    (photo-upload-script "imageUpload" url)]))

(ctmx/defcomponent ^:endpoint insurance-coverage-detail-page-rw [{:keys [db tr] :as req}]
  insurance-coverage-delete
  (let [post? (util/post? req)
        result (when post? (controller/update-instrument-and-coverage! req))
        insurance-team-member? (q/insurance-team-member? db (auth/get-current-member req))
        error (:error result)
        form-error (:form-error error)]
    (if (and post? (not error))
      (response/hx-redirect (urls/link-policy (:policy result)))
      (let [coverage-id (util.http/path-param-uuid! req :coverage-id)
            coverage (q/retrieve-coverage db coverage-id)
            {:instrument/keys [instrument-id] :as instrument} (:instrument.coverage/instrument coverage)
            policy (:insurance.policy/_covered-instruments coverage)
            coverage-types (:insurance.policy/coverage-types policy)]
        (assert coverage)
        [:div {:id id}
         (ui/panel {:title "Edit Instrument Coverage"
                    :buttons (list (ui/link-button :href (urls/link-coverage coverage) :label (tr [:action/cancel])))}
                   [:form {:hx-post (path ".") :class "space-y-8"  :hx-target (hash ".")}
                    [:div {:class "space-y-8 divide-y divide-gray-200 sm:space-y-5"}
                     [:div {:class "space-y-6 sm:space-y-5"}
                      [:div {:class "space-y-6 sm:space-y-5"}
                       [:input {:type :hidden :name "policy-id" :value (:insurance.policy/policy-id policy)}]
                       [:input {:type :hidden :name "coverage-id" :value (:instrument.coverage/coverage-id coverage)}]
                       [:input {:type :hidden :name "instrument-id" :value instrument-id}]
                       (ui/form-left-section :label (tr [:instrument/instrument]) :hint (tr [:instrument/create-subtitle]))
                       (instrument-form req error instrument)
                       [:div {:class "sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:border-y sm:border-gray-200 sm:py-5"}
                        [:div {:class "block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"} (tr [:instrument/photo-upload])]
                        [:div {:class "mt-1 sm:col-span-2 sm:mt-0"}
                         [:div {:class "relative max-w-lg sm:max-w-xs dropzone" :id "imageUpload"}
                          (photo-upload-widget (urls/link-instrument-image-upload instrument-id))]]]
                       (ui/form-left-section :label (tr [:insurance/instrument-coverage]) :hint (tr [:insurance/coverage-for] [(:insurance.policy/name policy)]))
                       (coverage-form req error coverage coverage-types)
                       (when form-error
                         [:div
                          [:p {:class "mt-2 text-right text-red-600"}
                           (icon/circle-exclamation {:class "h-5 w-5 inline-block mr-2"})
                           (tr [form-error])]])

                       (ui/form-buttons
                        :buttons-left (list
                                       (when insurance-team-member?
                                         (ui/button {:label     (tr [:action/delete]) :priority :white-destructive
                                                     :hx-delete (util/endpoint-path insurance-coverage-delete)
                                                     :hx-target (hash ".")
                                                     :hx-vals   {:coverage-id (str coverage-id)}
                                                     :attr      {:_ (ui/confirm-modal-script
                                                                     (tr [:action/confirm-generic])
                                                                     (tr [:action/confirm-delete-instrument] [(:instrument/name instrument)])
                                                                     (tr [:action/confirm-delete])
                                                                     (tr [:action/cancel]))}})))
                        :buttons-right (list
                                        (ui/link-button {:label (tr [:action/cancel]) :priority :white
                                                         :attr {:href (urls/link-policy policy)}})
                                        (ui/button {:label (tr [:action/save]) :priority :primary-orange})))]]]])]))))

(defn coverage-panel [tr coverage policy]
  (ui/panel {:title (tr [:insurance/instrument-coverage])
             :subtitle (tr [:insurance/coverage-for] [(:insurance.policy/name policy)])
             :buttons (list
                       (when (controller/policy-editable? policy)
                         (ui/link-button :hx-boost "true" :href (urls/link-coverage-edit coverage) :label (tr [:action/edit]))))}
            (ui/dl
             (ui/dl-item (tr [:insurance/item-count])
                         (:instrument.coverage/item-count coverage))
             (ui/dl-item (tr [:insurance/value])
                         (ui/money (:instrument.coverage/value coverage) :EUR))
             (ui/dl-item (tr [:band-private])
                         (if (:instrument.coverage/private? coverage)
                           [:span {:class "text-red-400"} (tr [:private-instrument])]
                           [:span {:class "text-green-400"} (tr [:band-instrument])])))
            [:div {:class "mt-4 max-w-xs"}
             [:h3 {:class "font-medium text-sm text-gray-500"} (tr [:insurance/coverage-types])]
             [:dl {:class "mt-2  border-t border-b border-gray-200"}
              (map (fn [{:insurance.coverage.type/keys [cost name]}]
                     [:div {:class "flex justify-between py-3 text-sm font-medium"}
                      [:dt {:class "text-gray-500"} name]
                      [:dd {:class "whitespace-nowrap text-gray-900"}
                       (ui/money cost :EUR)]]) (:instrument.coverage/types coverage))

              [:div {:class "flex justify-between py-3 text-sm font-medium border-double border-t-4 border-gray-300"}
               [:dt {:class "text-gray-500"} (tr [:insurance/total])]
               [:dd {:class "whitespace-nowrap text-gray-900"} (ui/money (:instrument.coverage/cost coverage) :EUR)]]]]))

(def history-field-exclusions #{:instrument.coverage/coverage-id :instrument/instrument-id})
(defn change-value [{:keys [tr] :as req} coverage k v]
  ;; (tap> [:k k :v v])
  (condp = k
    :instrument/category (str (:instrument.category/name v))
    :instrument.coverage/value (ui/money v :EUR)
    :instrument/owner (ui/member v)
    :instrument.coverage/instrument (:instrument/name v)
    :instrument.coverage/types (:insurance.coverage.type/name v)
    :instrument.coverage/private? (band-or-private tr v)
    :instrument/images (if-let [uri (controller/build-image-uri req (:instrument.coverage/instrument coverage) v)] [:img {:class "object-contain h-20 w-20" :src (:thumbnail uri)}]
                               "Image deleted.")
    (if (keyword? v)
      (tr [v])
      (str v))))

(defn munge-field-change [acc field-change]
  ;; (tap> [:field-change field-change])
  (condp =  (count field-change)
    1 (let [[k v action :as f] (first field-change)]
        (conj acc
              (if (= action :added)
                (assoc f 1 {:before nil :after v :action action})
                (assoc f 1 {:before v :after nil :action action}))))
    2 (let [updated? (= #{:retracted :added} (set (map #(nth % 2) field-change)))]
        (if updated?
          (conj acc
                (-> (first field-change)
                    (assoc 2 :updated)
                    (assoc 1 {:before (second (first field-change))
                              :after  (second (second field-change))})))
          (concat acc
                  (map (fn [[k v action :as f]]
                         (if (= :retracted action)
                           (assoc f 1 {:before v :after nil :action action})
                           (assoc f 1 {:before nil :after v :action action}))) field-change))))

    (concat acc
            (map (fn [[k v action :as f]]
                   (if (= :retracted action)
                     (assoc f 1 {:before v :after nil :action action})
                     (assoc f 1 {:before nil :after v :action action}))) field-change))))

(defn coverage-changes-panel [{:keys [tr] :as req} coverage policy]
  (let [history (controller/instrument-coverage-history req coverage)
        fn-change-value (partial change-value req coverage)]
    ;; (tap> [:history history])
    (ui/panel {:title (tr [:history/title])
               :subtitle (tr [:history/subtitle-coverage])}
              [:div {:class "mt-6 overflow-hidden border-t border-gray-100"}
               [:div {:class "mx-auto max-w-7xl px-2 sm:px-6 lg:px-8"}
                [:div {:class "mx-auto max-w-2xl lg:mx-0 lg:max-w-none"}
                 [:table {:class "table-auto text-left w-full"}
                  [:thead {:class "border-b border-gray-200 bg-gray-300"}
                   [:tr {:class "relative isolate py-2 font-semibold"}
                    [:th "Editor"
                     [:div {:class "absolute inset-y-0 right-full -z-10 w-screen border-b border-gray-200 bg-gray-200"}]
                     [:div {:class "absolute inset-y-0 left-0 -z-10 w-screen border-b border-gray-200 bg-gray-200"}]]
                    [:th "Field"]
                    [:th "Before"]
                    [:th "After"]]]
                  [:tbody
                   (map (fn [{:keys [changes timestamp audit] :as tx}]
                          (let [changes (->> changes
                                             (group-by first)
                                             (vals)
                                             (reduce munge-field-change [])
                                             (map (fn [[k {:keys [before after]} action]]
                                                    {:field-key k
                                                     :field-label (tr [k])
                                                     :action action
                                                     :before (when-let [before before] (fn-change-value  k before))
                                                     :after (fn-change-value  k after)}))

                                             (remove #(history-field-exclusions (:field-key %)))
                                             (sort-by :field-label))
                                audit-user-name (:member/name (:audit/member audit))]
                            [:div
                             [:tr {:class "text-sm leading-6 text-gray-900"}
                              [:th {:class "relative isolate py-2 font-semibold" :scope "colgroup" :colspan 5}

                               [:time {:datetime "2023-03-22"} (ui/humanize-dt timestamp)]
                               [:div {:class "absolute inset-y-0 right-full -z-10 w-screen border-b border-gray-200 bg-gray-50"}]
                               [:div {:class "absolute inset-y-0 left-0 -z-10 w-screen border-b border-gray-200 bg-gray-50"}]]]

                             (map
                              (fn [{:keys [action after before field-key field-label]}]
                                (let [after (if (keyword? after) (tr [after]) after)
                                      before (if (keyword? before) (tr [before]) before)]
                                  [:tr
                                   [:td {:class "relative py-3 pr-3"}
                                    [:div {:class "flex gap-x-3"}
                                     (condp = action
                                       :retracted (icon/circle-xmark {:class "h-6 w-5 text-red-400 flex-none sm:block"})
                                       :added (icon/circle-plus-solid {:class "h-6 w-5 text-green-400  flex-none sm:block"})
                                       :updated (icon/circle-exclamation {:class "h-6 w-5 text-sno-orange-400  flex-none sm:block"}))
                                     [:div
                                      {:class "flex-auto"}
                                      [:div
                                       {:class "flex items-start gap-x-3"}
                                       [:div
                                        {:class "text-sm  leading-6 text-gray-900"}
                                        audit-user-name]]
                                      [:div {:class "mt-1 text-xs leading-5 text-gray-500"} (tr [(keyword "history" (name action))])]]]]

                                   [:td
                                    {:class ""}
                                    [:div {:class "text-sm font-medium leading-6 text-gray-900"} field-label]
                                    [:div
                                     {:class "mt-1 text-xs leading-5 text-gray-500"}]]
                                   [:td
                                    {:class ""}
                                    [:div {:class "text-sm leading-6 text-gray-900"} (or  before "-")]
                                    [:div
                                     {:class "mt-1 text-xs leading-5 text-gray-500"}]]
                                   [:td
                                    {:class ""}
                                    [:div {:class "text-sm leading-6 text-gray-900"} (or  after "-")]
                                    [:div
                                     {:class "mt-1 text-xs leading-5 text-gray-500"}]]]))

                              changes)]))

                        history)]]]]])))

(ctmx/defcomponent ^:endpoint insurance-coverage-detail-page [{:keys [db tr] :as req}]
  insurance-coverage-delete
  (let [post? (util/post? req)
        result (when post? (controller/update-instrument-and-coverage! req))
        error (:error result)]
    (if (and post? (not error))
      (response/hx-redirect (urls/link-policy (:policy result)))
      (let [coverage-id (util.http/path-param-uuid! req :coverage-id)
            coverage (q/retrieve-coverage db coverage-id)
            policy (:insurance.policy/_covered-instruments coverage)
            coverage-types (:insurance.policy/coverage-types policy)
            coverage (first (domain/enrich-coverages policy coverage-types [coverage]))
            {:instrument/keys [instrument-id images-share-url] :as  instrument} (:instrument.coverage/instrument coverage)
            photo-uris (controller/build-image-uris req instrument)]
        (assert coverage)
        [:div {:id id}
         (breadcrumb-coverage tr policy coverage)
         (ui/panel {:title (:instrument/name instrument)
                    :buttons (when (controller/policy-editable? policy)
                               (list
                                (ui/link-button :hx-boost true :href (urls/link-coverage-edit coverage) :label (tr [:action/edit]))))}
                   (ui/dl
                    (ui/dl-item (tr [:instrument/owner])
                                (ui/member (:instrument/owner instrument)))
                    (ui/dl-item (tr [:instrument/name])
                                (:instrument/name instrument))
                    (ui/dl-item (tr [:instrument/category])
                                (:instrument.category/name (:instrument/category instrument)))
                    (ui/dl-item (tr [:instrument/make])
                                (:instrument/make instrument))
                    (ui/dl-item (tr [:instrument/model])
                                (:instrument/model instrument))
                    (ui/dl-item (tr [:instrument/serial-number])
                                (:instrument/serial-number instrument))
                    (ui/dl-item (tr [:instrument/build-year])
                                (:instrument/build-year instrument))
                    (ui/dl-item (tr [:instrument/description])
                                (:instrument/description instrument) "sm:col-span-2")
                    (ui/dl-item (tr [:instrument/images-share-url])
                                [:p (tr [:instrument/images-share-url-hint]) ": "
                                 [:a {:href images-share-url :target "_blank" :class "link-blue"} images-share-url]]))
                   (ui/photo-grid photo-uris))
         (coverage-panel tr coverage policy)
         (coverage-changes-panel req coverage policy)]))))

(defn instrument-public-page-download-all [req instrument-id]
  (controller/get-all-images-as-input-stream! req instrument-id))

(defn instrument-public-page [{:keys [tr db] :as req} instrument-id]
  (let [{:instrument/keys [name category model build-year make serial-number description] :as instrument} (q/retrieve-instrument db instrument-id)
        photo-uris (controller/build-image-uris req instrument)]
    (layout/centered-content-lg req
                                [:div
                                 [:div
                                  {:class "px-4 sm:px-0"}
                                  [:h3
                                   {:class "text-base font-semibold leading-7 text-gray-900"}   name]
                                  #_[:p {:class "mt-1 max-w-2xl text-sm leading-6 text-gray-500"} "Personal details and application."]]
                                 [:div
                                  {:class "mt-6 border-t border-gray-100"}
                                  [:dl
                                   {:class "divide-y divide-gray-100"}
                                   [:div
                                    {:class "px-4 py-4 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-0"}
                                    [:dt {:class "text-sm font-medium leading-6 text-gray-900"} (tr [:instrument/category])]
                                    [:dd {:class "mt-1 text-sm leading-6 text-gray-700 sm:col-span-2 sm:mt-0"} (:instrument.category/name category)]]
                                   (when-not (str/blank? make)
                                     [:div
                                      {:class "px-4 py-4 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-0"}
                                      [:dt {:class "text-sm font-medium leading-6 text-gray-900"} (tr [:instrument/make])]
                                      [:dd {:class "mt-1 text-sm leading-6 text-gray-700 sm:col-span-2 sm:mt-0"} (or make "-")]])
                                   (when-not (str/blank? model)
                                     [:div {:class "px-4 py-4 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-0"}
                                      [:dt {:class "text-sm font-medium leading-6 text-gray-900"} (tr [:instrument/model])]
                                      [:dd {:class "mt-1 text-sm leading-6 text-gray-700 sm:col-span-2 sm:mt-0"} (or model "-")]])
                                   (when-not (str/blank? description)
                                     [:div
                                      {:class "px-4 py-4 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-0"}
                                      [:dt {:class "text-sm font-medium leading-6 text-gray-900"} (tr [:instrument/description])]
                                      [:dd {:class "mt-1 text-sm leading-6 text-gray-700 sm:col-span-2 sm:mt-0"} (or description "-")]])
                                   (when-not (str/blank? serial-number)
                                     [:div
                                      {:class "px-4 py-4 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-0"}
                                      [:dt {:class "text-sm font-medium leading-6 text-gray-900"} (tr [:instrument/serial-number])]
                                      [:dd {:class "mt-1 text-sm leading-6 text-gray-700 sm:col-span-2 sm:mt-0"} (or serial-number "-")]])
                                   (when-not (str/blank? build-year)
                                     [:div
                                      {:class "px-4 py-4 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-0"}
                                      [:dt {:class "text-sm font-medium leading-6 text-gray-900"} (tr [:instrument/build-year])]
                                      [:dd {:class "mt-1 text-sm leading-6 text-gray-700 sm:col-span-2 sm:mt-0"} (or build-year "-")]])
                                   [:div
                                    {:class "px-4 py-4 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-0"}
                                    [:dt {:class "text-sm font-medium leading-6 text-gray-900"} (tr [:instrument/images])]
                                    [:dd {:class "mt-2 text-sm text-gray-900 sm:col-span-2 sm:mt-0"}
                                     (if (seq photo-uris)
                                       (list
                                        [:div
                                         (ui/link-button :href (str instrument-id "/download-zip") :icon icon/download :label "Download All" :priority :primary)]
                                        [:div {:class "-ml-5"}
                                         (ui/photo-grid photo-uris)])
                                       "No Photos")]]]]])))

(ctmx/defcomponent ^:endpoint insurance-coverage-create-page3 [{:keys [db tr] :as req}]
  (let [post? (util/post? req)
        result (when post? (controller/upsert-coverage! req))
        error (:error result)]
    (if (and post? (not error))
      (response/hx-redirect (urls/link-policy (:policy result)))
      (let [policy-id (util.http/path-param-uuid! req :policy-id)
            instrument-id  (util.http/path-param-uuid! req :instrument-id)
            policy (q/retrieve-policy db policy-id)
            coverage-types (:insurance.policy/coverage-types policy)]
        [:div {:id id}
         [:div {:class "flex justify-center items-center mt-10"}
          (ui/step-circles 3 3)]
         (ui/panel {:title (tr [:insurance/instrument-coverage])}
                   [:form {:hx-post (path ".") :class "space-y-8" :hx-target (hash ".")}
                    [:input {:type :hidden :name "policy-id" :value policy-id}]
                    [:input {:type :hidden :name "instrument-id" :value instrument-id}]
                    (coverage-form req error nil coverage-types)
                    (ui/form-buttons
                     :buttons-left
                     (list
                      (ui/link-button {:attr {:href (urls/link-coverage-create2 policy-id instrument-id)} :label (tr [:action/back]) :white :primary-orange}))
                     :buttons-right
                     (list
                      (ui/button {:label (tr [:action/save]) :priority :primary-orange})))])]))))

(defn image-fetch-handler [{:keys [parameters headers] :as req}]
  (let [{:keys [instrument-id image-id]} (:path parameters)
        {:keys [mode]} (:query parameters)
        img-resp (controller/load-instrument-image req instrument-id image-id mode)]
    (ui/file-response req
                      (:file-name img-resp)
                      ((:content-thunk img-resp))
                      img-resp)))

(defn image-upload-handler [req]
  (controller/upload-instrument-image! req)
  {:status 201})

(ctmx/defcomponent ^:endpoint image-upload [{:keys [tr] :as req}]
  (let [instrument-id (util.http/path-param-uuid! req :instrument-id)
        policy-id (util.http/path-param-uuid! req :policy-id)]
    [:div {:id id}
     [:div {:class "flex justify-center items-center mt-10"}
      (ui/step-circles 3 2)]
     (ui/panel {:title (tr [:instrument/photo-upload-title])
                :subtitle (tr [:instrument/photo-upload-subtitle])}
               [:form {:action (urls/link-instrument-image-upload instrument-id)
                       :class "dropzone space-y-8"
                       :id (util/id :comp/imageUpload)
                       :enctype "multipart/form-data"}
                (photo-upload-widget)]
               (ui/form-buttons :buttons-left (list
                                               (ui/link-button {:attr {:href (urls/link-coverage-create policy-id instrument-id)} :label (tr [:action/back]) :white :primary-orange}))
                                :buttons-right (list
                                                (ui/link-button {:attr {:href (urls/link-coverage-create3 policy-id instrument-id)} :label (tr [:action/next]) :priority :primary-orange}))))]))

(ctmx/defcomponent ^:endpoint insurance-coverage-create-page [{:keys [db tr] :as req}]
  (let [post? (util/post? req)
        result (when post? (controller/upsert-instrument! req))
        error (:error result)
        instrument-id (if post?
                        (-> result :instrument :instrument/instrument-id)
                        (util.http/query-param-uuid req :instrument-id))
        policy-id (util.http/path-param-uuid! req :policy-id)]
    (if (and post? (not error))
      (response/hx-redirect (urls/link-coverage-create2 policy-id instrument-id))
      (let [instrument (when instrument-id (q/retrieve-instrument db instrument-id))]
        [:div {:id id}
         [:div {:class "flex justify-center items-center mt-10"}
          (ui/step-circles 3 1)]
         (ui/panel {:title (tr [:instrument/create-title])}
                   [:form {:hx-post (path ".") :class "space-y-8" :hx-target (hash ".")}
                    [:input {:type :hidden :name "instrument-id" :value instrument-id}]
                    (instrument-form req error instrument)
                    (ui/form-buttons :buttons-right
                                     (list
                                      (ui/button {:label (tr [:action/next]) :priority :primary-orange})))])]))))

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
        instrument-coverages (:insurance.policy/covered-instruments policy)]
    (if (and put? policy)
      ctmx.response/hx-refresh
      [:div {:id id :hx-trigger "refreshCoverages from:body" :hx-get (comp-name)}
       [:div {:class "mt-8 grid w-full grid-cols-1 gap-6  lg:grid-flow-col-dense lg:grid-cols-3" :id id}
        [:div {:class "space-y-6 lg:col-span-3 lg:col-start-1"}
         [:section
          [:div {:class "bg-white shadow"}
           [:div {:class "px-4 py-5 px-6  flex items-center justify-between "}
            [:div
             [:h2 {:class "text-lg font-medium leading-6 text-gray-900"} (tr [:insurance/covered-instruments])]]
            [:div {:class "space-x-2 flex"}
             (list
              (when (controller/policy-editable? policy)
                (ui/link-button :label (tr [:action/add]) :priority :white :class "" :icon icon/plus :centered? true
                                :attr {:href (urls/link-coverage-create policy-id)})))]]
           [:div {:class "border-t border-gray-200 py-5"}
            [:div {:class "md:mx-0 md:rounded-lg"}
             (insurance-instrument-coverage-table req)]]]]]]

       (when (empty? instrument-coverages)
         [:div {:class "mt-2 pt-8 bg-white w-full"}
          [:div {:class "mx-auto mt-6 max-w-5xl px-4 sm:px-6 lg:px-8"}

           [:div
            [:div
             "You should add a coverage to an instrument"]
            [:div
             (ui/link-button :label (tr [:insurance/instrument-coverage])
                             :attr {:href (urls/link-coverage-create policy-id)}
                             :priority :primary :icon icon/plus)]]]])])))

(ctmx/defcomponent insurance-detail-page [{:keys [db] :as req}]
  create-survey-handler close-survey-handler toggle-survey-response-completion-handler send-survey-notifications-handler
  [:div
   (insurance-detail-page-header req false)
   (insurance-survey-table req)
   (insurance-instrument-coverage req)
   (insurance-coverage-types req false false)
   (insurance-category-factors req false false)])

(ctmx/defcomponent ^:endpoint insurance-create-page [{:keys [db] :as req}]
  (let [this-year (t/year (t/now))
        next-year (t/year (t/>> (t/now) (t/new-duration 365 :days)))
        tr (i18n/tr-from-req req)
        result (and (util/post? req) (controller/create-policy! req))]
    (if (:policy result)
      (response/hx-redirect (urls/link-policy (:policy result)))

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
  #_(if (util/post? req)
      (do
        (response/hx-redirect (urls/link-instrument
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
  #_(let [post? (util/post? req)
          comp-name (util/comp-namer #'instrument-detail-page)
          tr (i18n/tr-from-req req)
          instrument-id  (parse-uuid  (-> req :path-params :instrument-id))
          {:instrument/keys [name make build-year model serial-number] :as instrument} (q/retrieve-instrument db instrument-id)
          result (and post? (controller/update-instrument! req instrument-id))]
      (if (:instrument result)
        (response/hx-redirect (urls/link-instrument instrument-id))
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

     (breadcrumb-index tr)

     (let [policies (q/policies db)]
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
                    (policy-row tr policy)]) policies)])]])]))

(ctmx/defcomponent ^:endpoint insurance-send-notifications [{:keys [db tr] :as req}]
  (let [{:keys [error count-sent policy]} (controller/send-notifications! req)]
    (if error
      [:div
       [:h2 {:class "text-2xl"} "Error"]
       [:p {:class "text-red-700"} error]]
      [:div
       [:h2 {:class "text-2xl mb-4"} (icon/circle-check {:class "w-8 h-8 text-sno-green-700 inline"}) " All good!"]
       [:p (format "%d notifications sent!" count-sent)]
       [:p
        [:a {:class "link-blue" :href (urls/link-policy policy)} "Back to Insurance"]]])))

(ctmx/defcomponent ^:endpoint insurance-notify-page [{:keys [db tr] :as req}]
  (let [{:keys [policy sender-name members-data time-range]} (controller/build-data-notification-table req)]
    [:div {:id :comp/insurance-notify-page}
     (breadcrumb-payments tr policy)
     (ui/panel {:title (tr [:insurance/request-payments-title])
                :subtitle (tr [:insurance/request-payments-subtitle])}
               [:form {:class "max-w-xl" :hx-post (util/endpoint-path insurance-send-notifications) :tx-target (util/hash :comp/insurance-notify-page)}
                [:table {:class "table-auto w-full"}
                 [:thead
                  [:th {:class "text-left px-2"}
                   [:input {:type "checkbox" :id "instr-select-all"
                            :checked "true"
                            :_ (format "on checkboxChanged
                     if length of <tbody.instrgrid--body input[type=checkbox]:checked/> == length of <tbody.instrgrid--body input[type=checkbox]/>
                          log \"some\"
                         set my.indeterminate to false
                         then set my.checked to true
                         then set #send-payment@disabled to null
                     else
                       log \"not all\"
                       if length of <tbody.instrgrid--body input[type=checkbox]:checked/> > 0
                         set my.indeterminate to true
                         then set #send-payment@disabled to null
                       else
                         set my.indeterminate to false
                         then set my.checked to false
                         then set #send-payment@disabled to true
                       end
                     end
                     on click set the checked of <tbody.instrgrid--body input[type=checkbox]/> to my.checked
                       then if length of <tbody.instrgrid--body input[type=checkbox]:checked/> == length of <tbody.instrgrid--body input[type=checkbox]/>
                            set my.checked to true
                            end
                       then trigger checkboxChanged on me" (tr [:selected]))

                            :class "h-4 w-4 rounded border-gray-300 text-sno-orange-600 focus:ring-sno-orange-500"}]]
                  [:th {:class "text-left px-2"} "Member"]
                  [:th {:class "text-right px-2 max-w-32 text-pretty"} (str "# " (tr [:private-instruments]))]
                  [:th {:class "text-right"} (tr [:total])]]
                 [:tbody {:class "instrgrid--body"}
                  (map (fn [{:keys [member private-cost-total count-private]}]
                         (let [{:member/keys [name member-id]} member]
                           [:tr {:class "odd:bg-gray-200 even:bg-white"}
                            [:td {:class "px-2"}
                             [:input {:type "checkbox" :id "foo" :name "member-ids" :class "h-4 w-4 rounded border-gray-300 text-sno-orange-600 focus:ring-sno-orange-500"
                                      :value (str member-id)
                                      :checked "true"
                                      :_ "on click trigger checkboxChanged on #instr-select-all"}]]
                            [:td {:class "text-left px-2 py-1"} (ui/member member (urls/link-policy-table-member policy member))]
                            [:td {:class "text-right px-2"} count-private]
                            [:td {:class "text-right px-2"} (ui/money-cents private-cost-total :EUR)]]))

                       members-data)]]
                [:div {:class "mt-4"}
                 [:p "Example of what the email will look like:"]
                 [:div {:class "bg-gray-200 px-8 py-1  rounded-md prose"}
                  (email/render-insurance-debt-email-template req sender-name time-range (second members-data))]]
                [:div {:class "mt-4 text-right"}
                 [:input {:type :hidden :name "dummy" :value "dummy"}]
                 (ui/button :label (tr [:insurance/send-payment-notifications])
                            :id "send-payment"
                            :icon icon/envelope
                            :priority :primary)]])]))

(defn dashboard-survey-widget [{:keys [tr db] :as req} survey-response dismiss-endpoint-path dismiss-target]
  (let [{:member/keys [name] :as member} (:insurance.survey.response/member survey-response)
        open-items (q/open-survey-for-member-items db member)
        has-open-items? (not= 0 open-items)
        time-dur (java.lang.String/format java.util.Locale/GERMAN "%.2f" (to-array [(float (* open-items 0.75))]))]
    [:div {:class "flex items-center justify-center space-x-4"}
     [:div
      [:img {:class "cursor-pointer pbj-frozen hidden w-32 sm:w-16" :src "/img/peanut_butter_jelly_time_still.gif"
             :_ "on click remove .hidden from .pbj-live then add .hidden to me"}]
      [:img {:class "cursor-pointer pbj-live w-32 sm:w-16" :src "/img/peanut_butter_jelly_time.gif"
             :_ "on click remove .hidden from .pbj-frozen then add .hidden to me"}]]
     [:div
      [:p (tr [:insurance.survey/cta-p1] [name])]
      (if has-open-items?
        [:p (tr [:insurance.survey/cta-p2] [open-items time-dur])]
        [:p (tr [:insurance.survey/cta-p2-none])])
      [:div {:class "mt-2 flex items-center space-x-4"}
       (if has-open-items?
         (ui/link-button
          :href (urls/link-insurance-survey-start "123")
          :label (tr [:insurance.survey/start-button]) :priority :primary :icon icon/arrow-right)
         (ui/link-button
          :href (urls/link-insurance-add-coverage (-> survey-response :survey :insurance.survey/policy :insurance.policy/policy-id))
          :label (tr [:instrument.coverage/create-button]) :priority :primary))
       (when-not has-open-items?
         (ui/button :label (tr [:action/im-finished]) :priority :white :hx-post dismiss-endpoint-path :hx-target dismiss-target))]]]))

(defn band-or-private-bubble [tr private?]
  (ui/bool-bubble (not private?) {true "Band" false "Privat"}))

(defn kw->hint [kw]
  (keyword (namespace kw) (str (name kw) "-hint")))

(defn instrument-card-detail-data [tr k v]
  (let [tooltip-k (kw->hint k)
        tooltip (tr [tooltip-k])
        has-tooltip? (not= tooltip (tr [:missing]))]
    {:label (if has-tooltip? [:span {:class "tooltip underline decoration-dashed" :data-tooltip tooltip}
                              (tr [k])]
                (tr [k]))
     :value v}))

(defn instrument-card [{:keys [tr]} {:insurance.survey.report/keys [coverage]}]
  (let [{:instrument.coverage/keys [instrument private? value item-count cost types]} coverage
        {:instrument/keys [name build-year description make model serial-number category]} instrument
        _ (assert instrument "Instrument is required")
        _ (assert coverage "Coverage is required")
        item-image nil ;; TODO "/img/tuba-robot-boat-1000.jpg"
        make-detail (partial instrument-card-detail-data tr)
        ;; _ (tap> {:coverage coverage :value value :cost cost :instrument instrument})
        details [(make-detail :instrument/description description)
                 (make-detail :instrument/category (:instrument.category/name category))
                 (make-detail :instrument.coverage/private? (band-or-private-bubble tr private?))
                 (when (and private?)
                   (make-detail :instrument.coverage/cost
                                (if private?
                                  [:span  {:class "text-red-600 tooltip underline decoration-dashed" :data-tooltip (tr [:instrument.coverage/cost-hint-direct])} (ui/money-format cost :EUR)]
                                  (ui/money-format cost :EUR))))
                 (make-detail :instrument.coverage/value (ui/money-format value :EUR))
                 (make-detail :instrument.coverage/item-count (or item-count 1))
                 (make-detail :instrument.coverage/types (str/join ", " (map :insurance.coverage.type/name types)))
                 (make-detail :instrument/make make)
                 (make-detail :instrument/model model)
                 (make-detail :instrument/serial-number serial-number)
                 (make-detail :instrument/build-year build-year)]]

    [:li {:class "overflow-hidden rounded-xl border border-gray-200 divide-y divide-gray-200 "}
     [:div {:class "flex items-center gap-x-4 bg-gray-50 px-6 pt-3 pb-3"}
      #_[:img
         {:src "https://tailwindui.com/img/logos/48x48/tuple.svg",
          :alt "Tuple",
          :class
          "h-12 w-12 flex-none rounded-lg bg-white object-cover ring-1 ring-gray-900/10"}]
      [:div {:class "text-lg font-medium leading-6 text-gray-900"}
       name]]
     (when item-image
       [:div {:class "flex items-center justify-center"}
        [:img {:src item-image :class "w-full sm:w-1/2"}]])
     [:dl {:class " divide-y divide-gray-100 px-6 py-2 text-sm leading-6"}
      (map (fn [{:keys [label value]}]
             (when label
               [:div
                {:class "flex justify-between gap-x-4 py-3"}
                [:dt {:class "text-gray-500"} label]
                [:dd {:class "text-gray-700 text-right"}
                 value]]))
           details)]]))

(defn build-survey-flow [req]
  {:start-flow        :used
   :used          {:question {:primary "Was this used at a SNO gig in the past year?"}
                   :answers  [{:label "No" :icon icon/xmark  :next-flow-key :go-private :vals {:decisions [:confirm-not-band]}}
                              {:label "Yes" :icon icon/checkmark :next-flow-key :keep-insured :vals {:decisions [:confirm-band]}}]}
   :keep-insured {:question {:primary "Do you want to keep this instrument insured?"}
                  :answers  [{:label "No" :icon icon/xmark  :next-flow-key :confirm-band-removal}
                             {:label "Yes" :icon icon/checkmark :next-flow-key :data-check :vals {:decisions [:confirm-keep-insured]}}]}
   :confirm-band-removal {:question {:primary "Are you sure you want to remove the insurance?"
                                     :secondaries ["The band pays for the cost of the insurance since it is a band instrument."]}
                          :answers [{:label "No, keep the insurance" :next-flow-key :data-check}
                                    {:label "Yes, remove it" :next-flow-key :complete :vals {:decisions [:remove-coverage]}}]}
   :confirm-private-removal {:question {:primary "Are you sure you want to remove the insurance?"}
                             :answers [{:label "No, keep the insurance" :next-flow-key :confirm-go-private}
                                       {:label "Yes, remove it" :next-flow-key :complete :vals {:decisions [:remove-coverage]}}]}
   :go-private {:question {:primary "Do you want to pay to keep it insured?"
                           :secondaries ["Since this wasn't used at a gig in the last year you the band can no longer pay for it."
                                         (fn [{:keys [active-report]}]
                                           (let [coverage (:insurance.survey.report/coverage active-report)
                                                 cost (:instrument.coverage/cost coverage)]
                                             (format  "If you want the item to stay insured, you'll have to pay for it yourself. This will cost about %s€ per year for this item."
                                                      (ui/money-format cost :EUR))))]}
                :answers [{:label "No, stop insurance" :icon icon/xmark :next-flow-key :confirm-private-removal}
                          {:label "Yes, I'll pay" :icon icon/checkmark :next-flow-key :data-check :vals {:decisions [:confirm-keep-insured]}}]}
   :confirm-go-private {:question {:primary (fn [{:keys [active-report]}]
                                              (let [coverage (:insurance.survey.report/coverage active-report)
                                                    cost (:instrument.coverage/cost coverage)]
                                                (format  "You will have to pay about %s€ per year for this item. Is that OK?"
                                                         (ui/money-format cost :EUR))))}
                        :answers [{:label "No" :next-flow-key :go-private}
                                  {:label "Yes, I'll pay it" :next-flow-key :data-check :vals {:decisions [:confirm-keep-insured]}}]}
   :data-check {:question {:primary "Is all the data shown still correct?" :secondaries ["You might want to change the insured value if the market value has change or if you want to change the type of coverage."]}
                :answers  [{:label "No" :icon icon/xmark  :next-flow-key :data-edit}
                           {:label "Yes" :icon icon/checkmark  :next-flow-key :complete :vals {:decisions [:confirm-data-ok]}}]}
   :data-edit {:next-flow-key :complete}})

(defn valid-survey-flow-transition? [flow current-key next-key]
  (let [current-answers (get-in flow [current-key :answers])
        valid-answer-next-steps (set (map :next-flow-key current-answers))]
    #_(tap> {:current-answers current-answers :flow flow :curr current-key :next next-key :valid-steps valid-answer-next-steps :valid? (contains? valid-answer-next-steps next-key)})
    (contains? valid-answer-next-steps next-key)))

(declare survey-question-page)
(declare survey-start-page)
(declare survey-edit-page)

(defn survey-flow-invalid-transition []
  [:div "That isn't allowed"])

(defn prepare-next-active-report [{:keys [db] :as req}]
  (let [{:insurance.survey.response/keys [member response-id coverage-reports survey]} (controller/survey-response-for-member req)
        todo-reports (filter #(nil? (:insurance.survey.report/completed-at %)) coverage-reports)]
    {:active-report (first todo-reports)
     :todo-reports todo-reports
     :total-reports (count coverage-reports)
     :total-todo (count todo-reports)
     :current-idx  (inc (- (count coverage-reports) (count todo-reports)))
     :new-step? (some? (-> (util/unwrap-params req) :new-step))}))

(defn show-encouragement? [current-idx total-todo]
  (or
    ;; show on 2nd item, cause at least they did one
   (= current-idx 2)
    ;; otherwise show every 4th item, unless its the 2nd to last one
   (and (= 0 (mod current-idx 4))
        (not= current-idx (dec total-todo)))))

(defn survey-report-interstitial [{:keys [tr] :as req}]
  (let [{:keys [active-report todo-reports total-reports total-todo current-idx]} (prepare-next-active-report req)
        total-completed (- total-reports total-todo)
        {:insurance.survey.response/keys [member response-id coverage-reports survey]} (controller/survey-response-for-member req)]
    (if (= 0 total-todo)
      [:div {:id (util/id :comp/survey-page) :class (ui/cs "mx-auto max-w-2xl overflow-hidden")}
       [:div {:data-celebrate "true"}]
       [:div {:class "bg-white min-h-80 mt-8 gap-6 p-6 flex flex-col items-center justify-center"}
        [:div {:class "flex items-center justify-center text-sno-green-500"}
         (icon/shield-check-outline {:class "h-32 w-32"})
         [:h1 {:class "text-3xl font-bold"} (tr [:insurance.survey/all-done])]]
        [:div {:class "flex flex-col items-center"}
         (ui/button :size :xlarge  :label (tr [:action/celebrate]) :priority :white
                    :attr {:data-counter 0 :_
                           "on click call celebrate() then increment @data-counter
if @data-counter as Int is equal to 1 remove .opacity-0 from .stage-1
else if @data-counter as Int is equal to 2 remove .opacity-0 from .stage-2
else if @data-counter as Int is equal to 3 remove .opacity-0 from .stage-3 then remove .opacity-0 from .go-home "})

         [:p {:class "opacity-0 stage-1 pt-2 transition-opacity duration-1000"} "Why not celebrate again?"]]
        [:p {:class "opacity-0 stage-2 pt-2 transition-opacity duration-1000"} "Feels good right?"]
        [:p {:class "opacity-0 stage-3 pt-2 transition-opacity duration-1000"}
         "The Versicherungsteam Team thanks you"
         [:span {:class "text-red-500"} "❤️"]]
        [:div {:class "h-12"}
         (ui/link-button  :href "/" :size :xlarge :class "opacity-0 transition-opacity delay-1000 duration-1000 go-home" :label (tr [:error/go-home]) :priority :white)]]]

      (if (show-encouragement? current-idx total-todo)
        [:div {:id (util/id :comp/survey-page) :class (ui/cs "mx-auto max-w-2xl overflow-hidden")}
         [:form {:class (ui/cs "mx-auto mt-8 max-w-2xl gap-6 p-6 bg-white min-h-80") :hx-ext "class-tools"}
          [:div {:class "flex items-center space-x-2"}
           (ui/thumbs-up-animation)
           [:div {:class "transition-opacity opacity-0 hidden" :classes "remove hidden:1.1s & remove opacity-0:1.1s"}
            [:h1 {:class "text-2xl"} (tr [:good-job])]
            [:p {:class "opacity-0 transition-opacity" :classes "remove opacity-0:1.5s"}
             (if (> total-completed 1)
               (tr [:youve-completed-plural] [total-completed total-todo])
               (tr [:youve-completed-sing] [total-todo]))]
            [:div {:class "opacity-0 transition-opacity" :classes "remove opacity-0:1.5s"}
             (ui/button :label (tr [:action/keep-going]) :icon icon/arrow-right
                        :hx-get (util/endpoint-path survey-start-page)
                        :hx-target (util/hash :comp/survey-page)
                        :hx-vals {:new-step true})]]]]]
        (survey-start-page req)))))

(defn survey-flow-complete [{:keys [tr] :as req} flow decisions active-report]
  (let [decisions (set (map keyword decisions))]
    (controller/resolve-survey-report! req active-report decisions)
    (survey-report-interstitial (util/make-get-request req {:transitioning? true}))))

(ctmx/defcomponent ^:endpoint survey-edit-instrument-handler [{:keys [db tr] :as req}]
  (when (util/post? req)
    (let [result (controller/update-instrument-and-coverage! req)
          error (:error result)
          form-error (:form-error error)
          {:keys [decisions] :as params} (util.http/unwrap-params req)
          decisions (util/ensure-coll decisions)
          flow (build-survey-flow req)
          active-report (controller/survey-report-for-member req)]
      #_(tap> {:error error :form-error form-error :next-flow-key next-flow-key})
      (if error
        (survey-edit-page req decisions active-report error form-error)
        (survey-flow-complete (util/make-get-request req) flow decisions active-report)))))

(defn survey-edit-page [{:keys [tr] :as req} decisions active-report error form-error]
  (let [{:insurance.survey.report/keys [coverage]} active-report
        {:instrument.coverage/keys [instrument private? value item-count cost types coverage-id]} coverage
        {:instrument/keys [name build-year description make model serial-number instrument-id owner]} instrument
        policy (:insurance.policy/_covered-instruments coverage)
        coverage-types (:insurance.policy/coverage-types policy)]
    #_(tap> {:report active-report
             :coverage coverage
             :ins instrument
             :cts coverage-types
             :upload-url (urls/link-instrument-image-upload instrument-id)})
    [:form {:id (util/id :comp/survey-page-flow)
            :class "bg-white mx-auto max-w-2xl overflow-hidden p-6 mt-8 rounded-md"
            :hx-post (util/endpoint-path survey-edit-instrument-handler)
            :hx-target (if error
                         (util/hash :comp/survey-page-flow)
                         (util/hash :comp/survey-page))}
     [:input {:type :hidden :name "report-id" :value (str (:insurance.survey.report/report-id active-report))}]
     [:input {:type :hidden :name "policy-id" :value (str (:insurance.policy/policy-id policy))}]
     [:input {:type :hidden :name "instrument-id" :value (str instrument-id)}]
     [:input {:type :hidden :name "coverage-id" :value (str coverage-id)}]
     [:input {:type :hidden :name "owner-member-id" :value (str (:member/member-id owner))}]
     [:input {:type :hidden :name "private-band" :value (if private? "private" "band")}]
     (map (fn [v]
            [:input {:type :hidden :name :decisions :value v}]) decisions)
     (when form-error
       [:div
        [:p {:class "mt-2 text-right text-red-600"}
         (icon/circle-exclamation {:class "h-5 w-5 inline-block mr-2"})
         (tr [form-error])]])
     (instrument-form req error instrument {:hide-owner? true})
     [:div {:class "sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:border-t sm:border-gray-200 sm:py-5 mt-2"}
      [:div {:class "block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"} (tr [:instrument/photo-upload])]
      [:div {:class "mt-1 sm:col-span-2 sm:mt-0"}
       [:div {:class "relative max-w-lg sm:max-w-xs dropzone" :id "imageUpload"}
        (photo-upload-widget (urls/link-instrument-image-upload instrument-id))]]]
     (coverage-form req error coverage coverage-types {:hide-private? true})
     [:div {:class "flex justify-end"}
      (ui/button :label (tr [:action/save]) :priority :primary :spinner? true)]]))

(ctmx/defcomponent ^:endpoint survey-flow-progress [{:keys [db tr] :as req}]
  (let [{:keys [current-flow-key next-flow-key decisions] :as params} (util.http/unwrap-params req)
        decisions (util/ensure-coll decisions)
        next-flow-key (keyword next-flow-key)
        current-flow-key (keyword current-flow-key)
        flow (build-survey-flow req)
        valid-transition? (valid-survey-flow-transition? flow current-flow-key next-flow-key)
        active-report (controller/survey-report-for-member req)]
    ;; (tap> [:current-flow-key current-flow-key :next-flow-key next-flow-key :params params :active active-report :valid? valid-transition?])
    (if valid-transition?
      (condp = next-flow-key
        :complete (survey-flow-complete req flow decisions active-report)
        :data-edit (survey-edit-page req decisions active-report nil nil)
        (survey-question-page req flow decisions active-report next-flow-key))
      (survey-flow-invalid-transition))))

(defn- survey-answer-button
  [decisions endpoint {:keys [label icon vals next-flow-key]}]
  (let [decisions (concat (:decisions vals) decisions)
        final-vals {:next-flow-key next-flow-key}
        final-vals (assoc final-vals :decisions decisions)]
    ;; (tap> [:combined-decisions decisions :old-de decisions :new-de (:decisions vals) :vals final-vals])
    (ui/button :label label :priority :white :icon icon
               :spinner? true
               ;; :disabled? true
               :hx-target (if (= :complete next-flow-key)
                            (util/hash :comp/survey-page)
                            (util/hash :comp/survey-page-flow))
               :hx-post endpoint
               :hx-vals final-vals

               :attr {;; :_            "on load wait 1s then set @disabled to null end"
                      ;; for when using view transition api
                      ;; :hx-swap      "innerHTML transition:true"
                      :hx-swap      "outerHTML swap:0.6s"})))

(defn survey-question-page [{:keys [tr] :as req} flow decisions active-report current-flow-key]
  (let [{:insurance.survey.response/keys [member response-id coverage-reports survey]} (controller/survey-response-for-member req)
        transitioning? (:transitioning? req)
        todo-reports                                                                   (filter #(nil? (:insurance.survey.report/completed-at %)) coverage-reports)
        text-params {:tr tr :active-report active-report}
        {:keys [question answers]}                                                     (get flow current-flow-key)
        {:keys [primary secondaries]}                                                  question]
    [:div {:id (util/id :comp/survey-page-flow) :class (ui/cs  "mx-auto max-w-2xl overflow-hidden")}
     [:form {:class (ui/cs "mx-auto mt-8 max-w-2xl gap-6 p-6 bg-white rounded-md" (when transitioning? "slide-me-in-out"))}
      [:input {:type :hidden :name "report-id" :value (str (:insurance.survey.report/report-id active-report))}]
      [:input {:type :hidden :name "current-flow-key" :value current-flow-key}]
      [:div {:id "survey-card-question"
             ;; for when using view transition api
             ;; :class "slide-it"
             :class "slide-it slide-me-in-out"}
       [:p {:class "text-center text-lg"}
        (if (fn? primary)
          (primary text-params)
          primary)]
       (map (fn [s]
              [:p {:class "text-center text-gray-500"}
               (if (fn? s)
                 (s text-params)
                 s)]) secondaries)
       [:div {:class "flex gap-x-2 items-center justify-center mt-2"}
        (map (partial survey-answer-button decisions (util/endpoint-path survey-flow-progress)) answers)]
       [:div {:class "htmx-indicator pulsate text-center text-gray-500 mt-2"} (tr [:updating])]]
      [:ul {:role "list" :class "grid grid-cols-1 gap-x-6 gap-y-8 mb-2"}
       (instrument-card req active-report)]]]))

(ctmx/defcomponent ^:endpoint survey-start-page [{:keys [db tr] :as req}]
  survey-flow-progress
  survey-edit-instrument-handler
  (let [{:as data :keys [active-report todo-reports total-reports total-todo current-idx new-step?]} (prepare-next-active-report req)
        {:keys [start-flow] :as flow} (build-survey-flow req)]
    (if (= 0 total-todo)
      (survey-report-interstitial (util/make-get-request req {:transitioning? true}))
      [:div {:id :comp/survey-page}
       [:div {:class (ui/cs "flex justify-center items-center mt-10" (when new-step? "steps-new-step"))}
        (ui/step-circles total-reports current-idx)]
       [:div {:class "survey-panel-fade-in"}
        (survey-question-page req flow [] active-report start-flow)]])))
