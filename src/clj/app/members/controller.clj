(ns app.members.controller
  (:require [tick.core :as t]
            [app.datomic :as d]))

(def member-pattern [:member/gigo-key :member/name :member/active? :member/phone :member/email
                     {:member/section [:section/name]}])

(defn ->member [member]
  member)

(defn members [db]
  (->> (d/find-all db :member/gigo-key member-pattern)
       (mapv #(->member (first %)))
       (sort-by (juxt :member/name :member/active))))
