(ns app.controllers.events
  (:require
   [medley.core :as m]
   [clojure.walk :as walk]
   [app.controllers.common :refer [unwrap-params get-conn]]
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

(defn log-play! [req]
  (comment
    (let [conn (get-conn req)
          {:keys [gig song play-type feeling comment]} (unwrap-params req)
          gig (db/gig-by-id @conn gig)
          song (db/song-by-title @conn song)
          rating (keyword feeling)
          emphasis (keyword play-type)
          result (db/create-play! conn gig song rating emphasis)
          error (-> result :error)]
      (if error
        error
        {:play result})))
  (let [foo (->> :params req form/nest-params walk/keywordize-keys parse-log-params group-by-song)]
    ;; (tap> (:params req))
    ;; (tap> (-> :params req form/nest-params))
    ;; (tap> foo)
    ;; (tap> (->> :params req form/nest-params (walk/postwalk form/vectorize-map)))
    ;; (tap> (form/json-params (:params req)))
    ;; (tap> (form/prune-params (-> :params req form/json-params)))
    ;; (tap> (unwrap-params req))
    {:error {}
     :params foo}))

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
