(ns app.ledger.domain
  (:require
   [app.schemas :as s]
   [medley.core :as m]
   [tick.core :as t]))

(def LedgerEntryEntity
  (s/schema
   [:map {:name :app.entity/ledger.entry}
    [:ledger.entry/entry-id :uuid]
    [:ledger.entry/amount :int]
    [:ledger.entry/description ::s/non-blank-string]
    [:ledger.entry/tx-date ::s/inst]]))

(def LedgerEntity
  (s/schema
   [:map {:name :app.entity/ledger}
    [:ledger/ledger-id :uuid]
    [:ledger/owner ::s/datomic-ref]
    [:ledger/entries {:optional true} [:sequential ::s/datomic-ref]]
    [:ledger/balance :int]]))

(defn entry->db [entry]
  (update entry :ledger.entry/tx-date #(t/inst (t/in % (t/zone "Europe/Vienna")))))

(defn db->entry
  [entry]
  (when entry
    (-> (s/decode-datomic LedgerEntryEntity entry)
        (m/update-existing :ledger.entry/tx-data t/date-time))))

(defn db->ledger [ent]
  (when ent
    (update ent :ledger/entries #(->> %
                                      (map db->entry)
                                      (sort-by :ledger.entry/tx-date)
                                      (reverse)))))
