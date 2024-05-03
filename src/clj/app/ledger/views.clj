(ns app.ledger.views
  (:require
   [clojure.string :as str]
   [app.qrcode :as qr]
   [app.auth :as auth]
   [app.i18n :as i18n]
   [app.icons :as icon]
   [app.ledger.domain :as domain]
   [app.ledger.controller :as controller]
   [app.queries :as q]
   [app.ui :as ui]
   [app.urls :as urls]
   [app.util :as util]
   [app.util.http :as util.http]
   [ctmx.core :as ctmx]
   [ctmx.response :as response]
   [ctmx.rt :as rt]
   [hiccup.util :as hiccup.util]
   [medley.core :as m]
   [tick.core :as t]
   [clojure.set :as cset]
   [app.config :as config]))

(defn payment-direction-button [{:keys [label direction type]}]
  (ui/button :label label
             :attr {:_ (format  "on click set .tx-direction@value to '%s'  then remove .hidden from .tx-type-%s-%s then remove .hidden from .tx-add-form then add .hidden to .choose-tx-type2" direction type direction)}))

(defn add-transaction-form [{:keys [db tr] :as req} {:member/keys [name member-id] :as member} hx-target endpoint-path]
  (let [cancel-button (ui/button :priority :white
                                 :label (tr [:action/cancel])
                                 :attr {:type :button :_ "on click add .hidden to .tx-add-form then remove .hidden from .choose-tx-type then add .hidden to .tx-type-label then add .hidden to .choose-tx-type2"})]
    [:div {:class "flex border-t border-gray-100 pt-6"}
     [:div {:class "tx-add-form hidden"}
      [:div {:class "pb-12 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:pb-0"}
       [:div {:class "sm:grid sm:grid-cols-4 sm:items-start sm:gap-4"}
        [:form {:class "col-span-2 flex flex-col space-y-4"
                :hx-target hx-target :hx-post endpoint-path}
         [:div {:class ""}
          [:span {:class "hidden tx-type-label tx-type-debt-debit text-red-700"} (tr [:ledger.entry/direction-debit] [name])]
          [:span {:class "hidden tx-type-label tx-type-debt-credit text-green-700"}  (tr [:ledger.entry/direction-credit] [name])]
          [:span {:class "hidden tx-type-label tx-type-payment-debit text-red-700"}  (tr [:ledger.entry/payment-direction-debit] [name])]
          [:span {:class "hidden tx-type-label tx-type-payment-credit text-green-700"}  (tr [:ledger.entry/payment-direction-credit] [name])]]
         (ui/date2 :name "tx-date" :label (tr [:ledger.entry/tx-date]) :required? true)
         (ui/text :name "description" :label (tr [:ledger.entry/description]) :required? true)
         (ui/money-input tr :name "amount" :label (tr [:ledger.entry/amount]) :label-hint (tr [:insurance/value-hint]) :required? true  :integer? false)
         [:input {:class "tx-direction" :type :hidden :name "tx-direction" :value nil :required "true"}]
         [:input {:type :hidden :name "member-id" :value (str member-id)}]
         [:div {:class "self-end flex space-x-4 mt-4"}
          cancel-button
          (ui/button :priority :primary
                     :icon icon/plus
                     :label (tr [:action/save]))]]]]]
     [:div {:class "choose-payment-type choose-tx-type2 hidden flex flex-col space-y-2"}
      (map payment-direction-button
           [{:label (tr [:ledger.entry/payment-direction-credit] [name]) :direction "credit" :type "payment"}
            {:label (tr [:ledger.entry/payment-direction-debit] [name]) :direction "debit" :type "payment"}])
      cancel-button]
     [:div {:class "choose-debt-type choose-tx-type2 hidden flex flex-col space-y-2"}
      (map payment-direction-button
           [{:label (tr [:ledger.entry/direction-credit] [name]) :direction "credit" :type "debt"}
            {:label (tr [:ledger.entry/direction-debit] [name]) :direction "debit" :type "debt"}])
      cancel-button]

     [:div {:class "choose-tx-type flex space-x-4"}
      (ui/button :attr {:_ "on click remove .hidden from .choose-debt-type then add .hidden to .choose-tx-type"}
                 :icon icon/plus
                 :label (tr [:ledger/add-debt]))
      (ui/button :attr {:_ "on click remove .hidden from .choose-payment-type then add .hidden to .choose-tx-type"}
                 :icon icon/plus
                 :label (tr [:ledger/add-payment]))]]))

(defn maybe-transfer-reference [balance entries]
  (when (=  balance (:ledger.entry/amount (first entries)))
    (:ledger.entry/description (first entries))))

