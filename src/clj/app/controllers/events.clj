(ns app.controllers.events
  (:require
   [medley.core :as m]
   [clojure.walk :as walk]
   [app.controllers.common :refer [unwrap-params get-conn save-log-play!]]
   [ctmx.form :as form]
   [app.db :as db]))

(defn parse-log-params [params]
  (m/deep-merge
   (reduce (fn [m [k v]]
             (assoc m k {:song/title v}))
           {}
           (-> params :event-log-play :song))
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
            (assoc m (:song/title v) v))
          {}
          data))

(defn log-play! [req gig-id]
  (let [conn (get-conn req)
        foo (->> :params req form/nest-params walk/keywordize-keys parse-log-params group-by-song)
        plays (filter (fn [m] (not= :play-rating/not-played (:played/rating m))) (vals foo))
        results (map (fn [play]
                       (save-log-play! conn gig-id (:song/title play) (:played/rating play) (:played/emphasis play))) plays)
        errors (some #(contains? % :error) results)]
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

  ;; rich comment
  )
