(ns app.datomic-migrations
  (:require
   [clojure.tools.logging :as log]
   [clojure.edn :as edn]
   [com.yetanalytics.squuid :as sq]
   [clojure.java.io :as io]
   [datomic.client.api :as d]))

(defn ident-has-attr?
  [db ident attr]
  (contains? (d/pull db {:eid ident :selector '[*]}) attr))

(defn migrate!
  [env conn migrate-fns]
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
    #_(when (config/demo-mode? env)
        (when (= 0 (count-all (d/db conn) :member/member-id))
          (demo/seed-random-members! conn))
        (when (= 0 (count-all (d/db conn) :gig/gig-id))
          (demo/seed-gigs! conn)))
    (when (seq migrate-fns)
      (log/info "Running migrations")
      (doseq [f migrate-fns]
        (when-let [tx-data (f conn)]
          (tx tx-data)))))
  :seeded)

(defn ensure-member-ledgers [conn])
