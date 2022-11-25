(ns app.gigs.controller
  (:refer-clojure :exclude [comment])
  (:require
   [app.auth :as auth]
   [app.controllers.common :as common]
   [app.datomic :as d]
   [app.debug :as debug]
   [app.gigs.domain :as domain]
   [app.probeplan.domain :as probeplan.domain]
   [app.queries :as q]
   [app.schemas :as s]
   [app.util :as util]
   [clojure.data :as clojure.data]
   [clojure.string :as str]
   [com.yetanalytics.squuid :as sq]
   [datomic.client.api :as datomic]
   [tick.core :as t]))

(def str->plan (zipmap (map name domain/plans) domain/plans))
(def str->motivation (zipmap (map name domain/motivations) domain/motivations))
(def str->status (zipmap (map name domain/statuses) domain/statuses))
(def str->gig-type (zipmap (map name domain/gig-types) domain/gig-types))

(defn results->gigs [r]
  (->> r
       (mapv first)
       (mapv domain/db->gig)
       (sort-by :gig/date)))

(defn find-all-gigs [db]
  (results->gigs (d/find-all db :gig/gig-id q/gig-pattern)))

(defn retrieve-gig [db gig-id]
  (domain/db->gig
   (d/find-by db :gig/gig-id gig-id q/gig-detail-pattern)))

(defn gigs-before [db instant]
  (results->gigs  (d/q '[:find (pull ?e pattern)
                         :in $ ?time pattern
                         :where
                         [?e :gig/gig-id _]
                         [?e :gig/date ?date]
                         [(< ?date ?time)]] db instant q/gig-pattern)))

(defn gigs-after [db instant]
  (results->gigs (d/q '[:find (pull ?e pattern)
                        :in $ ?reference-time pattern
                        :where
                        [?e :gig/gig-id _]
                        [?e :gig/date ?date]
                        [(>= ?date ?reference-time)]] db instant q/gig-pattern)))

(defn gigs-between [db instant-start instant-end]
  (results->gigs (d/q '[:find (pull ?e pattern)
                        :in $ ?ref-start ?ref-end pattern
                        :where
                        [?e :gig/gig-id _]
                        [?e :gig/date ?date]
                        [(>= ?date ?ref-start)]
                        [(<= ?date ?ref-end)]] db
                      instant-start  instant-end q/gig-pattern)))

(defn date-midnight-today! []
  (-> (t/date)
      (t/at (t/midnight))
      (t/in "UTC")
      (t/inst)))

(defn gigs-future [db]
  (gigs-after db (date-midnight-today!)))

(defn gigs-past [db]
  (gigs-before db (date-midnight-today!)))

