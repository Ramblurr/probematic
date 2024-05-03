(ns app.ledger.controller
  (:require
   [app.ledger.domain :as domain]
   [app.config :as config]
   [app.datomic :as d]
   [app.errors :as errors]
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

(defn prepare-transaction-data
  "Takes the raw transaction data and makes it ready use with d/transact"
  [db data]
  data)

(defn balance-adjustment-datoms
  "Calculates the adjustment datoms for the specified transaction"
  [ledger entry]
  (let [balance-before (:ledger/balance ledger)
        entry-amount (:ledger.entry/amount entry)
        balance-after (+ balance-before entry-amount)]
    [[:db/add (d/ref ledger) :ledger/entries "new-ledger-entry"]
     [:db/add (d/ref ledger) :ledger/balance balance-after]]))

(defn append-balance-adjustment-datoms
  "Appends the datomic transaction commands necessary to adjust balances
  for the transaction"
  [ledger transaction]
  (concat [transaction] (balance-adjustment-datoms ledger transaction)))

(defn new-member-ledger-datom [member]
  {:db/id (str (:member/member-id member))
   :ledger/ledger-id (sq/generate-squuid)
   :ledger/owner (d/ref member)
   :ledger/balance 0})

(defn new-member-ledger! [conn member]
  (if-let [ledger (q/retrieve-ledger (datomic/db conn) (:member/member-id member))]
    ledger
    (let [result (datomic/transact conn {:tx-data [(new-member-ledger-datom member)]})]
      (q/retrieve-ledger (:db-after result) (:member/member-id member)))))

(defn post-transaction! [conn member entry]
  (if (s/valid? domain/LedgerEntryEntity entry)
    (let [tmpid "new-ledger-entry"
          ledger (new-member-ledger! conn member)
          db (datomic/db conn)
          tx-data (->> (domain/entry->db  entry)
                       (prepare-transaction-data db)
                       (merge {:db/id tmpid})
                       (append-balance-adjustment-datoms ledger))]
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
            {:keys [db-after] :as result-or-error} (post-transaction! datomic-conn member entry)]
        (if db-after
          {:ledger (q/retrieve-ledger db-after member-id)
           :member (q/retrieve-member db-after member-id)}
          result-or-error))
      {:error (s/explain-human AddTransaction decoded)})))

(defn delete-ledger-entry! [req])
(defn update-ledger-entry! [req])

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn))) ;; rcf

  (def albert (q/retrieve-member db #uuid "01860c2a-2929-8727-af1a-5545941b1115"))

  (q/retrieve-ledger db (:member/member-id albert))
  (new-member-ledger! conn albert)

  (s/valid? domain/LedgerEntryEntity {:ledger.entry/amount 1000
                                      :ledger.entry/tx-date (t/now)
                                      :ledger.entry/description "Testing"
                                      :ledger.entry/entry-id (sq/generate-squuid)})

  (post-transaction! conn albert {:ledger.entry/amount 1000
                                  :ledger.entry/tx-date (t/inst)
                                  :ledger.entry/description "Testing"
                                  :ledger.entry/entry-id (sq/generate-squuid)})

  (post-transaction! conn albert {:ledger.entry/amount -500
                                  :ledger.entry/tx-date (t/inst)
                                  :ledger.entry/description "Payment for testing"
                                  :ledger.entry/entry-id (sq/generate-squuid)})

  ;;
  )
