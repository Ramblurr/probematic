(ns app.members.controller
  (:require [tick.core :as t]
            [app.datomic :as d]
            [app.queries :as q]
            [app.controllers.common :as common]
            [datomic.client.api :as datomic]))

(def member-pattern [:member/gigo-key :member/name :member/active? :member/phone :member/email
                     {:member/section [:section/name]}])

(def member-detail-pattern [:member/gigo-key :member/name :member/active? :member/phone :member/email
                            {:member/section [:section/name]}
                            {:instrument/_owner [:instrument/name :instrument/instrument-id
                                                 {:instrument/category [:instrument.category/name]}]}])
(defn ->member [member]
  member)

(defn retrieve-member [db gigo-key]
  (->member
   (d/find-by db :member/gigo-key gigo-key member-detail-pattern)))

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

(defn update-active-and-section! [{:keys [datomic-conn db] :as req}]
  (let [params (common/unwrap-params req)
        gigo-key (:gigo-key params)
        member (retrieve-member db gigo-key)
        member-ref (d/ref member :member/gigo-key)
        tx-data [[:db/add member-ref :member/section [:section/name (:section-name params)]]
                 [:db/add member-ref :member/active? (not (:member/active? member))]]]
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
    (transact-member! datomic-conn gigo-key tx-data)))

(defn member-current-insurance-info [{:keys [db] :as req} member]
  (let [policy (q/insurance-policy-effective-as-of db (t/inst) q/policy-pattern)
        coverages (q/instruments-for-member-covered-by db  member policy q/instrument-coverage-detail-pattern)]
    coverages))

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

  (->>
   (datomic/q '[:find ?e
                :in $  ?now
                :where [?e :insurance.policy/effective-until ?date]
                [(>= ?date ?now)]]
              db  (t/now))
   (map #(datomic/entity db %)))
  (map first)
  (sort-by :insurance.policy/effective-until)
  reverse
  first
  ;;
  )
