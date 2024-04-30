(ns app.datomic
  (:refer-clojure :exclude [ref])
  (:require
   [app.auth :as auth]
   [app.config :as config]
   [app.demo :as demo]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [com.yetanalytics.squuid :as sq]
   [datomic.client.api :as d]
   [integrant.repl.state :as state]
   [medley.core :as m]))

(defn ident-has-attr?
  [db ident attr]
  (contains? (d/pull db {:eid ident :selector '[*]}) attr))

(defn uuid-for-seed [tx]
  (if-let [uuid-key (:uuid-key tx)]
    (-> tx
        (dissoc :uuid-key)
        (assoc uuid-key (sq/generate-squuid)))
    tx))

(defn last-transaction-time [db]
  (first (ffirst
          (d/q
           '[:find (max 1 ?tx)
             :where
             [?tx :db/txInstant]]
           db))))

(defn transact [conn opts]
  (try
    (d/transact conn opts)
    (catch clojure.lang.ExceptionInfo e
      {:error (ex-data e)
       :exception e
       :msg (ex-message e)})))

(defn audit-txs [req comment]
  (filterv #(some? %)
           [[:db/add "datomic.tx" :audit/user [:member/member-id (:member/member-id (auth/get-current-member req))]]
            (when comment [:db/add "datomic.tx" :audit/comment comment])]))

(defn transact-wrapper!
  ([req opts]
   (transact-wrapper! req opts nil))
  ([{:keys [datomic-conn] :as req} opts comment]
   (d/transact datomic-conn (update opts :tx-data concat (audit-txs req comment)))))

(defn expand-audit-user [db {:audit/keys [user] :as audit}]
  (assoc-in audit [:audit/member] (d/pull db [:member/member-id :member/username :member/name :member/nick] (:db/id user))))

(defn ident [db attr]
  (:db/ident (d/pull db '[:db/ident] attr)))

(defn ref? [db attr]
  (= :db.type/ref
     (->
      (d/pull db '[*] attr)
      :db/valueType
      :db/ident)))

(defn resolve-ref [db eid]
  (d/pull db '[*] eid))


(defn entity-history [db id-key id-value]
  (->> (d/q '{:find [?tx ?attr ?val ?added]
              :in [$ ?id-key ?id-value]
              :where [
                      [?e ?id-key ?id-value]
                      [?e ?attr ?val ?tx ?added]]}
            (d/history db)
            id-key id-value)
       (group-by first)
       (map (fn [[tx transactions]]
              (let [tx-info (d/pull db '[*] tx)]
                {:timestamp (:db/txInstant tx-info)
                 :ent-id-key id-key
                 :ent-id-value id-value
                 :tx-id (:db/id tx-info)
                 :audit (expand-audit-user db (select-keys tx-info [:audit/user :audit/comment]))
                 :changes (->> transactions
                               (map (fn [[_ attr val added]]
                                      [(ident db attr) (if (ref? db attr)
                                                         (resolve-ref db val)
                                                         val) (if added :added :retracted)]))
                               (sort-by last))})))
       (sort-by :timestamp)))

(defn unique-error? [error]
  (= :db.error/unique-conflict (-> error :error :db/error)))

(defn db-error? [result]
  (contains? result :error))

(defn db-ok? [result]
  (let [ok? (not (db-error? result))]
    (if ok? true
        (do
          ;; (tap> result)
          false))))

(defn pull-many
  "Warning: can result in many requests if the peer is remote"
  [db pattern eids]
  (map (partial d/pull db pattern) eids))

(defn find-all
  "Returns a list of all entities having attr"
  [db attr pattern]
  (d/q '[:find (pull ?e pattern)
         :in $ ?attr pattern
         :where
         [?e ?attr ?v]]
       db attr pattern))

(defn count-all
  "Returns the number of entities having attr"
  [db attr]
  (let [result
        (ffirst (d/q '[:find (count ?e)
                       :in $ ?attr
                       :where [?e ?attr ?v]]
                     db attr))]
    (if (nil? result)
      0
      result)))

(defn count-by
  "Count the number of entities possessing attribute attr"
  [db attr]
  (->> (d/q '[:find (count ?e)
              :in $ ?attr
              :where [?e ?attr]]
            db attr)
       ffirst))

(defn find-all-by
  "Returns the entities having attr and val"
  [db attr attr-val pattern]
  (d/q '[:find (pull ?e pattern)
         :in $ ?attr ?val pattern
         :where [?e ?attr ?val]]
       db attr attr-val pattern))

(defn find-by
  "Returns the unique entity identified by attr and val."
  [db attr attr-val pattern]
  (ffirst (find-all-by db attr attr-val pattern)))

(defn match-by
  "Returns the unique entity identified by attr and matching val-pattern"
  [db attr val-pattern pattern]
  (ffirst
   (d/q '[:find (pull ?e pattern)
          :in $ ?attr ?match pattern
          :where
          [(str "(?i)" ?match) ?matcher]
          [(re-pattern ?matcher) ?regex]
          [(re-find ?regex ?aname)]
          [?e ?attr ?aname]]
        db attr val-pattern pattern)))

(def entity-ids [:gig/gig-id
                 :gig/gigo-id
                 :member/member-id
                 :member/gigo-key
                 :song/song-id
                 :insurance.policy/policy-id
                 :played/play-id
                 :instrument.category/category-id
                 :instrument/instrument-id
                 :insurance.coverage.type/type-id
                 :instrument.coverage/coverage-id
                 :insurance.category.factor/category-factor-id
                 :comment/comment-id
                 :poll/poll-id
                 :poll.option/poll-option-id
                 :poll.vote/poll-vote-id
                 :attendance/gig+member])

(defn ref
  "Given a map and a key returns a tuple of [key value]. Useful for building datomic ref tuples from a pull result"
  ([m]
   (if-let [k (m/find-first #(contains? m %) entity-ids)]
     (ref m k)
     (throw (ex-info "entity map does not contain a known id key" {:entity-map m
                                                                   :possible-id-keys entity-ids}))))

  ([m k]
   [k (k m)]))

(def q d/q)

(defn load-dataset
  [env conn]
  (let [db (d/db conn)
        tx #(d/transact conn {:tx-data %})]
    (tx (-> (io/resource "schema.edn") slurp edn/read-string))
    (when-not (ident-has-attr? db :member/name :db/ident)
      (log/info "Loading db schema")
      ;; (tx (map uuid-for-seed (-> (io/resource "seeds.edn") slurp edn/read-string)))
      (tx (-> (io/resource "seeds.edn") slurp edn/read-string)))
    ;; (when-not (ident-has-attr? db :account/account-id :db.attr/preds)
    ;;   (tx validation/attr-pred))
    ;; (when-not (ident-has-attr? db :account/validate :db.entity/attrs)
    ;;   (tx validation/entity-attrs))
    (when (config/demo-mode? env)
      (when (= 0 (count-all (d/db conn) :member/member-id))
        (demo/seed-random-members! conn))
      (when (= 0 (count-all (d/db conn) :gig/gig-id))
        (demo/seed-gigs! conn))))
  :seeded)

(def datum-elements (juxt :e :a :v :tx :added))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require  '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn))) ;; rcf

  (d/transact conn {:tx-data (-> (io/resource "seeds.edn") slurp edn/read-string)})

  (d/transact conn {:tx-data  [{:db/ident :attendance/motivation
                                :db/doc "The member-added motivation statement"
                                :db/valueType :db.type/keyword
                                :db/cardinality :db.cardinality/one}]})
  (d/transact conn {:tx-data [{:section/name "dance" :section/default? false}
                              {:section/name "melodica" :section/default? false}
                              {:section/name "cabaret" :section/default? false}
                              {:section/name "violin" :section/default? false}
                              {:section/name "horn" :section/default? false}]})
  (d/transact conn {:tx-data [{:db/ident :forum.topic/topic-id
                               :db/doc "The topic id of the associated forum topic"
                               :db/valueType :db.type/string
                               :db/cardinality :db.cardinality/one}]})

  (defn rollback
    "Reassert retracted datoms and retract asserted datoms in a transaction,
  effectively \"undoing\" the transaction.

  WARNING: *very* naive function!"
    [conn tx]
    (let [tx-log (d/tx-range conn {:start tx :end nil}) ; find the transaction
          txid   (-> tx-log :t d/t->tx) ; get the transaction entity id
          newdata (->> (:data tx-log)   ; get the datoms from the transaction
                       (remove #(= (:e %) txid)) ; remove transaction-metadata datoms
                     ; invert the datoms add/retract state.
                       (map #(do [(if (:added %) :db/retract :db/add) (:e %) (:a %) (:v %)]))
                       reverse)] ; reverse order of inverted datoms.
      @(d/transact conn newdata)))

  (let [tx-log (d/tx-range conn {:start tx :end nil}) ; find the transaction
        txid   (-> tx-log :t d/t->tx)   ; get the transaction entity id
        newdata (->> (:data tx-log)     ; get the datoms from the transaction
                     (remove #(= (:e %) txid)) ; remove transaction-metadata datoms
                                        ; invert the datoms add/retract state.
                     (map #(do [(if (:added %) :db/retract :db/add) (:e %) (:a %) (:v %)]))
                     reverse)]          ; reverse order of inverted datoms.
    @(d/transact conn newdata))

  (datum-elements
   (last
    (:data
     (last
      (d/tx-range conn {:start (last-transaction-time db)
                        :end nil})))))

;;
  )
