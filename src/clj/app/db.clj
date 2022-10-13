(ns app.db
  (:require [datahike.api :as d]
            [datahike.impl.entity :as de]
            [tick.core :as t]
            [clojure.walk :as clojure.walk]
            [app.util :as util]))

(def SESSION_TIMEOUT (t/new-duration 20 :minutes))

(def schema
  [{:db/ident       :member/name
    :db/valueType   :db.type/string
    :db/unique      :db.unique/value
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "A member's name"}
   {:db/ident       :member/gigo-key
    :db/valueType   :db.type/string
    :db/unique      :db.unique/identity
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "The gigo member key (id)"}
   {:db/ident       :member/permission-granted?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "Whether or not we have permission to nudge the user"}
   {:db/ident       :member/phone
    :db/valueType   :db.type/string
    :db/unique      :db.unique/value
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "The phone number of the member"}
   {:db/ident       :member/sessions
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc         "The chatbot sessions"}
   {:db/ident       :session/member
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "The member the session is for"}
   {:db/ident       :session/id
    :db/valueType   :db.type/string
    :db/unique      :db.unique/identity
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "The dialogflow session id"}
   {:db/ident       :session/last-activity-at
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/doc         "The time a tx or rx happened"}
   {:db/ident       :session/started
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "When the session was started"}
   {:db/ident       :session/ended
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "When the session ended"}
   {:db/ident       :gig/id
    :db/valueType   :db.type/string
    :db/unique      :db.unique/identity
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "The gig id from gig-o-matic."}
   {:db/ident       :gig/title
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "The title of the gig"}
   {:db/ident       :gig/date
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/doc         "The date the gig takes place. Stored as an instant. Disregard the time portion"}
   {:db/ident       :gig/location
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Where the gig takes place"}

   {:db/ident       :gig/status
    :db/valueType   :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc         "The status of the gig"}
   {:db/ident       :dialogflow/entity-value
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "The last known value used as the entity in dialogflow. This should usually be the title."}

   {:db/ident       :song/play-count
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "The total number of times the song has been played"}

   {:db/ident       :song/title
    :db/valueType   :db.type/string
    :db/unique      :db.unique/value
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "The title of the song"}

   {:db/ident       :song/play-count
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc         "The total number of times the song has been played"}

   {:db/ident       :song/last-played
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/doc         "The time the song was last played"}

   {:db/ident       :song/active?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "Whether or not the song is part of the active repertoire"}

   {:db/ident       :played/song
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "The song that was played"}

   {:db/ident       :played/gig
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "The gig/probe that the song was played at"}

   {:db/ident       :played/rating
    :db/valueType   :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "The impression of how well the song was played"}

   {:db/ident       :played/emphasis
    :db/valueType   :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/index       true
    :db/doc         "Intensiv or normal when played at a probe"}

   ;
   ])

(defn idempotent-schema-install! [conn]
  (d/transact conn schema))

(defn export-entity
  [entity]
  (clojure.walk/prewalk
   (fn [x]
     (if (de/entity? x)
       (into {} x)
       x))
   entity))

(defn find-by
  "Returns the unique entity identified by attr and val."
  [db attr attr-val]
  (d/entity db
            (ffirst (d/q '[:find ?e
                           :in $ ?attr ?val
                           :where [?e ?attr ?val]]
                         db attr attr-val))))

(defn find-all-by
  "Returns the entities having attr and val"
  [db attr attr-val]
  (mapv (partial d/entity db) (d/q '[:find [?e ...]
                                     :in $ ?attr ?val
                                     :where [?e ?attr ?val]]
                                   db attr attr-val)))

(defn entities
  "Given the result of a query, where all results are entity ids, turns them into entities"
  [db res]
  (mapv (partial d/entity db) res))

(defn find-all
  "Returns a list of all entities having attr"
  [db attr]
  (entities db
            (d/q '[:find [?e ...]
                   :in $ ?attr
                   :where [?e ?attr]] db attr)))

;; (find-by @conn :member/name "Casey")

(defn update-permission! [conn member-name permission-granted?]
  (d/transact conn [{:member/name member-name :member/permission-granted? permission-granted?}])
  nil)

(defn member-by-phone [db phone]
  (find-by db :member/phone phone))

(defn member-by-name [db name]
  (find-by db :member/name name))

(defn member-by-session-id [db id]
  (first (entities db
                   (d/q '[:find [?member ...]
                          :in $ ?id
                          :where
                          [?e :session/id ?id]
                          [?e :session/member ?member]]
                        db id))))

(defn member-set-permission! [conn member permission-granted?]
  (d/transact conn [{:db/id                      (:db/id member)
                     :member/permission-granted? permission-granted?}]))

(defn member-set-gigo-key! [conn member gigo-key]
  (d/transact conn [{:db/id           (:db/id member)
                     :member/gigo-key gigo-key}]))

(defn member-set-phone! [conn member phone]
  (d/transact conn [{:db/id        (:db/id member)
                     :member/phone phone}]))
(defn members [db]
  (d/pull-many db '[*]
               (mapv :db/id
                     (find-all db :member/name))))

(defn gig-db->model [entity]
  (-> entity
      export-entity
      (update :gig/date t/zoned-date-time)))
(defn gigs-db->model [es]
  (->> es
       (map gig-db->model)
       (sort-by :gig/date)))

(defn gigs [db]
  (gigs-db->model
   (find-all db :gig/id)))

