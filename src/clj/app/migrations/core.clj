(ns app.migrations.core
  (:require
   [com.yetanalytics.squuid :as sq]
   [app.util :as util]
   [clojure.tools.logging :as log]
   [app.datomic :as d]
   [datomic.client.api :as datomic]
   [app.queries :as q]
   [app.ledger.domain :as ledger.domain]))

(defn all-members [db]
  (->> (d/find-all db :member/member-id q/member-pattern)
       (mapv #(first %))))

(defn m001-create-member-ledger [conn]
  (let [db (datomic/db conn)
        members (all-members db)]
    (mapcat (fn [{:member/keys [member-id name] :as member}]
              (let [ledger (q/retrieve-ledger db member-id)]
                (when (nil? ledger)
                  (log/info "Creating ledger for member" name)
                  (ledger.domain/txs-new-member-ledger (str member-id) (sq/generate-squuid) (d/ref member)))))
            members)))

(def migration-fns [m001-create-member-ledger])
