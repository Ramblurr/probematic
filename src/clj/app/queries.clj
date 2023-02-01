(ns app.queries
  (:require
   [app.datomic :as d]
   [app.gigs.domain :as gig.domain]
   [app.util :as util]
   [datomic.client.api :as datomic]
   [medley.core :as m]
   [tick.core :as t]))

(def section-pattern [:section/name :section/default? :section/position])

(def attendance-pattern [{:attendance/section [:section/name]}
                         {:attendance/member [:member/name :member/member-id :member/nick]}
                         :attendance/plan
                         :attendance/gig+member
                         {:attendance/gig [:gig/gig-id]}
                         :attendance/comment
                         :attendance/motivation
                         :attendance/updated])

(def member-pattern [:member/member-id :member/username :member/name :member/nick :member/active? :member/phone :member/email :member/avatar-template
                     {:member/section [:section/name]}])

(def member-detail-pattern [:member/member-id :member/name :member/nick :member/active? :member/phone :member/email
                            :member/username :member/keycloak-id
                            :member/discourse-id :member/avatar-template
                            {:member/section [:section/name]}
                            {:instrument/_owner [:instrument/name :instrument/instrument-id
                                                 {:instrument/category [:instrument.category/name]}]}])

(def song-pattern [:song/title :song/song-id :song/active? :song/solo-count])
(def song-pattern-detail [:song/title :song/song-id
                          :song/total-plays :song/total-performances :song/total-rehearsals
                          :forum.topic/topic-id
                          {:song/last-rehearsal [:gig/gig-id :gig/title :gig/date]}
                          {:song/last-performance [:gig/gig-id :gig/title]}
                          :song/active? :song/composition-credits :song/arrangement-credits :song/arrangement-notes :song/origin :song/solo-count])

(def gig-pattern [:gig/gig-id :gig/title :gig/status :gig/date :gig/end-date :gig/call-time :gig/set-time :gig/location :gig/gig-type :gig/gigo-id])
(def gig-detail-pattern [:gig/gig-id :gig/title :gig/status :gig/date :gig/location :gig/gigo-id
                         :gig/end-date  :gig/pay-deal :gig/call-time :gig/set-time
                         :gig/end-time :gig/description :gig/setlist :gig/leader :gig/post-gig-plans
                         :forum.topic/topic-id :gig/more-details :gig/gig-type
                         {:gig/comments [{:comment/author [:member/name :member/nick :member/member-id :member/avatar-template]}
                                         :comment/body :comment/comment-id :comment/created-at]}
                         {:gig/contact [:member/name :member/member-id :member/nick]}])

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
     :instrument/make
     :instrument/model
     :instrument/serial-number
     :instrument/build-year
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
                         {:instrument/owner [:member/name :member/member-id]}
                         {:instrument/category [:instrument.category/category-id
                                                :instrument.category/code
                                                :instrument.category/name]}])

(def instrument-detail-pattern [:instrument/name
                                :instrument/instrument-id
                                :instrument/make
                                :instrument/model
                                :instrument/build-year
                                :instrument/serial-number
                                {:instrument/owner [:member/name :member/member-id]}
                                {:instrument/category [:instrument.category/category-id
                                                       :instrument.category/code
                                                       :instrument.category/name]}])

(def setlist-v1-pattern [{:setlist/gig [:gig/gig-id]}
                         :setlist/version
                         :setlist.v1/songs])

(defn retrieve-song [db song-id]
  (d/find-by db :song/song-id song-id song-pattern-detail))

(defn retrieve-sections [db]
  (->>
   (d/find-all db :section/name section-pattern)
   (mapv first)
   (sort-by :section/position)))

(defn retrieve-active-sections [db]
  (->>
   (d/find-all-by db :section/active? true section-pattern)
   (mapv first)
   (sort-by :section/position)))

(defn retrieve-section-by-name [db name]
  (d/find-by db :section/name name section-pattern))