(defn next-probes
  "Return the future confirmed probes"
  [db]
  (->> (gigs-after db (date-midnight-today!))
       (filter #(= :gig.type/probe (:gig/gig-type %)))
       (filter #(= :gig.status/confirmed (:gig/status %)))))

(defn attach-attendance [db member {:gig/keys [gig-id] :as gig}]
  (assoc gig :attendance (q/attendance-for-gig db gig-id (:member/gigo-key member))))

(defn gigs-planned-for
  "Return the gigs that the member as supplied an attendance plan for"
  [db member]
  (->>
   (results->gigs (d/q '[:find (pull ?gig pattern)
                         :in $ ?member ?reference-time pattern
                         :where
                         [?gig :gig/date ?date]
                         [(>= ?date ?reference-time)]
                         [?a :attendance/gig ?gig]
                         [?a :attendance/member ?member]
                         [?a :attendance/plan ?plan]
                         [(!= ?plan :plan/no-response)]]
                       db (d/ref member) (date-midnight-today!) q/gig-pattern))
   (map (partial attach-attendance db member))))

(def member {:member/gigo-key "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA2NP7ggoM"})

(clojure.core/comment
  (d/find-by db :gig/gig-id "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q6uCAw" q/gig-pattern))

(defn gigs-needing-plan
  "Return the gigs that the member needs to supply an attendance plan for"
  [db member]
  (let [gigs-with-no-attendance
        (->>
         (d/q '[:find (pull ?gig pattern)
                :in $ ?member ?reference-time pattern
                :where
                [?gig :gig/date ?date]
                [(>= ?date ?reference-time)]
                [?gig :gig/gig-id ?gig-id]
                (not-join [?gig ?member]
                          [?a :attendance/gig ?gig]
                          [?a :attendance/member ?member])]

              db (d/ref member) (date-midnight-today!) q/gig-detail-pattern)
         results->gigs
         (map (fn [gig]
                (assoc gig :attendance {:attendance/section (:member/section member)
                                        :attendance/member  member
                                        :attendance/plan :plan/no-response}))))
        gigs-with-unknown-attendance (->> (d/q '[:find (pull ?gig pattern)
                                                 :in $ ?member ?reference-time pattern
                                                 :where
                                                 [?gig :gig/date ?date]
                                                 [(>= ?date ?reference-time)]
                                                 [?a :attendance/gig ?gig]
                                                 [?a :attendance/member ?member]
                                                 (or
                                                  [(missing? $ ?a :attendance/plan)]
                                                  [?a :attendance/plan :plan/no-response]
                                                  [?a :attendance/plan :plan/unknown])]

                                               db (d/ref member) (date-midnight-today!) q/gig-detail-pattern)
                                          results->gigs
                                          (map (partial attach-attendance db member)))]

    (sort-by :gig/date
             (concat gigs-with-no-attendance gigs-with-unknown-attendance))))

(clojure.core/comment
  (q/attendance-for-gig db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q6uCQw"
                        "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA2NP7ggoM")

  (gigs-needing-plan db {:member/gigo-key "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA2NP7ggoM"}))

(defn page
  ([offset limit coll]
   (take limit (drop offset coll)))
  ([limit coll]
   (page coll 0 limit)))

(defn gigs-past-page [db offset limit]
  (->>
   (d/q '[:find (pull ?e pattern)
          :in $ ?time pattern
          :where
          [?e :gig/gig-id _]
          [?e :gig/date ?date]
          [(< ?date ?time)]] db (date-midnight-today!) [:gig/gig-id :gig/date :db/id])
   (map first)
   (sort-by :gig/date)
   (page offset limit)
   (map :db/id)
   (d/pull-many db q/gig-pattern)
   (sort-by :gig/date)
   (mapv domain/db->gig)))

(defn gigs-past-two-weeks [db]
  (let [now (date-midnight-today!)
        then (t/<< now (t/new-duration 14 :days))]
    (gigs-between db then now)))

(defn query-result->play
  [[play]]
  play)

