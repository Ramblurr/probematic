(ns app.songs.controller
  (:require
   [app.datomic :as d]
   [app.file-utils :as fu]
   [app.queries :as q]
   [app.util :as util]
   [com.yetanalytics.squuid :as sq]
   [ctmx.rt]
   [datomic.client.api :as datomic]))

(defn create-song! [req]
  (let [{:keys [title active?]} (-> req util/unwrap-params)
        song-id (sq/generate-squuid)
        result (d/transact (:datomic-conn req)
                           {:tx-data [{:song/title title :song/song-id song-id
                                       :song/play-count 0
                                       :song/active? (ctmx.rt/parse-boolean active?)}]})]
    (if (d/db-ok? result)
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

(defn retrieve-song [db song-id]
  (d/find-by db :song/song-id song-id q/song-pattern-detail))

(defn add-sheet-music! [{:keys [datomic-conn]} song-id section-name selected-path]
  (let [tx {:sheet-music/sheet-id (sq/generate-squuid)
            :sheet-music/song [:song/song-id song-id]
            :sheet-music/section [:section/name section-name]
            :sheet-music/title (fu/basename selected-path)
            :file/webdav-path selected-path}]
    (:db-after
     (datomic/transact datomic-conn {:tx-data [tx]}))))
(defn remove-sheet-music! [{:keys [datomic-conn]} sheet-id]
  (:db-after
   (datomic/transact datomic-conn {:tx-data [[:db/retractEntity [:sheet-music/sheet-id sheet-id]]]})))

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
