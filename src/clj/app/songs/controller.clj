(ns app.songs.controller
  (:require
   [app.datomic :as d]
   [com.yetanalytics.squuid :as sq]
   [app.util :as util]
   [ctmx.form :as form]
   [app.db :as db]
   [app.queries :as q]))

(defn create-song! [req]
  (let [title (-> req util/unwrap-params :song)
        song-id (sq/generate-squuid)
        result (d/transact (:datomic-conn req)
                           {:tx-data [{:song/title title :song/song-id song-id
                                       :song/play-count 0
                                       :song/active? true}]})]
    (if  (d/db-ok? result)
      {:song result}
      result)))

;; (defn update-song! [req]
;;   (let [title (-> req util/unwrap-params :song)
;;         song-id (-> req :path-params :song/song-id)
;;         result (transact-song (:datomic-conn req) {:title title :song-id song-id})]
;;     (if  (db-ok? result)
;;       {:song result}
;;       result)))

(defn log-play! [req]
  (let [{:keys [gig-id song-id play-type feeling]} (util/unwrap-params req)
        rating (keyword feeling)
        emphasis (keyword play-type)
        play-id (sq/generate-squuid)
        result (d/transact (:datomic-conn req) {:tx-data
                                                [{:played/song [:gig/gig-id gig-id]
                                                  :played/gig  [:song/song-id song-id]
                                                  :played/rating rating
                                                  :played/play-id play-id
                                                  :played/emphasis emphasis}]})]
    (if (d/db-ok? result)
      {:play result}
      result)))

(defn retrieve-song [db title]
  (d/find-by db :song/title title q/song-pattern))

(comment

  (do
    (require '[integrant.repl.state :as state])

    (require  '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def req {:datomic-conn conn
              :db (datomic/db conn)
              :params {}}))

  (def _titles
    ["Kingdom Come"
     "Surfin"
     "Asterix" ,
     "Bella Ciao" ,
     "Cumbia Sobre el Mar" ,
     "Der Zug um 7.40" ,
     "Grenzrenner" ,
     "Inner Babylon" ,
     "Kids Aren't Alright" ,
     "Klezma 34" ,
     "Laisse Tomber Les Filles" ,
     "L’estaca del pueblo" ,
     "Metanioa" ,
     "Monkeys Rally" ,
     "Montserrat Serrat" ,
     "Rasta Funk" ,
     "Tammurriata Nera" ,
     "Tschufittl Cocek" ,
     "You Move You Lose" ,
     "Odessa Bulgar"])

  (d/transact conn {:tx-data
                    (mapv (fn [title]
                            {:song/title title
                             :song/active? true
                             :song/song-id (sq/generate-squuid)
                             :song/play-count 0}) _titles)})

  (q/find-all-songs (d/db conn))

  (let [res
        (create-song! (assoc-in req [:params :song-new_song] "Asterix"))]
    (unique-error? res))

  (d/transact conn {:tx-data [{:db/ident :song/title
                               :db/doc "The title of the song"
                               :db/valueType :db.type/string
                               :db/unique :db.unique/value
                               :db/cardinality :db.cardinality/one}]})
  ;;
  )