(defn plays-by-gig [db gig-id]
  (->> (d/find-all-by db :played/gig [:gig/gig-id gig-id] q/play-pattern)
       (map query-result->play)
       (sort-by #(-> % :played/song :song/title))))

(defn songs-not-played [plays all-songs]
  (->> all-songs
       (remove (fn [song]
                 (->> plays
                      (map #(-> % :played/song :song/song-id))
                      (some (fn [p] (= p (:song/song-id song)))))))))

(defn get-attendance
  ([db gig-id gigo-key]
   (get-attendance db (q/gig+member gig-id gigo-key)))
  ([db gig+member]
   (assert (string? gig+member))
   (d/find-by db :attendance/gig+member gig+member q/attendance-pattern)))

(defn transact-attendance! [datomic-conn attendance-txs gig+member]
  (let [result (d/transact datomic-conn {:tx-data attendance-txs})]
    (if (d/db-ok? result)
      {:attendance (get-attendance (:db-after result) gig+member)}
      (do
        (tap> result)
        result))))

(defn update-attendance-plan-tx [attendance plan]
  [:db/add (d/ref attendance) :attendance/plan plan])

(defn touch-attendance-tx [attendance]
  [:db/add (d/ref attendance) :attendance/updated (t/inst)])

(defn create-attendance-tx [db gig-id gigo-key]
  {:attendance/gig+member (q/gig+member gig-id gigo-key)
   :attendance/gig        [:gig/gig-id gig-id]
   :attendance/member     [:member/gigo-key gigo-key]
   :attendance/updated    (t/inst)
   :attendance/section    [:section/name (q/section-for-member db gigo-key)]})

(defn create-attendance-plan-tx [db gig-id gigo-key plan]
  (assoc (create-attendance-tx db gig-id gigo-key) :attendance/plan plan))

(defn update-attendance-plan! [{:keys [datomic-conn db] :as req} gig-id]
  (let [{:keys [gigo-key plan] :as params} (common/unwrap-params req)
        _ (assert gig-id)
        _ (assert gigo-key)
        attendance (get-attendance db gig-id gigo-key)
        plan-kw (str->plan plan)
        attendance-txs (if attendance
                         [(update-attendance-plan-tx attendance plan-kw)
                          (touch-attendance-tx attendance)]
                         [(create-attendance-plan-tx db gig-id gigo-key plan-kw)])]

    (assert plan-kw (format  "unknown plan value: '%s'" plan))
    (transact-attendance! datomic-conn attendance-txs (q/gig+member gig-id gigo-key))))

(defn create-attendance-comment-tx [db gig-id gigo-key comment]
  (assoc (create-attendance-tx db gig-id gigo-key) :attendance/comment comment))

(defn update-attendance-comment-tx [attendance comment]
  [:db/add (d/ref attendance) :attendance/comment comment])

(defn retract-attendance-comment-tx [attendance]
  [:db/retract (d/ref attendance) :attendance/comment])

(defn update-attendance-comment! [{:keys [datomic-conn db] :as req} gig-id]
  (let [{:keys [gigo-key comment] :as params} (common/unwrap-params req)
        _ (assert gig-id)
        _ (assert gigo-key)
        attendance (get-attendance db gig-id gigo-key)
        attendance-txs (if attendance
                         (if (str/blank? comment)
                           (if (:attendance/comment attendance)
                             [(retract-attendance-comment-tx attendance)
                              (touch-attendance-tx attendance)]
                             :nop)
                           [(update-attendance-comment-tx attendance comment)
                            (touch-attendance-tx attendance)])
                         (if (str/blank? comment)
                           :nop
                           [(create-attendance-comment-tx db gig-id gigo-key comment)]))]

    (if (= :nop attendance-txs)
      {:attendance attendance}
      (transact-attendance! datomic-conn attendance-txs (q/gig+member gig-id gigo-key)))))

(defn create-attendance-motivation-tx [db gig-id gigo-key motivation]
  (assoc (create-attendance-tx db gig-id gigo-key) :attendance/motivation motivation))

(defn update-attendance-motivation-tx [attendance motivation]
  [:db/add (d/ref attendance) :attendance/motivation motivation])

(defn update-attendance-motivation! [{:keys [datomic-conn db] :as req} gig-id]
  (let [{:keys [gigo-key motivation] :as params} (common/unwrap-params req)
        _ (assert gig-id)
        _ (assert gigo-key)
        attendance (get-attendance db gig-id gigo-key)
        motivation-kw (str->motivation motivation)
        attendance-txs  (if attendance
                          [(update-attendance-motivation-tx attendance motivation-kw)
                           (touch-attendance-tx attendance)]
                          [(create-attendance-motivation-tx db gig-id gigo-key motivation-kw)])]
    (assert motivation-kw (format  "unknown motivation value: %s" motivation))
    (transact-attendance! datomic-conn attendance-txs (q/gig+member gig-id gigo-key))))

(defn transact-gig! [datomic-conn gig-txs gig-id]
  (let [result (datomic/transact datomic-conn {:tx-data gig-txs})]
    {:gig (retrieve-gig (:db-after result) gig-id)}))

(defn post-comment! [{:keys [datomic-conn] :as req}]
  (let [{:keys [body]} (common/unwrap-params req)
        author (d/ref (auth/get-current-member req))
        gig-id (-> req :path-params :gig/gig-id)
        gig-txs [{:db/id "new_comment"
                  :comment/comment-id (sq/generate-squuid)
                  :comment/body body
                  :comment/author author
                  :comment/created-at (t/inst)}
                 [:db/add [:gig/gig-id gig-id] :gig/comments "new_comment"]]]
    (:gig
     (transact-gig! datomic-conn gig-txs gig-id))))

(def UpdateGig
  "This schema describes the http post we receive when updating a gig's info"
  (s/schema
   [:map {:name ::UpdateGig}
    [:gig-id ::s/non-blank-string]
    [:title ::s/non-blank-string]
    [:date ::s/date]
    [:end-date {:optional true} ::s/date]
    [:location ::s/non-blank-string]
    [:contact :string]
    [:gig-type (s/enum-from (map name domain/gig-types))]
    [:status (s/enum-from (map name domain/statuses))]
    [:call-time ::s/time]
    [:set-time {:optional true} ::s/time]
    [:end-time {:optional true} ::s/time]
    [:leader {:optional true} :string]
    [:pay-deal {:optional true} :string]
    [:outfit {:optional true} :string]
    [:more-details {:optional true} :string]
    [:setlist {:optional true} :string]
    [:post-gig-plans {:optional true} :string]]))

(defn update-gig! [{:keys [datomic-conn] :as req}]
  (let [gig-id (common/path-param req :gig/gig-id)
        params   (-> req common/unwrap-params util/remove-nils (assoc :gig-id gig-id))
        decoded  (util/remove-nils (s/decode UpdateGig params))]
    (if (s/valid? UpdateGig decoded)
      (let [tx (-> decoded
                   (common/ns-qualify-key :gig)
                   (update :gig/status str->status)
                   (update :gig/gig-type str->gig-type)
                   (update :gig/contact (fn [gigo-key] [:member/gigo-key gigo-key]))
                   (domain/gig->db))]
        ;; (tap> {:params params :decoded decoded :tx tx :expl (s/explain-human UpdateGig decoded)})
        (transact-gig! datomic-conn [tx] gig-id))
      (s/throw-error "Cannot update the gig. The gig data is invalid." nil  UpdateGig decoded))))

(defn create-gig! [{:keys [datomic-conn] :as req}]
  (let [gig-id (str (sq/generate-squuid))
        params (-> req util/unwrap-params util/remove-empty-strings util/remove-nils (assoc :gig-id gig-id))
        decoded (util/remove-nils (s/decode UpdateGig params))
        tx (-> decoded (common/ns-qualify-key :gig)
               (update :gig/status str->status)
               (update :gig/gig-type str->gig-type)
               (update :gig/contact (fn [gigo-key] [:member/gigo-key gigo-key]))
               (domain/gig->db))]
    (if (s/valid? UpdateGig decoded)
      (transact-gig! datomic-conn [tx] gig-id)
      (s/throw-error "Cannot create the gig. The gig data is invalid." nil  UpdateGig decoded))))

(defn reconcile-setlist [eid new-song-tuples current-song-tuples]
  (let [[added removed] (clojure.data/diff (set new-song-tuples)  (set current-song-tuples))
        add-tx (map #(-> [:db/add eid :setlist.v1/ordered-songs %]) (filter some? added))
        remove-tx (map #(-> [:db/retract eid :setlist.v1/ordered-songs %]) (filter some? removed))]
    (concat add-tx remove-tx)))

(defn update-setlist!
  "Updates the setlist for the current gig. song-ids are ordered. Returns the songs in the setlist."
  [{:keys [datomic-conn db] :as req} song-ids]
  (let [gig-id (common/path-param req :gig/gig-id)
        song-tuples (map-indexed (fn [idx sid] [[:song/song-id sid] idx]) song-ids)
        current (q/setlist-song-tuples-for-gig db  gig-id)
        txs (reconcile-setlist "setlist" song-tuples current)
        tx  {:setlist/gig [:gig/gig-id gig-id]
             :db/id "setlist"
             :setlist/version :setlist.version/v1}
        txs (concat [tx] txs)
        result (datomic/transact datomic-conn {:tx-data txs})]
    (q/find-songs (:db-after result) song-ids)))

(defn reconcile-probeplan [eid new-song-tuples current-song-tuples]
  (let [[added removed] (clojure.data/diff (set new-song-tuples)  (set current-song-tuples))
        add-tx (map #(-> [:db/add eid :probeplan.classic/ordered-songs %]) (filter some? added))
        remove-tx (map #(-> [:db/retract eid :probeplan.classic/ordered-songs %]) (filter some? removed))]
    (concat add-tx remove-tx)))

(defn probeplan-song-tx [{:keys [song-id emphasis position] :as s}]
  [[:song/song-id (util/ensure-uuid song-id)]
   (Integer/parseInt position)
   (or
    (probeplan.domain/str->play-emphasis emphasis) probeplan.domain/probeplan-classic-default-emphasis)])

(defn update-probeplan!
  "Updates the probeplan for the current gig. song-ids are ordered. Returns the songs in the setlist."
  [{:keys [datomic-conn db] :as req} song-id-emphases]
  (let [gig-id (common/path-param req :gig/gig-id)
        song-tuples (map probeplan-song-tx song-id-emphases)
        current (q/probeplan-song-tuples-for-gig db  gig-id)
        ;; _ (tap> {:sides song-id-emphases :song-tups song-tuples :current current})
        txs (reconcile-probeplan "probeplan" song-tuples current)
        tx  {:probeplan/gig [:gig/gig-id gig-id]
             :db/id "probeplan"
             :probeplan/version :probeplan.version/classic}
        txs (concat [tx] txs)
        ;; _ (tap> txs)
        result (datomic/transact datomic-conn {:tx-data txs})]
    (q/probeplan-songs-for-gig (:db-after result) gig-id)))

(clojure.core/comment
  (update-setlist! {:datomic-conn conn :db db :path-params {:gig/gig-id "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMDCiYe6CAw"}}
                   [#uuid "01844740-3eed-856d-84c1-c26f07068207"]) ;

  (q/setlist-song-tuples-for-gig db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMDCiYe6CAw") ;

  (reconcile-setlist "A" (set [[:song/song-id #uuid "01844740-3eed-856d-84c1-c26f07068207" 0]
                               [:song/song-id #uuid "01844740-3eed-856d-84c1-c26f0706820a" 1]])
                     (set [[:song/song-id #uuid "01844740-3eed-856d-84c1-c26f07068207" 1]
                           [:song/song-id #uuid "01844740-3eed-856d-84c1-c26f0706820a" 0]])) ;
  ;; p
  )

(defn upsert-log-play-tx [gig-id {:keys [song-id play-id feeling intensive]}]
  (let [song-id (common/ensure-uuid song-id)
        play-id (or (common/ensure-uuid play-id) (sq/generate-squuid))
        rating (keyword feeling)
        emphasis (keyword (if (string? intensive) intensive (second intensive)))]
    {:played/gig [:gig/gig-id gig-id]
     :played/song [:song/song-id song-id]
     :played/rating rating
     :played/gig+song (pr-str [gig-id song-id])
     :played/play-id play-id
     :played/emphasis emphasis}))

(defn log-play! [{:keys [datomic-conn] :as req} gig-id]
  (let [play-params (->> (common/unwrap-params req)
                         (map (fn [play]
                                ;; songs that are not played should not be marked as intensive
                                (if (= (:feeling play) "play-rating/not-played")
                                  (assoc play :intensive "play-emphasis/durch")
                                  play))))
        tx-data (map #(upsert-log-play-tx gig-id %) play-params)
        result (d/transact datomic-conn {:tx-data tx-data})]
    (if (d/db-ok? result)
      {:plays (plays-by-gig (:db-after result) gig-id)}
      result)))

(clojure.core/comment

  (do
    (require '[integrant.repl.state :as state])
    (def env (:app.ig/env state/system))
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db  (datomic/db conn))) ;; rcf

  (q/attendances-for-gig db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q6uCQw")
  (q/attendances-for-gig db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q6uCAw")
  (tap> {:result
         (q/attendance-for-gig db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q6uCQw"
                               "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA2NP7ggoM")})

  ;;
  )
