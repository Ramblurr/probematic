(ns app.queries
  (:require
   [datomic.client.api :as datomic]
   [app.datomic :as d]
   [app.util :as util]
   [tick.core :as t]
   [medley.core :as m]
   [clojure.set :as set]
   [app.debug :as debug]))

(def attendance-pattern [{:attendance/section [:section/name]}
                         {:attendance/member [:member/name :member/gigo-key :member/nick]}
                         :attendance/plan
                         :attendance/gig+member
                         :attendance/comment
                         :attendance/motivation
                         :attendance/updated])

(def member-pattern [:member/gigo-key :member/name :member/nick :member/active? :member/phone :member/email
                     {:member/section [:section/name]}])

(def member-detail-pattern [:member/gigo-key :member/name :member/nick :member/active? :member/phone :member/email
                            :member/discourse-id :member/avatar-template
                            {:member/section [:section/name]}
                            {:instrument/_owner [:instrument/name :instrument/instrument-id
                                                 {:instrument/category [:instrument.category/name]}]}])

(def song-pattern [:song/title :song/song-id])

(def gig-pattern [:gig/gig-id :gig/title :gig/status :gig/date :gig/location])
(def gig-detail-pattern [:gig/gig-id :gig/title :gig/status :gig/date :gig/location
                         :gig/end-date  :gig/pay-deal :gig/call-time :gig/set-time
                         :gig/end-time :gig/description :gig/setlist :gig/leader :gig/post-gig-plans
                         :gig/more-details
                         {:gig/comments [{:comment/author [:member/name :member/nick :member/gigo-key :member/avatar-template]}
                                         :comment/body :comment/comment-id :comment/created-at]}
                         {:gig/contact [:member/name :member/gigo-key :member/nick]}])

(def play-pattern [{:played/gig gig-pattern}
                   {:played/song [:song/song-id :song/title]}
                   :played/gig+song
                   :played/rating
                   :played/play-id
                   :played/emphasis])

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
                  {:member/_section [:member/name :member/gigo-key :member/nick]}])
   (map first)))

(defn active-members [db]
  (->>
   (datomic/q '[:find (pull ?member pattern)
                :in $ pattern
                :where
                [?member :member/active? true]]
              db [:member/name :member/gigo-key :member/nick {:member/section [:section/name]}])
   (map first)
   (map #(update % :member/section :section/name))))

(defn attendance-for-gig [db gig-id]
  (->>
   (d/find-by db :gig/gig-id gig-id
              [:gig/gig-type :gig/title {:attendance/_gig attendance-pattern}])
   :attendance/_gig
   (map #(update % :attendance/section :section/name))
     ;; (group-by #(-> % :attendance/section :section/name))
   ))

(defn attendance-for-gig-with-all-active-members
  "Returns a list of maps :attendance/ with attendance attributes for all active members.
  If some member has a concrete plan response for the gig, that will be included, if not then the plan is set to unknown."
  [db gig-id]
  (let [plans (attendance-for-gig db gig-id)
        members  (active-members db)
        no-plan (remove (fn [member]
                          (m/find-first (fn [p]
                                          (= (:member/gigo-key member) (get-in p [:attendance/member :member/gigo-key]))) plans))
                        members)]
    (concat plans
            (map (fn [member]
                   {:attendance/section (:member/section member)
                    :attendance/member  member
                    :attendance/plan :plan/unknown}) no-plan))))

(defn member-nick-or-name [member]
  (debug/xxx
   (if (:member/nick member)
     (:member/nick member)
     (:member/name member))))

(defn attendance-plans-by-section-for-gig
  "Like attendance-for-gig-with-all-active-members, but returns a list of maps, one for each section with :members attendance plans"
  [db gig-id]
  (->> (attendance-for-gig-with-all-active-members db gig-id)
       (group-by :attendance/section)
       (reduce (fn [acc [k v]]
                 (when k
                   (conj acc {:section/name k
                              :members (util/isort-by #(-> % :attendance/member member-nick-or-name) v)})))
               [])))

(defn section-for-member [db gigo-key]
  (->
   (datomic/q '[:find ?section-name
                :in $ ?gigo-key
                :where
                [?member :member/gigo-key ?gigo-key]
                [?member :member/section ?section]
                [?section :section/name ?section-name]]
              db gigo-key)
   ffirst))

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

  (d/transact conn {:tx-data [{:attendance/gig [:gig/gig-id "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q7OCww"]
                               :attendance/member [:member/gigo-key "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA086WiwoM"]
                               :attendance/gig+member (pr-str ["ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q7OCww" "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA086WiwoM"])
                               :attendance/section [:section/name "flute"]
                               :attendance/updated (t/inst)
                               :attendance/plan :plan/definitely-not}]})

  (def plans)

  (def sections (active-members-by-section db))
  (map (fn [section]
         (update section :member/_section
                 (fn [members]
                   (map (fn [member]
                          (assoc member :foo :bar)
                          ;; (get plans (:section/name section))
                          ;;
                          )members)
                   ;;
                   )))sections)
  plans

  (def active-mem (active-members db))
  active-mem

  (let [plans (->>
               (d/find-by db :gig/gig-id "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q7OCww"
                          [:gig/gig-type :gig/title {:attendance/_gig [{:attendance/section [:section/name]}
                                                                       {:attendance/member [:member/name :member/gigo-key :member/nick]}
                                                                       :attendance/plan
                                                                       :attendance/comment
                                                                       :attendance/motivation
                                                                       :attendance/updated]}])
               :attendance/_gig
               ;; (group-by #(-> % :attendance/section :section/name))
               )
        plans (attendance-for-gig db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q7OCww")
        members (active-members db)
        no-plan]

    (concat plans
            (map (fn [member]
                   {:attendance/section (:member/section member)
                    :attendance/member  member
                    :attendance/plan :plan/unknown}) no-plan)))
  (active-members-by-section db)
  (->>
   (attendance-for-gig-with-all-active-members db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q7OCww"))

  (attendance-plans-by-section-for-gig db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q7OCww")
  ;;
  )
