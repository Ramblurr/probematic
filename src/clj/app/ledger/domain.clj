(ns app.ledger.domain
  (:require
   [taoensso.nippy :as nippy]
   [app.schemas :as s]
   [medley.core :as m]
   [tick.core :as t]))

(def entry-meta-types #{:ledger.entry.meta.type/insurance})

(def LedgerEntryMetadataEntity
  (s/schema
   [:map {:name :app.entity/ledger.entry.meta}
    [:ledger.entry.meta/meta-type (s/enum-from entry-meta-types)]]))

(def LedgerEntryEntity
  (s/schema
   [:map {:name :app.entity/ledger.entry}
    [:ledger.entry/entry-id :uuid]
    [:ledger.entry/amount :int]
    [:ledger.entry/description ::s/non-blank-string]
    [:ledger.entry/posting-date ::s/inst]
    [:ledger.entry/tx-date ::s/date]
    [:ledger.entry/metadata {:optional true} LedgerEntryMetadataEntity]]))

(def LedgerEntity
  (s/schema
   [:map {:name :app.entity/ledger}
    [:ledger/ledger-id :uuid]
    [:ledger/owner ::s/datomic-ref]
    [:ledger/entries {:optional true} [:sequential ::s/datomic-ref]]
    [:ledger/balance :int]]))

(defn entry->db [entry]
  (-> entry
      (update :ledger.entry/posting-date #(t/inst (t/in % (t/zone "Europe/Vienna"))))
      (update :ledger.entry/tx-date str)
      (m/update-existing :ledger.entry/data nippy/freeze)))

(defn db->entry
  [entry]
  (when entry
    (-> (s/decode-datomic LedgerEntryEntity entry)
        (m/update-existing :ledger.entry/posting-date t/date-time)
        (m/update-existing :ledger.entry/tx-date t/date))))

(defn db->ledger [ent]
  (when ent
    (update ent :ledger/entries #(->> %
                                      (map db->entry)
                                      (sort-by (juxt :ledger.entry/tx-date :ledger.entry/posting-date))
                                      (reverse)))))

(defn new-member-ledger-datom [tmpid ledger-id owner]
  {:db/id tmpid
   :ledger/ledger-id ledger-id
   :ledger/owner owner
   :ledger/balance 0})
