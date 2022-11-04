(ns app.members.controller
  (:require [tick.core :as t]
            [app.datomic :as d]
            [app.controllers.common :as common]))

(def member-pattern [:member/gigo-key :member/name :member/active? :member/phone :member/email
                     {:member/section [:section/name]}])

(defn ->member [member]
  member)

(defn retrieve-member [db gigo-key]
  (->member
   (d/find-by db :member/gigo-key gigo-key member-pattern)))

(defn members [db]
  (->> (d/find-all db :member/gigo-key member-pattern)
       (mapv #(->member (first %)))
       (sort-by (juxt :member/name :member/active))))

(defn transact-member!
  [conn gigo-key tx-data]
  (let [result (d/transact conn {:tx-data tx-data})]
    (if  (d/db-ok? result)
      {:member
       (d/find-by (:db-after result)
                  :member/gigo-key
                  gigo-key
                  member-pattern)}
      result)))

(defn toggle-active-state! [{:keys [datomic-conn db] :as req} gigo-key]
  (let [member (retrieve-member db gigo-key)
        tx-data [[:db/add [:member/gigo-key gigo-key] :member/active? (not (:member/active? member))]]]
    (transact-member! datomic-conn gigo-key tx-data)))