(defn outstanding-balance-widget [{:keys [tr] :as req} {:ledger/keys [balance entries] :as ledger}]
  (let [member-owes-band? (> balance 0)
        band-owes-member? (< balance 0)
        {:keys [iban bic account-name]} (config/band-bank-info (-> req :system :env))]
    [:div {:class "pb-4 sm:flex sm:items-top sm:space-x-5"}
     [:div
      [:div {:class "text-sm font-medium text-gray-500"} (tr [:outstanding-balance])]
      [:div {:class (ui/cs (if member-owes-band? "text-red-700" "text-green-700") " text-2xl")} (ui/money-cents balance :EUR)]
      [:div {:class "text-sm font-medium"} [:a {:class "link-blue" :href (urls/link-member-ledger-table (:ledger/owner ledger)) } "Why?"]]]
     [:div
      (when band-owes-member?
        [:div {:class "flex items-baseline"}
         (icon/circle-exclamation {:class "h-4 w-4 text-orange-500 relative top-0.5 mr-2"})
         [:div
          [:p {:class "mb-4"} (tr [:band-owes-you] [(ui/money-cents-format balance :EUR)])]]])
      (when member-owes-band?
        [:div {:class "flex items-baseline"}
         (icon/circle-exclamation {:class "h-4 w-4 text-orange-500 relative top-0.5 mr-2"})
         [:div
          [:p {:class "mb-4"} (tr [:please-pay-to-band] [(ui/money-cents-format balance :EUR)])]
          [:p "Name: " [:span {:class "font-semibold"}] account-name]
          [:p {:class "my-2"} "IBAN: " [:span {:class "font-semibold"} (ui/iban iban)]]
          [:p "BIC: " [:span {:class "font-semibold"} bic]]
          [:p {:class "mt-4"} (tr [:or-scan-qr-code])]]])]
     (when member-owes-band?
       [:div
        [:img {:src
               (qr/image-to-data-uri (qr/qr (qr/sepa-payment-code {:bic bic :iban iban :name account-name :amount (/ balance 100) :unstructured-reference (maybe-transfer-reference balance entries)}) 300 300))}]])]))

(defn ledger-table [{:keys [tr db] :as req} {:member/keys [member-id]}]
  (if-let [ledger (q/retrieve-ledger db member-id)]
    (let [entries (:ledger/entries ledger)]
      [:div
       (outstanding-balance-widget req ledger)

       [:table {:class "table-auto text-left w-full mt-2" :id "member-ledger-table"}
        [:thead {:class "border-b border-gray-200 bg-gray-300"}
         [:tr {:class "isolate py-2 font-semibold"}
          [:th (tr [:ledger.entry/tx-date])]
          [:th (tr [:ledger.entry/description])]
          [:th {:class "text-right"} (tr [:ledger.entry/amount])]]]
        [:tbody
         (when-not (seq entries)
           [:div (tr [:no-transactions-yet])])
         (map (fn [{:ledger.entry/keys [amount description tx-date entry-id]}]
                [:tr
                 [:td
                  {:class ""}
                  [:div {:class "text-sm font-medium leading-6 text-gray-900"} [:time {:dateetime (str tx-date)} (ui/format-dt tx-date "dd.MM.YY")]]
                  [:div
                   {:class "mt-1 text-xs leading-5 text-gray-500"}]]
                 [:td
                  {:class ""}
                  [:div {:class "text-sm leading-6 text-gray-900"} description]
                  [:div
                   {:class "mt-1 text-xs leading-5 text-gray-500"}]]
                 [:td {:class "text-right"}
                  [:div {:class (ui/cs "text-sm leading-6" (if (> amount 0) "text-red-700" "text-green-700"))} (ui/money-cents amount :EUR)]
                  [:div
                   {:class "mt-1 text-xs leading-5 text-gray-500"}]]]) entries)]]])
    [:p (tr [:no-transactions-yet])]))

(declare member-ledger-panel)

(ctmx/defcomponent ^:endpoint form-transaction-handler [{:keys [db] :as req}]
  (when (util/post? req)
    (let [{:keys [error member]} (controller/add-transaction! req)]
      (if error
        (tap> [:error error])
        (member-ledger-panel (util/make-get-request req) member)))))

(ctmx/defcomponent member-ledger-panel [{:keys [db] :as req} member]
  form-transaction-handler
  (ui/panel {:id :comp/member-ledger-panel
             :title "Money Stuff"
             :subtitle
             [:span  "What " [:span {:class "text-red-700"} "you owe the band (+)"]  " and what " [:span {:class "text-green-700"} "the band owes you (-)"]]}
            [:div
             (ledger-table req member)
             (add-transaction-form req member (util/hash :comp/member-ledger-panel) (util/endpoint-path form-transaction-handler))]))
