(ns app.ledger.views
  (:require
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
   [clojure.set :as cset]))

(defn ledger-table [{:keys [tr db] :as req} {:member/keys [member-id]}]
  (if-let [{:ledger/keys [balance] :as ledger} (q/retrieve-ledger db member-id)]
    (let [entries (:ledger/entries ledger)
          member-owes-band? (> balance 0)]

      [:div
       [:div {:class "flex justify-between items-center pb-2 border-b border-gray-200"}
        [:div
         [:div {:class "text-sm font-medium text-gray-500"}
          "Outstanding Balance"]
         [:div {:class (ui/cs (if member-owes-band? "text-red-700" "text-green-700") " text-lg")} (ui/money-cents balance :EUR)]]]

       [:table {:class "table-auto text-left w-full mt-2"}
        [:thead {:class "border-b border-gray-200 bg-gray-300"}
         [:tr {:class "isolate py-2 font-semibold"}
          [:th "Date"]
          [:th "Transaction"]
          [:th {:class "text-right"} "Amount"]]]
        [:tbody
         (when-not (seq entries)
           [:div "No transactions yet."])
         (map (fn [{:ledger.entry/keys [amount description tx-date entry-id]}]
                [:tr
                 [:td
                  {:class ""}
                  [:div {:class "text-sm font-medium leading-6 text-gray-900"} [:time {:dateetime (str tx-date)} (ui/format-dt tx-date "dd.MM.YY")] ]
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
    [:p "No transactions yet."]
    ))
