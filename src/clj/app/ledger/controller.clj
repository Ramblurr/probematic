(ns app.ledger.controller
  (:require
   [app.config :as config]
   [app.datomic :as d]
   [app.errors :as errors]
   [app.ledger.domain :as domain]
   [app.queries :as q]
   [app.schemas :as s]
   [app.urls :as urls]
   [app.util :as util]
   [app.util.http :as util.http]
   [clojure.string :as str]
   [com.yetanalytics.squuid :as sq]
   [datomic.client.api :as datomic]
   [malli.util :as mu]
   [medley.core :as m]
   [tick.core :as t]))

(defn balance-adjustment-datoms
  "Calculates the adjustment datoms for the specified transaction"
  [ledger entry]
  (let [balance-before (:ledger/balance ledger)
        entry-amount (:ledger.entry/amount entry)
        balance-after (+ balance-before entry-amount)]
    [[:db/add (d/ref ledger) :ledger/entries (:db/id entry)]
     [:db/add (d/ref ledger) :ledger/balance balance-after]]))

(defn append-balance-adjustment-datoms
  "Appends the datomic transaction commands necessary to adjust balances
  for the transaction"
  [ledger transaction]
  (concat [transaction] (balance-adjustment-datoms ledger transaction)))

(defn append-entry-metadata-datoms [metadata entry-tmpid datoms]
  (if metadata
    (concat datoms
            [(merge metadata {:db/id "metadata"})
             [:db/add entry-tmpid :ledger.entry/metadata "metadata"]])
    datoms))

(defn new-member-ledger! [conn {:member/keys [member-id] :as member}]
  (if-let [ledger (q/retrieve-ledger (datomic/db conn) member-id)]
    ledger
    (let [result (datomic/transact conn {:tx-data [(domain/new-member-ledger-datom (str member-id) (sq/generate-squuid) (d/ref member))]})]
      (q/retrieve-ledger (:db-after result) member-id))))

(defn prepare-transaction-data
  "Takes the raw transaction data and makes it ready to use with d/transact"
  [db ledger entry metadata]
  (assert (s/valid? domain/LedgerEntryEntity entry))
  (assert ledger)
  (let [entry-tmpid (d/tempid)]
    (->> (domain/entry->db entry)
         (merge {:db/id entry-tmpid})
         (append-balance-adjustment-datoms ledger)
         (append-entry-metadata-datoms metadata entry-tmpid))))

(defn post-transaction! [conn member entry metadata]
  (if (s/valid? domain/LedgerEntryEntity entry)
    (let [ledger (new-member-ledger! conn member)
          db (datomic/db conn)
          tx-data (prepare-transaction-data db ledger entry metadata)]
      (tap> [:tx-data tx-data])
      (datomic/transact conn {:tx-data tx-data}))
    {:error (s/explain-human domain/LedgerEntryEntity entry) :ledger-entry entry}))

(defn coerce-amount [amount-str]
  (when-let [num (util/parse-number amount-str)]
    (-> num
        (* 100)
        (double)
        (Math/round)
        (int))))

(def AddTransaction
  "This schema describes the http post we receive when creating a transaction"
  (s/schema
   [:map {:name ::AddTransaction}
    [:member-id :uuid]
    [:tx-date ::s/date]
    [:description :string]
    [:amount {:decode/string coerce-amount} :int]
    [:tx-direction [:enum "debit" "credit"]]]))

(s/explain-human AddTransaction
                 (s/decode AddTransaction {:member-id "01860c2a-2929-8727-af1a-5545941b1115"
                                           :tx-date "2021-09-01"
                                           :description "Testing"
                                           :amount "10.00"
                                           :tx-direction "debit"}))

(defn adjust-amount-sign [direction amount]
  (assert (> amount 0) "Amount must be positive")
  (if (= direction "credit")
    (- amount)
    amount))

