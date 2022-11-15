(ns app.gigs.controller
  (:refer-clojure :exclude [comment])
  (:require
   [app.controllers.common :as common]
   [app.datomic :as d]
   [app.debug :as debug]
   [app.gigs.domain :as domain]
   [app.queries :as q]
   [app.schemas :as s]
   [app.util :as util]
   [clojure.string :as str]
   [com.yetanalytics.squuid :as sq]
   [datomic.client.api :as datomic]
   [tick.core :as t]
   [app.auth :as auth]))

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

(defn gigs-before [db time]
  (results->gigs  (d/q '[:find (pull ?e pattern)
                         :in $ ?time pattern
                         :where
                         [?e :gig/gig-id _]
                         [?e :gig/date ?date]
                         [(< ?date ?time)]] db (t/inst time) q/gig-pattern)))

(defn gigs-after [db time]
  (results->gigs (d/q '[:find (pull ?e pattern)
                        :in $ ?reference-time pattern
                        :where
                        [?e :gig/gig-id _]
                        [?e :gig/date ?date]
                        [(>= ?date ?reference-time)]] db (t/inst time) q/gig-pattern)))

(defn gigs-between [db start end]
  (results->gigs (d/q '[:find (pull ?e pattern)
                        :in $ ?ref-start ?ref-end pattern
                        :where
                        [?e :gig/gig-id _]
                        [?e :gig/date ?date]
                        [(>= ?date ?ref-start)]
                        [(<= ?date ?ref-end)]] db
                      (t/inst start) (t/inst end) q/gig-pattern)))

(defn gigs-future [db]
  (gigs-after db (t/at (t/date) (t/midnight))))

(defn gigs-past [db]
  (gigs-before db (t/at (t/date) (t/midnight))))

(defn page
  ([offset limit coll]
   (take limit (drop offset coll)))
  ([limit coll]
   (page coll 0 limit)))

(defn gigs-past-page [db offset limit]
  (let [now (t/inst (t/at (t/date) (t/midnight)))]
    (->>
     (d/q '[:find (pull ?e pattern)
            :in $ ?time pattern
            :where
            [?e :gig/gig-id _]
            [?e :gig/date ?date]
            [(< ?date ?time)]] db now [:gig/gig-id :gig/date :db/id])
     (map first)
     (sort-by :gig/date)
     (page offset limit)
     (map :db/id)
     (d/pull-many db q/gig-pattern)
     (sort-by :gig/date)
     (mapv domain/db->gig))))

(defn gigs-past-two-weeks [db]
  (let [now (t/at (t/date) (t/midnight))
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

(defn gig+member [gig-id gigo-key]
  (pr-str [gig-id gigo-key]))

(defn get-attendance
  ([db gig-id gigo-key]
   (get-attendance db (gig+member gig-id gigo-key)))
  ([db gig+member]
   (assert (string? gig+member))
   (d/find-by db :attendance/gig+member gig+member q/attendance-pattern)))

(defn attendance-touch-ts [a]
  (assoc a :attendance/updated (t/inst)))

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
  {:attendance/gig+member (gig+member gig-id gigo-key)
   :attendance/gig        [:gig/gig-id gig-id]
   :attendance/member     [:member/gigo-key gigo-key]
   :attendance/updated    (t/inst)
   :attendance/section    [:section/name (q/section-for-member db gigo-key)]})

(defn create-attendance-plan-tx [db gig-id gigo-key plan]
  (assoc (create-attendance-tx db gig-id gigo-key) :attendance/plan plan))

(defn update-attendance-plan! [{:keys [datomic-conn db] :as req}]
  (let [{:keys [gigo-key plan] :as params} (common/unwrap-params req)
        gig-id (-> req :path-params :gig/gig-id)
        attendance (get-attendance db gig-id gigo-key)
        plan-kw (str->plan plan)
        attendance-txs (if attendance
                         [(update-attendance-plan-tx attendance plan-kw)
                          (touch-attendance-tx attendance)]
                         [(create-attendance-plan-tx db gig-id gigo-key plan-kw)])]

    (assert plan-kw (format  "unknown plan value: '%s'" plan))
    (transact-attendance! datomic-conn attendance-txs (gig+member gig-id gigo-key))))

(defn create-attendance-comment-tx [db gig-id gigo-key comment]
  (assoc (create-attendance-tx db gig-id gigo-key) :attendance/comment comment))

(defn update-attendance-comment-tx [attendance comment]
  [:db/add (d/ref attendance) :attendance/comment comment])

(defn retract-attendance-comment-tx [attendance]
  [:db/retract (d/ref attendance) :attendance/comment])

(defn update-attendance-comment! [{:keys [datomic-conn db] :as req}]
  (let [{:keys [gigo-key comment] :as params} (common/unwrap-params req)
        gig-id (-> req :path-params :gig/gig-id)
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
      (transact-attendance! datomic-conn attendance-txs (gig+member gig-id gigo-key)))))

(defn create-attendance-motivation-tx [db gig-id gigo-key motivation]
  (assoc (create-attendance-tx db gig-id gigo-key) :attendance/motivation motivation))

(defn update-attendance-motivation-tx [attendance motivation]
  [:db/add (d/ref attendance) :attendance/motivation motivation])

(defn update-attendance-motivation! [{:keys [datomic-conn db] :as req}]
  (let [{:keys [gigo-key motivation] :as params} (common/unwrap-params req)
        gig-id (-> req :path-params :gig/gig-id)
        _ (assert gig-id)
        _ (assert gigo-key)
        attendance (get-attendance db gig-id gigo-key)
        motivation-kw (str->motivation motivation)
        attendance-txs  (if attendance
                          [(update-attendance-motivation-tx attendance motivation-kw)
                           (touch-attendance-tx attendance)]
                          [(create-attendance-motivation-tx db gig-id gigo-key motivation-kw)])]
    (assert motivation-kw (format  "unknown motivation value: %s" motivation))
    (transact-attendance! datomic-conn attendance-txs (gig+member gig-id gigo-key))))

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
    [:gig-type (domain/enum-from (map name domain/gig-types))]
    [:status (domain/enum-from (map name domain/statuses))]
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
        params   (-> req common/unwrap-params common/no-blanks util/remove-nils (assoc :gig-id gig-id))
        params (assoc params :date "")
        decoded  (s/decode UpdateGig params)]
    (if (s/valid? UpdateGig decoded)
      (let [tx (-> decoded
                   (common/ns-qualify-key :gig)
                   (update :gig/status str->status)
                   (update :gig/gig-type str->gig-type)
                   (update :gig/contact (fn [gigo-key] [:member/gigo-key gigo-key]))
                   (domain/gig->db))]
        (tap> {:params params :decoded decoded :tx tx :expl (s/explain-human UpdateGig decoded)})
        (transact-gig! datomic-conn [tx] gig-id))
      (s/throw-error "Cannot update the gig. The gig data is invalid." req UpdateGig decoded))))

(defn upsert-log-play-tx [gig-id {:keys [song-id play-id feeling intensive]}]
  (let [song-id (common/ensure-uuid song-id)
        play-id (or (common/ensure-uuid play-id) (sq/generate-squuid))
        rating (keyword feeling)
        emphasis (keyword (if (string? intensive) intensive (second intensive)))]
    {:played/gig [:gig/gig-id gig-id]
     :played/song  [:song/song-id song-id]
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
    (def db  (datomic/db conn)))        ;; rcf

  ;;
  )