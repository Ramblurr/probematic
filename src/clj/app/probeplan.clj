(ns app.probeplan
  (:require [app.datomic :as d]
            [app.queries :as q]))

{:db/ident :probeplan/gig
 :db/doc "The gig this probeplan belongs to"
 :db/valueType :db.type/ref
 :db/unique :db.unique/unique
 :db/cardinality :db.cardinality/one}

{:db/ident :probeplan/format
 :db/doc "The format of the probeplan"
 :db/valueType :db.type/keyword
 :db/cardinality :db.cardinality/one}

{:db/ident :probeplan.classic/intensive1
 :db/doc "The first intensive song"
 :db/valueType :db.type/ref
 :db/cardinality :db.cardinality/one}

{:db/ident :probeplan.classic/intensive2
 :db/doc "The second intensive song"
 :db/valueType :db.type/ref
 :db/cardinality :db.cardinality/one}

{:db/ident :probeplan.classic/others
 :db/doc "The other songs to be 'durchgespielt'"
 :db/valueType :db.type/ref
 :db/cardinality :db.cardinality/many}

{:db/ident :probeplan.setlist/ordered-songs
 :db/doc "The other songs to be 'durchgespielt'"
 :db/valueType :db.type/ref
 :db/cardinality :db.cardinality/many}

{:db/ident :setlist/song
 :db/doc "The song to be played"
 :db/valueType :db.type/ref
 :db/cardinality :db.cardinality/one}

{:db/ident :setlist/position
 :db/doc "The position of the song in the setlist"
 :db/valueType :db.type/long
 :db/cardinality :db.cardinality/one}

(defn gen-plan-for-gig [db gig]
  (let [tx-data [{:probeplan/gig (d/ref gig)
                  :probeplan/format :classic
                  :probeplan.classic/intensive1 nil
                  :probeplan.classic/intensive2 nil
                  :probeplan.classic/others []}]]))

(def classic-num-intensive 2)
(def classic-num-durchspielen 5)

(defn gen-classic-from-last [song-cycle {:probeplan.classic/keys [intensive1 intensive2 others]}])
(defn gen-classic-fresh [song-cycle]
  (let [num-to-take (+ classic-num-durchspielen classic-num-durchspielen)
        songs (take num-to-take song-cycle)
        intensive1 (first songs)
        intensive2 (second songs)
        others (take classic-num-durchspielen (nthrest songs 2))]
    {:probeplan.classic/intensive1 intensive1
     :probeplan.classic/intensive2 intensive2
     :probeplan.classic/others others}))

(defn gen-classic [song-cycle last-plan]
  (if last-plan
    (gen-classic-from-last song-cycle last-plan)
    (gen-classic-fresh song-cycle)))

(defn make-song-cycle
  "Return a lazy, infinite seq of songs, optionally shuffled once."
  ([db]
   (make-song-cycle db false))
  ([db shuffle?]
   (let [songs (q/find-all-songs db)]
     (cycle
      (if shuffle?
        (shuffle songs)
        songs)))))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require  '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn)))         ; rcF

  (let [song-cycle (make-song-cycle db)]
    (take 7 song-cycle))

  (take 5 (nthrest (make-song-cycle db) 2))

  (gen-classic-fresh (make-song-cycle db))

  ;;
  )
