(ns app.probeplan.controller
  (:require
   [app.datomic :as d]
   [app.queries :as q]
   [datomic.client.api :as datomic]
   [medley.core :as m]
   [tick.core :as t]
   [tick.alpha.interval :as t.i]
   [app.debug :as debug]
   [app.util :as util]))

(defn update-song-play-stats [db])

(defn days-since [d as-of]
  (t/days
   (t/between d as-of)))

(defn fetch-plays [db as-of]
  (->>
   (d/find-all db :played/gig [{:played/song [:song/song-id :song/title]} :played/rating :played/emphasis {:played/gig [:gig/gig-id :gig/date :gig/gig-type]}])
   (map first)
   (map #(-> %
             (merge (:played/gig %))
             (dissoc :played/gig)
             (merge (:played/song %))
             (dissoc :played/song)))
   (group-by :song/title)
   (vals)
   (map (fn [gs]
          {:song/song-id (-> gs first :song/song-id)
           :song/title (-> gs first :song/title)
           :plays
           (->> gs
                (sort-by :gig/date t/>)
                (remove #(= :play-rating/not-played (:played/rating %)))
                (map (fn [g]
                       (-> g
                           (update :gig/date #(t/date-time %))
                           (assoc :days-since (days-since (:gig/date g) as-of))))))}))))

(defn days-since-when [pred plays]
  (when-let [play (m/find-first pred plays)]
    (:days-since play)))

(defn calc-play-stat [window-cutoff {:keys [plays] :as p}]
  (let [last-play (first plays)
        reversed (reverse plays)
        is-gig? #(= :gig.type/gig (:gig/gig-type %))
        is-probe? #(#{:gig.type/probe :gig.type/extra-probe} (:gig/gig-type %))
        is-intensive? #(= :play-emphasis/intensiv (:played/emphasis %))
        is-good? #(= :play-rating/good (:played/rating %))
        is-bad? #(= :play-rating/bad (:played/rating %))
        is-ok? #(= :play-rating/ok (:played/rating %))
        windowed-plays (filter #(t/>= (:gig/date %) window-cutoff) plays)]
    (-> p
        (assoc :last-performance (:gig/gig-id (m/find-first is-gig? plays)))
        (assoc :last-rehearsal (:gig/gig-id (m/find-first is-probe? plays)))
        (assoc :last-intensive (:gig/gig-id (m/find-first is-intensive? plays)))
        (assoc :first-performance (:gig/gig-id (m/find-first is-gig? reversed)))
        (assoc :first-rehearsal (:gig/gig-id (m/find-first is-probe? reversed)))
        (assoc :total-plays (count plays))
        (assoc :total-performances (count (filter is-gig? plays)))
        (assoc :total-rehearsals (count (filter is-probe? plays)))
        (assoc :total-rating-good (count (filter is-good? plays)))
        (assoc :total-rating-bad (count (filter is-bad? plays)))
        (assoc :total-rating-ok (count (filter is-ok? plays)))
        (assoc :windowed-total-rating-good (count (filter is-good? windowed-plays)))
        (assoc :windowed-total-rating-bad (count (filter is-bad? windowed-plays)))
        (assoc :windowed-total-rating-ok (count (filter is-ok? windowed-plays)))
        (assoc :last-played-on (:gig/date last-play))
        (assoc :days-since-last-played (:days-since last-play))
        (assoc :days-since-performed (days-since-when is-gig? plays))
        (assoc :days-since-rehearsed (days-since-when is-probe? plays))
        (assoc :days-since-intensive (days-since-when is-intensive? plays))
        ;;
        )))

(defn stat-tx [stat]
  {:song/song-id                     (:song/song-id stat)
   :song/total-plays                 (:total-plays stat)
   :song/total-performances          (:total-performances stat)
   :song/total-rehearsals            (:total-rehearsals stat)
   :song/total-rating-good           (:total-rating-good stat)
   :song/total-rating-bad            (:total-rating-bad stat)
   :song/total-rating-ok             (:total-rating-ok stat)
   :song/six-month-total-rating-good (:windowed-total-rating-good stat)
   :song/six-month-total-rating-bad  (:windowed-total-rating-bad stat)
   :song/six-month-total-rating-ok   (:windowed-total-rating-ok stat)
   :song/days-since-performed        (:days-since-performed stat)
   :song/days-since-rehearsed        (:days-since-rehearsed stat)
   :song/days-since-intensive        (:days-since-intensive stat)
   :song/last-played-on              (:last-played-on stat)
   :song/last-performance            (when-let [v (:last-performance stat)] [:gig/gig-id v])
   :song/last-rehearsal              (when-let [v (:last-rehearsal stat)] [:gig/gig-id v])
   :song/last-intensive              (when-let [v (:last-intensive stat)] [:gig/gig-id v])
   :song/first-performance           (when-let [v (:first-performance stat)] [:gig/gig-id v])
   :song/first-rehearsal             (when-let [v (:first-rehearsal stat)] [:gig/gig-id v])})

(defn calc-stats [db]
  (let [window-period (t/new-period 6 :months)
        today (t/at (t/date) (t/midnight))
        window-cutoff (t/<< today window-period)]
    (->> (fetch-plays db today)
         (map (partial calc-play-stat window-cutoff))
         (map util/remove-nils)
         (map stat-tx)
         (map util/remove-nils))))

(defn calc-and-save-play-stats! [conn]
  (datomic/transact conn {:tx-data
                          (calc-stats (datomic/db conn))}))

(comment
  (d/find-all db :played/song [{:played/song [:song/title]}])
  (def today (t/at (t/today) (t/midnight)))
  (fetch-plays db today)
  (def window-period (t/new-period 6 :months))
  (calc-stats db)

  (calc-and-save-play-stats! conn)

  (do
    (require '[integrant.repl.state :as state])
    (def env (:app.ig/env state/system))
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db  (datomic/db conn))) ;; rcf

  ;;
  )
