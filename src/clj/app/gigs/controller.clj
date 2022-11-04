(ns app.gigs.controller
  (:require
   [app.datomic :as d]
   [tick.core :as t]
   [com.yetanalytics.squuid :as sq]
   [app.util :as util]
   [medley.core :as m]
   [clojure.walk :as walk]
   [ctmx.form :as form]))

(def gig-pattern [:gig/gig-id :gig/title :gig/status :gig/date :gig/location])

(def play-pattern [{:played/gig gig-pattern}
                   :played/song [:song/song-id :song/title]
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
  (query-result->play
   (d/find-all-by db :played/gig [:gig/gig-id gig-id] play-pattern)))

(defn parse-log-params [params]
  (m/deep-merge
   (reduce (fn [m [k v]]
             (assoc m k {:song/song-id v}))
           {}
           (-> params :event-log-play :song-id))
   (reduce (fn [m [k v]]
             (assoc m k {:played/rating (keyword v)}))
           {}
           (-> params :event-log-play :feeling))
   (reduce (fn [m [k v]]
             (assoc m k {:played/emphasis
                         (keyword (if (string? v)
                                    v
                                    (second v)))}))
           {}
           (-> params :event-log-play :intensive))))

(defn group-by-song [data]
  (reduce (fn [m [k v]]
            (assoc m (:song/song-id v) v))
          {}
          data))

(defn create-log-play! [conn gig-id play]
  (d/transact conn {:tx-data
                    [{:played/song [:gig/gig-id gig-id]
                      :played/gig  [:song/song-id (:played/song-id play)]
                      :played/rating (:played/rating play)
                      :played/play-id (sq/generate-squuid)
                      :played/emphasis (:played/emphasis play)}]}))

(defn log-play! [{:keys [db datomic-conn] :as req} gig-id]
  (let [foo (->> :params req form/nest-params walk/keywordize-keys parse-log-params group-by-song)
        _ (tap> foo)
        plays (filter (fn [m] (not= :play-rating/not-played (:played/rating m))) (vals foo))
        _ (tap> plays)
        results (map
                 (partial create-log-play! datomic-conn gig-id) plays)
        errors (some #(d/db-error? %) results)]
    (tap> results)
    (if errors
      {:error errors
       :params foo}
      {:plays results})))

(comment
  (def data {:event-log-play {:intensive {:14 "play-emphasis/durch"
                                          :0 ["play-emphasis/durch" "play-emphasis/intensiv"]}
                              :feeling {:14 "play-rating/not-played"
                                        :18 "play-rating/not-played"
                                        :12 "play-rating/not-played"
                                        :11 "play-rating/not-played"
                                        :24 "play-rating/not-played"
                                        :10 "play-rating/not-played"
                                        :21 "play-rating/not-played"
                                        :23 "play-rating/not-played"
                                        :13 "play-rating/not-played"
                                        :0 "play-rating/not-played"
                                        :4 "play-rating/not-played"
                                        :26 "play-rating/not-played"
                                        :16 "play-rating/not-played"
                                        :7 "play-rating/not-played"
                                        :1 "play-rating/not-played"
                                        :8 "play-rating/not-played"
                                        :22 "play-rating/not-played"
                                        :25 "play-rating/not-played"
                                        :9 "play-rating/not-played"
                                        :20 "play-rating/not-played"
                                        :17 "play-rating/not-played"
                                        :19 "play-rating/not-played"
                                        :2 "play-rating/not-played"
                                        :5 "play-rating/not-played"
                                        :15 "play-rating/not-played"
                                        :3 "play-rating/not-played"
                                        :6 "play-rating/not-played"}
                              :song {:14 "Laisse Tomber Les Filles"
                                     :18 "Montserrat Serrat"
                                     :12 "Kingdom Come"
                                     :11 "Kids Aren't Alright"
                                     :24 "tralala"
                                     :10 "Inner Babylon"
                                     :21 "Surfin"
                                     :23 "Trala!"
                                     :13 "Klezma 34"
                                     :0 "<script>alert(\"OMG\");</script>"
                                     :4 "Bella Ciao"
                                     :26 "You Move You Lose"
                                     :16 "Metanioa"
                                     :7 "foobar"
                                     :1 "<script>alert(?XSS?);</script>"
                                     :8 "FooBar!"
                                     :22 "Tammurriata Nera"
                                     :25 "Tschufittl Cocek"
                                     :9 "Grenzrenner"
                                     :20 "Rasta Funk"
                                     :17 "Monkeys Rally"
                                     :19 "Odessa Bulgar"
                                     :2 "Asterix"
                                     :5 "Cumbia Sobre el Mar"
                                     :15 "L’estaca del pueblo"
                                     :3 "Asterix2"
                                     :6 "Der Zug um 7.40"}}})

  (reduce (fn [m [k v]]
            (assoc m (:song/title v) v))
          {}
          (parse-log-params data))
  (group-by (fn [v]
              (:song/title v))
            (vals
             (parse-log-params data)))

  (do
    (require '[integrant.repl.state :as state])
    (require  '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def req {:datomic-conn conn
              :db (datomic/db conn)
              :params {}}))

  (d/find-all (datomic/db conn) :played/play-id play-pattern)

  ;; rich comment
  )