(defn results->gigs [r]
  (->> r
       (mapv first)
       (mapv gig.domain/db->gig)
       (sort-by :gig/call-time)
       (sort-by :gig/date)))

(defn find-all-gigs [db]
  (results->gigs (d/find-all db :gig/gig-id gig-pattern)))

(defn retrieve-gig [db gig-id]
  (gig.domain/db->gig
   (d/find-by db :gig/gig-id (util/ensure-uuid gig-id) gig-detail-pattern)))

(defn retrieve-gig-by-gigo [db gigo-id]
  (gig.domain/db->gig
   (d/find-by db :gig/gigo-id gigo-id gig-detail-pattern)))

(defn gigs-missing-id [db]
  (mapv first
        (d/q '[:find (pull ?e pattern)
               :in $ pattern
               :where [?e :gig/gigo-id _]
               [(missing? $ ?e :gig/gig-id)]]
             db gig-pattern)))

(defn members-missing-id [db]
  (mapv first
        (d/q '[:find (pull ?e pattern)
               :in $ pattern
               :where [?e :member/member-id _]
               [(missing? $ ?e :member/member-id)]]
             db member-pattern)))

(defn gigs-before [db instant]
  (results->gigs  (d/q '[:find (pull ?e pattern)
                         :in $ ?time pattern
                         :where
                         [?e :gig/gig-id _]
                         [?e :gig/date ?date]
                         [(< ?date ?time)]] db instant gig-pattern)))

(defn gigs-after
  ([db instant]
   (gigs-after db instant gig-pattern))
  ([db instant pattern]
   (results->gigs (d/q '[:find (pull ?e pattern)
                         :in $ ?reference-time pattern
                         :where
                         [?e :gig/gig-id _]
                         [?e :gig/date ?date]
                         [(>= ?date ?reference-time)]] db instant pattern))))

(defn gigs-between [db instant-start instant-end]
  (results->gigs (d/q '[:find (pull ?e pattern)
                        :in $ ?ref-start ?ref-end pattern
                        :where
                        [?e :gig/gig-id _]
                        [?e :gig/date ?date]
                        [(>= ?date ?ref-start)]
                        [(<= ?date ?ref-end)]] db
                      instant-start  instant-end gig-pattern)))

(defn date-midnight-today! []
  (-> (t/date)
      (t/at (t/midnight))
      (t/in "UTC")
      (t/inst)))

(defn gigs-future [db]
  (gigs-after db (date-midnight-today!)))

(defn gigs-past [db]
  (gigs-before db (date-midnight-today!)))

(defn gig+member [gig-id member-id]
  (pr-str [(str gig-id) (str member-id)]))

(defn retrieve-member [db member-id]
  (d/find-by db :member/member-id member-id member-detail-pattern))

(defn members-for-select
  [db]
  (mapv first
        (d/find-all db :member/member-id  [:member/name :member/nick :member/member-id :member/active?])))

(defn members-for-select-active
  [db]
  (filter :member/active?
          (members-for-select db)))

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
              (d/ref member :member/member-id)
              (d/ref policy :insurance.policy/policy-id))
   (map first)))

(defn retrieve-all-songs [db]
  (util/isort-by :song/title
                 (mapv first
                       (d/find-all db :song/song-id song-pattern))))
(defn retrieve-songs [db song-ids]
  (if (empty? song-ids)
    []
    (filter #((set song-ids) (:song/song-id %))
            (retrieve-all-songs db))))

(defn active-members-by-section [db]
  (->>
   (datomic/q '[:find (pull ?section pattern)
                :in $ pattern
                :where
                [?section :section/name ?v]]
              db [:section/name
                  {:member/_section [:member/name :member/member-id :member/nick]}])
   (map first)))