(defn gigs-before [db t]
  (gigs-db->model
   (entities db (d/q '[:find [?e ...]
                       :in $ ?now
                       :where
                       [?e :gig/id _]
                       [?e :gig/date ?date]
                       [(< ?date ?now)]] db t))))

(defn gigs-after [db t]
  (gigs-db->model
   (entities db (d/q '[:find [?e ...]
                       :in $ ?now
                       :where
                       [?e :gig/id _]
                       [?e :gig/date ?date]
                       [(>= ?date ?now)]] db t))))

(defn gigs-future [db]
  (gigs-after db (t/inst)))

(defn gigs-missing-dialogflow-entity [db]
  (entities db (d/q '[:find [?e ...]
                      :in $
                      :where
                      [?e :gig/id _]
                      [(missing? $ ?e :dialogflow/entity-value)]] db)))

(defn gigs-with-dialogflow-entity [db]
  (entities db (d/q '[:find [?e ...]
                      :in $
                      :where
                      [?e :gig/id _]
                      [?e :dialogflow/entity-value _]] db)))

(defn- sessions-> [db ents]
  (->> ents
       (mapv :db/id)
       (mapv (partial d/pull db '[*]))))

(defn active-session-for-member [db member]
  (first (sessions-> db
                     (entities db
                               (d/q '[:find [?e ...]
                                      :in $ ?member
                                      :where
                                      [?e :session/started _]
                                      [?e :session/member ?member]
                                      [(missing? $ ?e :session/ended)]] db (:db/id member))))))

(defn sessions [db]
  (sessions-> db (find-all db :session/member)))

(defn open-sessions [db]
  (sessions-> db
              (entities db
                        (d/q '[:find [?e ...]
                               :in $
                               :where
                               [?e :session/started _]
                               [(missing? $ ?e :session/ended)]] db))))

(defn close-session! [conn session]
  (d/transact conn [{:db/id (:db/id session) :session/ended (t/inst)}]))

(defn create-session-tx [member id]
  (let [now (t/inst)]
    {:session/id               id
     :session/member           (:db/id member)
     :session/started          now
     :session/last-activity-at now}))

(defn create-session! [conn member id]
  (assert (nil? (active-session-for-member @conn member)) "An active session for member already exists!")
  (d/transact conn [(create-session-tx member id)]))

(defn session-expired? [session]
  (t/> (t/now)
       (t/>> (:session/last-activity-at session) SESSION_TIMEOUT)))

(defn session-get-or-create! [conn member id]
  (let [session (active-session-for-member @conn member)]
    (cond
      (nil? session) (do
                       (create-session! conn member id)
                       (active-session-for-member @conn member))
      (session-expired? session) (do
                                   (close-session! conn session)
                                   (create-session! conn member id)
                                   (active-session-for-member @conn member))
      :else session)))

(defn touch-session! [conn session]
  (d/transact conn [{:db/id                    (:db/id session)
                     :session/last-activity-at (t/inst)}]))

(defn create-song-tx [title]
  {:song/title title
   :song/active? true
   :song/play-count 0})

(defn create-song! [conn title]
  (d/transact conn [(create-song-tx title)]))

(defn song-db->model [entity]
  (-> entity
      export-entity))

(defn songs-db->model [es]
  (->> es
       (map song-db->model)
       (sort-by :song/title)))

(defn songs [db]
  (util/isort-by :song/title
                 (songs-db->model
                  (find-all db :song/title))))
(comment
  (do
    (require '[integrant.repl.state :as state])
    (require '[tick.core :as t])
    (def conn (:ol.datahike.ig/connection state/system)))

  {:session/member           [:member/name "Casey"]
   :session/started          (t/inst)
   :session/last-activity-at nil}

  @conn
  (d/transact conn schema)
  (d/transact conn [{:member/name  "Casey"
                     :member/phone "+43000000"}])
  (d/transact conn [{:session/member           [:member/name "Casey"]
                     :session/started          (t/inst)
                     :session/state            :start
                     :session/last-activity-at (t/inst)}])

  (def casey
    (member-by-phone @conn "+436770000"))

  (d/transact conn [{:member/name  "ANotherOne"
                     :member/phone "+4360000"}])

  (def casey-session
    (active-session-for-member @conn casey))
  (create-session! conn casey "random-thing")
  (session-get-or-create! conn casey (str (rand-int 1000)))

  (->> (open-sessions @conn)
       first
       (close-session! conn))
  (open-sessions @conn)
  (sessions @conn)
  (gigs @conn)
  (members @conn)
  (gigs @conn)
  (find-all @conn :gig/id)

  (d/transact conn [{:gig/id "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMCI17rBCAw"
                     :dialogflow/entity-value "super"}])

  (mapv :gig/title (gigs-before @conn ()))
  (:dialogflow/entity-value (find-by @conn :gig/title "draussenprobe"))

  (d/transact conn [[:db/retract 36
                     :dialogflow/entity-value "draussenprobe"]])

  (d/transact conn
              (mapv (fn [g] [:db/retract (:db/id g) :dialogflow/entity-value]) (gigs @conn)))
  (gigs-missing-dialogflow-entity @conn)
  (mapv :member/name (members @conn))

  (->
   (gigs-future @conn)
   first)

  (type
   (t/zoned-date-time
    #inst "2020-05-11"))

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

  (d/transact conn
              (mapv create-song-tx _titles))
  (songs @conn)
  ;
  )
