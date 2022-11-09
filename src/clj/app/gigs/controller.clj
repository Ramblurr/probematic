(ns app.gigs.controller
  (:refer-clojure :exclude [comment])
  (:require
   [app.datomic :as d]
   [app.queries :as q]
   [tick.core :as t]
   [com.yetanalytics.squuid :as sq]
   [app.util :as util]
   [medley.core :as m]
   [clojure.walk :as walk]
   [ctmx.form :as form]
   [app.controllers.common :as common]
   [datomic.client.api :as datomic]
   [clojure.set :as set]
   [clojure.string :as str]
   [app.debug :as debug]))

(def plans [:plan/definitely
            :plan/probably
            :plan/unknown
            :plan/probably-not
            :plan/definitely-not
            :plan/not-interested])

(def str->plan (zipmap (map name plans) plans))

(def motivations [:motivation/none
                  :motivation/very-high
                  :motivation/high
                  :motivation/medium
                  :motivation/low
                  :motivation/very-low])
(def str->motivation (zipmap (map name motivations) motivations))

(defn ->comment [comment]
  (-> comment
      (m/update-existing :comment/created-at t/date-time)))

(defn ->gig [gig]
  (debug/xxx
   (-> gig
       (m/update-existing :gig/call-time t/time)
       (m/update-existing :gig/end-time t/time)
       (m/update-existing :gig/set-time t/time)
       (m/update-existing :gig/date t/date-time)
       (m/update-existing :gig/end-date t/date-time)
       (m/update-existing :gig/comments #(->> %
                                              (map ->comment)
                                              (sort-by :comment/created-at))))))

(defn query-result->gig [[{:gig/keys [title] :as gig}]]
  (->gig gig))

(defn find-all-gigs [db]
  (sort-by :gig/date
           (mapv query-result->gig
                 (d/find-all db :gig/gig-id q/gig-pattern))))

(defn retrieve-gig [db gig-id]
  (->gig
   (d/find-by db :gig/gig-id gig-id q/gig-detail-pattern)))

(defn gigs-before [db time]
  (mapv query-result->gig
        (d/q '[:find (pull ?e pattern)
               :in $ ?time pattern
               :where
               [?e :gig/gig-id _]
               [?e :gig/date ?date]
               [(< ?date ?time)]] db time q/gig-pattern)))

(defn gigs-after [db time]
  (mapv query-result->gig
        (d/q '[:find (pull ?e pattern)
               :in $ ?time pattern
               :where
               [?e :gig/gig-id _]
               [?e :gig/date ?date]
               [(>= ?date ?time)]] db time q/gig-pattern)))

(defn gigs-future [db]
  (gigs-after db (t/inst)))

(defn gigs-past [db]
  (gigs-before db (t/inst)))

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
  (let [result (d/transact datomic-conn {:tx-data gig-txs})]
    (if (d/db-ok? result)
      {:gig (retrieve-gig (:db-after result) gig-id)}
      (do
        (tap> result)
        result))))

(defn post-comment! [{:keys [datomic-conn] :as req}]
  (let [{:keys [body]} (common/unwrap-params req)
        author [:member/gigo-key "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA2NP7ggoM"]
        gig-id (-> req :path-params :gig/gig-id)
        gig-txs [{:db/id "new_comment"
                  :comment/comment-id (sq/generate-squuid)
                  :comment/body body
                  :comment/author author
                  :comment/created-at (t/inst)}
                 [:db/add [:gig/gig-id gig-id] :gig/comments "new_comment"]]]
    (:gig
     (transact-gig! datomic-conn gig-txs gig-id))))

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