(defn active-members [db]
  (->>
   (datomic/q '[:find (pull ?member pattern)
                :in $ pattern
                :where
                [?member :member/active? true]]
              db [:member/name :member/member-id :member/nick {:member/section [:section/name]} :member/email])
   (map first)
   (map #(update % :member/section :section/name))))

(defn attendance-for-gig
  "Return the member's attendance for the gig"
  [db gig-id member-id]
  (->
   (datomic/q '[:find (pull ?gig pattern)
                :in $ ?gig-id ?gig+member pattern
                :where
                [?gig :gig/gig-id ?gig-id]
                [?a :attendance/gig+member ?gig+member]]
              db gig-id (gig+member gig-id member-id)
              [:gig/title {:attendance/_gig attendance-pattern}])
   ffirst
   :attendance/_gig
   first))
(defn attendances-for-gig
  "Return a list of attendances for the gig for all members"
  [db gig-id]
  (->>
   (d/find-by db :gig/gig-id gig-id
              [:gig/gig-type :gig/title {:attendance/_gig attendance-pattern}])
   :attendance/_gig
   (map #(update % :attendance/section :section/name))
   ;; (group-by #(-> % :attendance/section :section/name))
   ))

(defn attendance-for-gig-with-all-active-members
  "Returns a list of maps :attendance/ with attendance attributes for all active members.
  If some member has a concrete plan response for the gig, that will be included, if not then the plan is set to no-response."
  [db gig-id]
  (let [plans (attendances-for-gig db gig-id)
        members  (active-members db)
        no-plan (remove (fn [member]
                          (m/find-first (fn [p]
                                          (= (:member/member-id member) (get-in p [:attendance/member :member/member-id]))) plans))
                        members)]
    (concat plans
            (map (fn [member]
                   {:attendance/section (:member/section member)
                    :attendance/member  member
                    :attendance/plan :plan/no-response}) no-plan))))

(defn member-nick-or-name [member]
  (if (:member/nick member)
    (:member/nick member)
    (:member/name member)))

(defn attendance-plans-by-section-for-gig
  "Like attendance-for-gig-with-all-active-members, but returns a list of maps, one for each section with :members attendance plans"
  [db attendance-for-gig committed-only?]
  (->> attendance-for-gig
       (group-by :attendance/section)
       (reduce (fn [acc [k v]]
                 (when k
                   (conj acc {:section/name k
                              :members
                              (cond->> v
                                committed-only? (filter #(= :plan/definitely (:attendance/plan %)))
                                true (util/isort-by #(-> % :attendance/member member-nick-or-name)))})))
               [])
       (map (fn [as]
              (assoc as :section/position (-> (retrieve-section-by-name db (:section/name as))
                                              :section/position))))
       (sort-by :section/position)))

(defn section-for-member [db member-id]
  (->
   (datomic/q '[:find ?section-name
                :in $ ?member-id
                :where
                [?member :member/member-id ?member-id]
                [?member :member/section ?section]
                [?section :section/name ?section-name]]
              db member-id)
   ffirst))

(defn member-by-email [db email]
  (d/find-by db :member/email email member-pattern))

(defn setlist-for-gig [db gig-id]
  (-> (d/find-by db :setlist/gig [:gig/gig-id gig-id] setlist-v1-pattern)
      (update  :setlist.v1/songs #(map (partial datomic/pull db song-pattern) %))))

(defn setlist-song-tuples-for-gig [db gig-id]
  (->>
   (-> (d/find-by db :setlist/gig [:gig/gig-id gig-id] [:setlist.v1/ordered-songs])
       :setlist.v1/ordered-songs)
   (sort-by second)
   (map #(update % 0 (fn [eid] (->> (datomic/pull db [:song/song-id] eid)
                                    (into [])
                                    first))))))
(defn setlist-song-ids-for-gig [db gig-id]
  (->> (-> (d/find-by db :setlist/gig [:gig/gig-id gig-id] [:setlist.v1/ordered-songs])
           :setlist.v1/ordered-songs)
       (sort-by second)
       (map first)
       (map (partial datomic/pull db [:song/song-id]))
       (map :song/song-id)))

(defn probeplan-song-tuples-for-gig
  [db gig-id]
  (->> (d/q '[:find ?result
              :in $ ?gig-id
              :where
              [?e :probeplan/gig ?gig-id]
              [?e :probeplan.classic/ordered-songs ?tup]
              [(untuple  ?tup) [?song-eid ?position ?emphasis]]
              [?song-eid :song/song-id ?song-id]
              [(tuple ?song-id ?position ?emphasis) ?result]]
            db [:gig/gig-id gig-id])
       (mapv first)
       (mapv (fn [song]
               (update song 0 #(vector :song/song-id %))))))

(defn probeplan-songs-for-gig
  "Returns a list of maps with the following keys:

     :song/title
     :song/song-id
     :emphasis
     :position
  "
  [db gig-id]
  (->> (d/q '[:find ?result
              :in $ ?gig-id
              :where
              [?e :probeplan/gig ?gig-id]
              [?e :probeplan.classic/ordered-songs ?tup]
              [(untuple  ?tup) [?song-eid ?position ?emphasis]]
              [?song-eid :song/title ?song-title]
              [?song-eid :song/song-id ?song-id]
              [(tuple ?song-id ?song-title ?position ?emphasis) ?result]]
            db [:gig/gig-id gig-id])
       (mapv first)
       (mapv (fn [[song-id title position emphasis]]
               {:song/song-id song-id
                :song/title title
                :position position
                :emphasis emphasis}))
       (sort-by :position)))

(defn setlist-songs-for-gig
  "Returns a list of maps with the following keys:
  :song/title
  :song/song-id
  :position
  "
  [db gig-id]
  (->> (setlist-song-tuples-for-gig db gig-id)
       (mapv (fn [[[_ song-id] position]]
               {:song/title (:song/title (retrieve-song db song-id))
                :song/song-id song-id
                :position position}))
       (sort-by :position)))

(defn planned-songs-for-gig
  "If the gig is a probe, then returns probeplan-songs-for gig, otherwise returns"
  [db gig-id]
  (let [{:gig/keys [gig-type]} (retrieve-gig db gig-id)]
    (if (= :gig.type/gig gig-type)
      (setlist-songs-for-gig db gig-id)
      ;; (setlist-for-gig db gig-id)
      (probeplan-songs-for-gig db gig-id))))

(defn sheet-music-by-song [db song-id]
  (->> (d/find-all-by db :sheet-music/song  [:song/song-id song-id] play-pattern)
       (mapv first)))

(defn sheet-music-for-song
  [db song-id]
  (let [sections-with-sheets
        (->>
         (d/q '[:find  (pull ?section pattern)
                :in $ ?song pattern
                :where
                [?sm :sheet-music/song ?song]
                [?sm :sheet-music/section ?section]]
              db
              [:song/song-id song-id]
              [:section/name :section/position :section/default? {:sheet-music/_section [:sheet-music/sheet-id :sheet-music/title :file/webdav-path]}])
         (mapv first))
        sections-with-no-sheets (->> (d/q '[:find (pull ?section pattern)
                                            :in $ ?song pattern
                                            :where
                                            [?section :section/name _]
                                            [?section :section/active? true]
                                            (not-join [?section ?song]
                                                      [_ :sheet-music/section ?section]
                                                      [_ :sheet-music/song ?song])] db [:song/song-id song-id] [:section/name :section/default? :section/position])
                                     (mapv first))]
    (->> (concat sections-with-no-sheets sections-with-sheets)
         (sort-by :section/position))))

(defn plays-by-gig [db gig-id]
  (->> (d/find-all-by db :played/gig [:gig/gig-id gig-id] play-pattern)
       (mapv first)
       (sort-by #(-> % :played/song :song/title))))

(defn plays-by-song [db song-id]
  (->> (d/find-all-by db :played/song [:song/song-id song-id] play-pattern)
       (mapv first)))

(defn load-play-stats [db]
  (->>
   (d/find-all db :song/song-id [:song/song-id
                                 :song/title
                                 :song/total-plays
                                 :song/total-performances
                                 :song/total-rehearsals
                                 :song/total-rating-good
                                 :song/total-rating-bad
                                 :song/total-rating-ok
                                 :song/six-month-total-rating-good
                                 :song/six-month-total-rating-bad
                                 :song/six-month-total-rating-ok
                                 :song/days-since-performed
                                 :song/days-since-rehearsed
                                 :song/days-since-intensive
                                 :song/last-played-on
                                 {:song/last-performance [:gig/gig-id :gig/date]}
                                 {:song/last-rehearsal [:gig/gig-id :gig/date]}
                                 {:song/last-intensive [:gig/gig-id :gig/date]}
                                 {:song/first-performance [:gig/gig-id :gig/date]}
                                 {:song/first-rehearsal [:gig/gig-id :gig/date]}])
   (mapv first)))

(defn next-probes
  "Return the future confirmed probes"
  [db]
  (->> (gigs-after db (date-midnight-today!))
       (filter #(= :gig.type/probe (:gig/gig-type %)))
       (filter #(= :gig.status/confirmed (:gig/status %)))))

(defn next-probes-with-plan
  "Return the future confirmed probes"
  [db comp]
  (->>
   (next-probes db)
   (map (fn [gig]
          (assoc gig :songs (->> (probeplan-songs-for-gig db (:gig/gig-id gig))
                                 (sort-by :emphasis comp)))))))

;;;; END
(comment
  (do
    (require '[integrant.repl.state :as state])
    (require  '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn))) ;; rcf

  (load-play-stats db)
  (sheet-music-for-song db #uuid "01844740-3eed-856d-84c1-c26f07068207")

  (retrieve-gig db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q7OCQw")

  (active-members-by-section db)

  (instruments-for-member-covered-by db
                                     {:member/member-id "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA086WiwoM"}
                                     (insurance-policy-effective-as-of db (t/now) policy-pattern)
                                     instrument-coverage-detail-pattern)

  (d/transact conn {:tx-data [{:attendance/gig [:gig/gig-id "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q7OCww"]
                               :attendance/member [:member/member-id "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA086WiwoM"]
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
                                                                       {:attendance/member [:member/name :member/member-id :member/nick]}
                                                                       :attendance/plan
                                                                       :attendance/comment
                                                                       :attendance/motivation
                                                                       :attendance/updated]}])
               :attendance/_gig
               ;; (group-by #(-> % :attendance/section :section/name))
               )
        plans (attendances-for-gig db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q7OCww")
        members (active-members db)
        no-plan]

    (concat plans
            (map (fn [member]
                   {:attendance/section (:member/section member)
                    :attendance/member  member
                    :attendance/plan :plan/no-response}) no-plan)))
  (active-members-by-section db)
  (->>
   (attendance-for-gig-with-all-active-members db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q7OCww"))

  (attendance-plans-by-section-for-gig db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q7OCww")
  (d/q '[:find ?result
         :in $ ?gig-id
         :where
         [?e :probeplan/gig ?gig-id]
         [?e :probeplan.classic/ordered-songs ?tup]
         [(untuple  ?tup) [?song-eid ?position ?emphasis]]
         [?song-eid :song/title ?song-title]
         [?song-eid :song/song-id ?song-id]
         [(tuple ?song-id ?song-title ?position ?emphasis) ?result]]
       db [:gig/gig-id "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q6uCgw"])

  (d/find-by db :probeplan/gig
             [:gig/gig-id "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q6uCgw"]
             '[*])
  (d/q '[:find ?song-title ;; [?song-id ?song-title ?position ?emphasis]
         :in $ ?val
         :where
         [?e :probeplan/gig ?val]
         [?e :probeplan.classic/ordered-songs ?tup]
         [(untuple ?song-eid ?position ?emphasis) ?tup]
         [?song-eid :song/title ?song-title]
         [?song-eid :song/song-id ?song-id]]
       db :probeplan/gig
       [:gig/gig-id "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q6uCgw"])
  (probeplan-songs-for-gig db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q6uCgw")
  (probeplan-song-tuples-for-gig db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q6uCgw")
  ;;
  )
