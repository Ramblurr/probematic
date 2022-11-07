(ns app.datomic
  (:require [datomic.client.api :as d]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.yetanalytics.squuid :as sq]
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

(defn load-dataset
  [conn]
  (let [db (d/db conn)
        tx #(d/transact conn {:tx-data %})]
    (when-not (ident-has-attr? db :member/name :db/ident)
      (tap> "Loading db schema")
      (tx (-> (io/resource "schema.edn") slurp edn/read-string))
      ;; (tx (map uuid-for-seed (-> (io/resource "seeds.edn") slurp edn/read-string)))
      (tx (-> (io/resource "seeds.edn") slurp edn/read-string)))
    ;; (when-not (ident-has-attr? db :account/account-id :db.attr/preds)
    ;;   (tx validation/attr-pred))
    ;; (when-not (ident-has-attr? db :account/validate :db.entity/attrs)
    ;;   (tx validation/entity-attrs))
    ))

(defn transact [conn opts]
  (try
    (d/transact conn opts)
    (catch clojure.lang.ExceptionInfo e
      {:error (ex-data e)
       :exception e
       :msg (ex-message e)})))

(defn unique-error? [error]
  (= :db.error/unique-conflict (-> error :error :db/error)))

(defn db-error? [result]
  (contains? result :error))

(defn db-ok? [result]
  (let [ok? (not (db-error? result))]
    (if ok? true
        (do
          (tap> result)
          false))))

(defn find-all
  "Returns a list of all entities having attr"
  [db attr pattern]
  (d/q '[:find (pull ?e pattern)
         :in $ ?attr pattern
         :where
         [?e ?attr ?v]]
       db attr pattern))

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

(def entity-ids [:gig/gig-id
                 :song/song-id
                 :insurance.policy/policy-id
                 :played/play-id
                 :instrument.category/category-id
                 :instrument/instrument-id
                 :insurance.coverage.type/type-id
                 :instrument.coverage/coverage-id
                 :insurance.category.factor/category-factor-id
                 :comment/comment-id])

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

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require  '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn)))
  (d/transact conn {:tx-data (-> (io/resource "seeds.edn") slurp edn/read-string)})
  ;;
  )
