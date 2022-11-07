(ns app.queries
  (:require
   [datomic.client.api :as datomic]
   [app.datomic :as d]
   [app.util :as util]
   [tick.core :as t]
   [medley.core :as m]))

(def song-pattern [:song/title :song/song-id])

(def instrument-coverage-detail-pattern
  [:instrument.coverage/coverage-id
   {:instrument.coverage/instrument
    [:instrument/name
     :instrument/instrument-id
     {:instrument/owner [:member/name]}
     {:instrument/category [:instrument.category/category-id
                            :instrument.category/code
                            :instrument.category/name]}]}
   {:instrument.coverage/types
    [:insurance.coverage.type/name
     :insurance.coverage.type/type-id
     :insurance.coverage.type/premium-factor]}
   :instrument.coverage/private?
   :instrument.coverage/value])
(def policy-pattern [:insurance.policy/policy-id
                     :insurance.policy/currency
                     :insurance.policy/name
                     :insurance.policy/effective-at
                     :insurance.policy/effective-until
                     :insurance.policy/premium-factor
                     {:insurance.policy/covered-instruments
                      [:instrument.coverage/coverage-id
                       {:instrument.coverage/instrument
                        [:instrument/name
                         :instrument/instrument-id
                         {:instrument/owner [:member/name]}
                         {:instrument/category [:instrument.category/category-id
                                                :instrument.category/code
                                                :instrument.category/name]}]}
                       {:instrument.coverage/types
                        [:insurance.coverage.type/name
                         :insurance.coverage.type/type-id
                         :insurance.coverage.type/premium-factor]}
                       :instrument.coverage/private?
                       :instrument.coverage/value]}
                     {:insurance.policy/coverage-types
                      [:insurance.coverage.type/name :insurance.coverage.type/premium-factor :insurance.coverage.type/type-id]}
                     {:insurance.policy/category-factors
                      [{:insurance.category.factor/category
                        [:instrument.category/name
                         :instrument.category/category-id]}
                       :insurance.category.factor/factor
                       :insurance.category.factor/category-factor-id]}])

(def instrument-pattern [:instrument/name
                         :instrument/instrument-id
                         {:instrument/owner [:member/name :member/gigo-key]}
                         {:instrument/category [:instrument.category/category-id
                                                :instrument.category/code
                                                :instrument.category/name]}])

(def instrument-detail-pattern [:instrument/name
                                :instrument/instrument-id
                                :instrument/make
                                :instrument/model
                                :instrument/build-year
                                :instrument/serial-number
                                {:instrument/owner [:member/name :member/gigo-key]}
                                {:instrument/category [:instrument.category/category-id
                                                       :instrument.category/code
                                                       :instrument.category/name]}])

(defn insurance-policy-effective-as-of [db inst pattern]
  (->>
   (datomic/q '[:find (pull ?e pattern)
                :in $ pattern ?now
                :where [?e :insurance.policy/effective-until ?date]
                [(>= ?date ?now)]]
              db pattern inst)
   (map first)
   (sort-by :insurance.policy/effective-until)
   reverse
   first))

(defn instruments-for-member-covered-by [db member policy pattern]
  (->>
   (datomic/q '[:find (pull ?coverage pattern)
                :in $ pattern ?member ?policy-id
                :where
                [?policy-id :insurance.policy/covered-instruments ?coverage]
                [?coverage :instrument.coverage/instrument ?instr]
                [?instr :instrument/owner ?member]]
              db pattern
              (d/ref member :member/gigo-key)
              (d/ref policy :insurance.policy/policy-id))
   (map first)))

(defn find-all-songs [db]
  (util/isort-by :song/title
                 (mapv first
                       (d/find-all db :song/song-id song-pattern))))

(defn active-members-by-section [db]
  (->>
   (datomic/q '[:find (pull ?section pattern)
                :in $ pattern
                :where
                [?section :section/name ?v]]
              db [:section/name
                  {:member/_section [:member/name :member/gigo-key]}])
   (map first)))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require  '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn))) ;; rcf

  (active-members-by-section db)

  (instruments-for-member-covered-by db
                                     {:member/gigo-key "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA086WiwoM"}
                                     (insurance-policy-effective-as-of db (t/now) policy-pattern)
                                     instrument-coverage-detail-pattern)

  ;;
  )