(defn add-transaction! [{:keys [db datomic-conn] :as req}]
  (let [decoded (util/remove-nils (s/decode AddTransaction (util.http/unwrap-params req)))]
    (tap> [:decoded decoded])
    (if (s/valid? AddTransaction decoded)
      (let [member-id (:member-id decoded)
            member (q/retrieve-member db member-id)
            entry {:ledger.entry/amount (adjust-amount-sign (:tx-direction decoded) (:amount decoded))
                   :ledger.entry/tx-date (:tx-date decoded)
                   :ledger.entry/posting-date (t/inst)
                   :ledger.entry/description (:description decoded)
                   :ledger.entry/entry-id (sq/generate-squuid)}
            _ (tap> [:post entry :member member])
            {:keys [db-after] :as result-or-error} (post-transaction! datomic-conn member entry nil)]
        (if db-after
          {:ledger (q/retrieve-ledger db-after member-id)
           :member (q/retrieve-member db-after member-id)}
          result-or-error))
      {:error (s/explain-human AddTransaction decoded)})))


(defn prepare-delete-transaction-datoms [ {:ledger.entry/keys [amount entry-id] :as entry}]
  (let [{:ledger/keys [balance entries] :as ledger} (first (:ledger/_entries entry))]
    (assert ledger "Ledger must be present")
    [[:db/retractEntity [:ledger.entry/entry-id entry-id]]
     [:db/add (d/ref ledger) :ledger/balance (- balance amount)]]) )

(defn delete-ledger-entry! [{:keys [db member] :as req}]
  (let [params (util.http/unwrap-params req)
        entry-id (util/ensure-uuid! (:ledger-entry-id params))
        tx-data (prepare-delete-transaction-datoms (q/retrieve-ledger-entry db entry-id) ) ]
    (try
      (d/transact-wrapper! req {:tx-data tx-data})
      {:member member}
      (catch clojure.lang.ExceptionInfo e
        (errors/report-error! e)
        {:error (ex-data e)
         :exception e
         :msg (ex-message e)}))))

(defn update-ledger-entry! [req])

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn))) ;; rcf

  (def albert (q/retrieve-member db #uuid "01860c2a-2929-8727-af1a-5545941b1115"))

  (def maggo-ledger (q/retrieve-ledger db #uuid "01860c2a-2929-8727-af1a-5545941b110f"))
  (datomic/transact conn {:tx-data [[:db/add (d/ref maggo-ledger) :ledger/balance 0 ]]})
  (:ledger/_entries
    (q/retrieve-ledger-entry db #uuid "018f4031-2d54-822a-a7a2-2fa0cd39847c"))

  (q/retrieve-ledger db (:member/member-id albert))
  (new-member-ledger! conn albert)

  (s/valid? domain/LedgerEntryEntity {:ledger.entry/amount       1000
                                      :ledger.entry/tx-date      (t/date)
                                      :ledger.entry/posting-date (t/inst)
                                      :ledger.entry/description  "prepared"
                                      :ledger.entry/entry-id     (sq/generate-squuid)})

  (post-transaction! conn albert
                     {:ledger.entry/amount       1000
                      :ledger.entry/tx-date      (t/date)
                      :ledger.entry/posting-date (t/inst)
                      :ledger.entry/description  "prepared"
                      :ledger.entry/entry-id     (sq/generate-squuid)} nil)
  (post-transaction! conn albert {:ledger.entry/amount 500
                                  :ledger.entry/tx-date (t/date)
                                  :ledger.entry/posting-date (t/inst)
                                  :ledger.entry/description "prepared"
                                  :ledger.entry/entry-id (sq/generate-squuid)}
                     {:ledger.entry.meta/meta-type :ledger.entry.meta.type/insurance
                      :ledger.entry.meta.insurance/policy [:insurance.policy/policy-id #uuid "018e15c2-ddbe-8b4f-b814-bddd26f8aec2"]})

  (q/find-ledger-entries-insurance db (:member/member-id albert))
  (q/ledger-entry-debit-for-policy db (:member/member-id albert) #uuid "018e15c2-ddbe-8b4f-b814-bddd26f8aec2")
  (q/ledger-entry-debit-for-policy db (:member/member-id albert) #uuid "018f1059-4acd-86c1-8f55-4b3e5eb856e0")

  ;;
  )
