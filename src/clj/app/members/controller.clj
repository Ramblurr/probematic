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

(defn sections [db]
  (->> (d/find-all db :section/name [:section/name])
       (mapv first)
       (sort-by :section/name)))

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

(defn update-member! [{:keys [datomic-conn] :as req}]
  (let [gigo-key (-> req :path-params :gigo-key)
        params (common/unwrap-params req)
        member-ref [:member/gigo-key gigo-key]
        tx-data [{:db/id member-ref
                  :member/name (:name params)
                  :member/phone (:phone params)
                  :member/email (:email params)
                  :member/active? (common/check->bool (:active? params))
                  :member/section [:section/name (:section-name params)]}]]

    (tap> params)
    (tap> tx-data)
    (transact-member! datomic-conn gigo-key tx-data)))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require  '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn))) ;; rcf
  (sections db)
  (def section-defaults ["percussion"
                         "bass"
                         "trombone/bombardino"
                         "sax tenor"
                         "sax alto"
                         "trumpet"
                         "sax soprano/clarinet"
                         "flute"
                         "No Section"])

  (sections (datomic/db conn))
  (d/transact conn {:tx-data
                    (map (fn [n] {:section/name n}) section-defaults)})

  ;;
  )
