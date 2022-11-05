(ns app.gigs.controller
  (:require
   [app.datomic :as d]
   [tick.core :as t]
   [com.yetanalytics.squuid :as sq]
   [app.util :as util]
   [medley.core :as m]
   [clojure.walk :as walk]
   [ctmx.form :as form]
   [app.controllers.common :as common]
   [datomic.client.api :as datomic]
   [app.queries :as q]))

(def gig-pattern [:gig/gig-id :gig/title :gig/status :gig/date :gig/location])

(def play-pattern [{:played/gig gig-pattern}
                   {:played/song [:song/song-id :song/title]}
                   :played/gig+song
                   :played/rating
                   :played/play-id
                   :played/emphasis])

(defn ->gig [gig]
  (-> gig
      (update :gig/date t/zoned-date-time)))

(defn query-result->gig [[{:gig/keys [title] :as gig}]]
  (->gig gig))

(defn find-all-gigs [db]
  (sort-by :gig/date
           (mapv query-result->gig
                 (d/find-all db :gig/gig-id gig-pattern))))

(defn retrieve-gig [db gig-id]
  (->gig
   (d/find-by db :gig/gig-id gig-id gig-pattern)))

(defn gigs-before [db time]
  (mapv query-result->gig
        (d/q '[:find (pull ?e pattern)
               :in $ ?time pattern
               :where
               [?e :gig/gig-id _]
               [?e :gig/date ?date]
               [(< ?date ?time)]] db time gig-pattern)))

(defn gigs-after [db time]
  (mapv query-result->gig
        (d/q '[:find (pull ?e pattern)
               :in $ ?time pattern
               :where
               [?e :gig/gig-id _]
               [?e :gig/date ?date]
               [(>= ?date ?time)]] db time gig-pattern)))

(defn gigs-future [db]
  (gigs-after db (t/inst)))

(defn gigs-past [db]
  (gigs-before db (t/inst)))

(defn query-result->play
  [[play]]
  play)

(defn plays-by-gig [db gig-id]
  (->> (d/find-all-by db :played/gig [:gig/gig-id gig-id] play-pattern)
       (map query-result->play)
       (sort-by #(-> % :played/song :song/title))))

(defn songs-not-played [plays all-songs]
  (->> all-songs
       (remove (fn [song]
                 (->> plays
                      (map #(-> % :played/song :song/song-id))
                      (some (fn [p] (= p (:song/song-id song)))))))))

(comment
  (def gig-id "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMD81q7OCww")
  (def _plays (plays-by-gig db gig-id))
  (def _songs (q/find-all-songs db))

  (map #(-> % :played/song :song/title) _plays)

  (def asterix {:song/title "Asterix"
                :song/song-id #uuid "01844740-3eed-856d-84c1-c26f07068207"})
  (def ymyl
    {:song/title "You Move You Lose"
     :song/song-id #uuid "01844740-3eed-856d-84c1-c26f07068217"})

  ;;  played-songs
  (def played-songs)
  _songs

  (def _not_played (songs-not-played _plays _songs))
  (map (fn [s]
         {:played/song s}) _not_played)

  (->> _plays
       (map #(-> % :played/song :song/song-id))
       (filter #(= % (:song/song-id ymyl))))

  (remove (fn [song]
            (->> _plays
                 (map #(-> % :played/song :song/song-id))
                 (filter #(do (tap> {:play %}) (= % (:song/song-id song)))))) _songs)

  (d/find-all-by db :played/gig [:gig/gig-id gig-id] play-pattern)
  (d/find-all-by db :played/gig + song "[\"ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMCc_fOfCQw\" #uuid \"01844740-3eed-856d-84c1-c26f0706820a\"]" play-pattern)
  (datomic/transact conn {:tx-data
                          (->>
                           (d/find-all db :played/play-id play-pattern)
                           (map first)
                           (map :played/play-id)
                           (map (fn [i] [:db/retractEntity [:played/play-id i]])))})
  ;;
  )
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
      (do
        (tap> result)
        result))))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require  '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn))) ;;rcf

  (d/find-all (datomic/db conn) :played/play-id play-pattern)

;;
  )
