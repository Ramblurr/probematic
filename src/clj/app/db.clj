(ns app.db
  (:require [datahike.api :as d]
            [tick.core :as t]))

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
   {:db/ident       :dialogflow/entity-value
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "The last known value used as the entity in dialogflow. This should usually be the title."}])

(defn idempotent-schema-install! [conn]
  (d/transact conn schema))

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

(defn gigs [db]
  (find-all db :gig/id))

(defn gigs-before [db t]
  (entities db (d/q '[:find [?e ...]
                      :in $ ?now
                      :where
                      [?e :gig/id _]
                      [?e :gig/date ?date]
                      [(< ?date ?now)]] db t)))

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

  ;
  )
