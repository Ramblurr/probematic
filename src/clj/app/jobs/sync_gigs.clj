(ns app.jobs.sync-gigs
  (:require [ol.jobs-util :as jobs]
            [tick.core :as t]
            [datahike.api :as d]
            ;[dialogflow.core :as df-api]
            [clojure.string :as s]
            [clojure.set :as set]
            [app.db :as db]
            [app.gigo :as gigo]
            [app.features :as f])

  (:import (java.time DayOfWeek)))

;; Since gigo can be pretty slow, and it is a free service
;; we cache the list of gigs and attendance to avoid hammering it too much
;; (and also to speed up our own responses)
;;
;; We also need to store some details about the gigs outside the cache
;; in order to manage the dialogflow Gig entity
(defn- gig-tx [gig]
  {:gig/id    (:id gig)
   :gig/title (:title gig)
   :gig/location (:address gig)
   :gig/status (:status gig)
   :gig/date  (t/inst (t/at (:date gig) (t/midnight)))})

(defn- update-gigs-db! [conn gigs]
  (d/transact conn
              (map gig-tx gigs)))

(defn probe? [g]
  (and
   (s/includes?
    (s/lower-case (:gig/title g)) "probe")
   (= DayOfWeek/WEDNESDAY (-> g :gig/date (t/date) (t/day-of-week)))))

(defn hard-coded-enrichments [title]
  (cond
    (s/includes? title "fff") "demo"
    :else nil))

(defn enrich-synonyms [e]
  (let [title (:value e)
        tokens (->> (s/split title #" ")
                    (filter #(<= 3 (count %)))
                    (remove #(s/includes? (s/lower-case %) "probe")))
        extra (hard-coded-enrichments (s/lower-case title))
        tokens (if extra (conj tokens extra) tokens)]
    (update e :synonyms #(concat % tokens))))

(defn gig-to-ent [g]
  (enrich-synonyms
   {:value (:gig/title g) :synonyms [(:gig/title g)]}))

(defn match-entity-gig [existing gigs]
  (->> (set/join existing gigs {:value :dialogflow/entity-value})
       (vec)
       (mapv (fn [m]
               (let [changed? (not= (:value m) (:gig/title m))]
                 {:new-ent       (gig-to-ent m)
                  :old-ent-value (:value m)
                  :gig           (-> m
                                     (assoc :dialogflow/entity-value (:gig/title m))
                                     (select-keys [:gig/title :gig/id :dialogflow/entity-value]))
                  :changed?      changed?})))))
(comment
  (defn update-dialogflow-gig-entities!
    "Ok..

  Problem: Sometimes the title of a gig can change. Since dialogflow entities are string based (with no id field)
  we need a way to detect changes and properly update the gig entities in dialogflow.

  For example:

    time 1:
        gig title: Jolly Rabbit Demo
        dialogflow entity: Jolly Rabbit Demo

    time 2: (someone changes the gig name in gigo)
        gig title: Peter Rabbit Demo
        dialogflow entity: Jolly Rabbit Demo

        at this point the dialogflow entity is out of date. it needs to be changed too.

    time 3: (desired state after this function runs)
        gig title: Peter Rabbit Demo
        dialogflow entity: Peter Rabbit Demo <---- CREATED
        dialogflow entity: Jolly Rabbit Demo <---- DELETED


  Methodology:

  Since dialogflow entities have no id numbers, we can't easily tell which gig it belongs to.
  We have no local record of past gig titles to compare against... except we do, the field :dialogflow/entity-value is
  stored in the database for each gig. This attr records the last known dialogflow entity value for the gig. So if the gig/title changes
  out from under us, we can spot the difference and perform a change + delete as needed.

  We only delete when necessary, but we always update (potentially overwriting values with the same) just in case
  something changed with the synonyms (which could only happen with a code change, but :shrug:
  "

    [{:keys [dialogflow]} conn etc]
    (let [etn (df-api/entity-type-name (-> dialogflow :project-id)
                                       (-> dialogflow :gigs-entity-type-id))
          existing (:entities (df-api/get-entity-type etc etn))
          gigs (->> (db/gigs-with-dialogflow-entity @conn)
                    (remove probe?) ;; we remove probe gigs because we don't want a million probes in the list
                    (mapv #(select-keys % [:gig/id :gig/title :dialogflow/entity-value])))
          matches (match-entity-gig existing gigs)
          ents-to-update (conj (mapv :new-ent matches)
                               ;; we tack on the fixed probe gig to ensure it's always there
                               {:value "probe" :synonyms ["probe" "rehearsal" "rehersal"]})

                                        ; these gigs had their :dialogflow/entity-value attr changed
          gigs-to-update (->> matches
                              (filter :changed?)
                              (mapv :gig))
          ents-to-delete (->> matches
                              (filter :changed?)
                              (mapv :old-ent-value))]
                                        ;(tap> "matches dialogflow and gigs")
                                        ;(tap> matches)
      (when-not (empty? ents-to-delete)
        @(df-api/batch-delete-entities etc etn ents-to-delete "en")
        (d/transact conn gigs-to-update))
      @(df-api/batch-update-entities etc etn ents-to-update "en")
      {:titles-changed (count ents-to-delete)
       :total-updated  (count ents-to-update)})))

(comment
  (defn- prune-gig-entities-before!
    "Removes past gigs from the dialogflow entity list
  Arg t should be something that is coercable to inst.
  Doesn't remove then from the database"
    [{:keys [dialogflow]} conn etc t]
    (let [etn (df-api/entity-type-name (-> dialogflow :project-id)
                                       (-> dialogflow :gigs-entity-type-id))
          gigs (->> (db/gigs-before @conn (t/inst t))
                    (remove probe?))
          to-delete (->> gigs (mapv gig-to-ent)
                         (mapv :value))]
                                        ;(tap> "to-delete dialogflow gigs")
                                        ;(tap> to-delete)
      (when-not (empty? to-delete)
        @(df-api/batch-delete-entities etc etn to-delete "en")
        (d/transact conn
                    (mapv (fn [g] [:db/retract (:db/id g) :dialogflow/entity-value]) gigs))
        {:deleted (count to-delete)}))))

(comment
  (defn- create-dialogflow-gig-entities!
    "For freshly created gigs, creates a corresponding dialogflow gig entity"
    [{:keys [dialogflow]} conn etc]
    (let [etn (df-api/entity-type-name (-> dialogflow :project-id)
                                       (-> dialogflow :gigs-entity-type-id))
          gigs (->> (db/gigs-missing-dialogflow-entity @conn)
                    (remove probe?))
          to-create (mapv gig-to-ent gigs)]
                                        ;(tap> "to-create dialogflow gigs")
                                        ;(tap> to-create)
      (when-not (empty? to-create)
        @(df-api/batch-create-entities etc etn to-create "en")
        (d/transact conn
                    (->> gigs
                         (mapv #(select-keys % [:gig/id :dialogflow/entity-value :gig/title]))
                         (mapv #(assoc % :dialogflow/entity-value (:gig/title %)))))
        {:created (count to-create)}))))

(defn- sync-gigs [env conn gigo etc _]
  (when (f/feature? :feat/sync-gigs)
    (tap> "syncing gigs")
                                        ; update the cache  - the cache is stored in memory only
    (gigo/update-cache! gigo)

                                        ; update the gig list in the db
                                        ; we don't store all of the gig data, just what is necessary to manage the dialogflow entities
    (update-gigs-db! conn @gigo/gigs-cache)

; create new gigs in dialogflow
   ; (create-dialogflow-gig-entities! env conn etc)

                                        ; update dialogflow with the names of the gigs in case the names changed
    ;(update-dialogflow-gig-entities! env conn etc)

                                        ; delete the dialogflow entities for gigs in the past
   ; (prune-gig-entities-before! env conn etc (t/at (t/yesterday) (t/midnight)))
    ))

(defn make-gigs-sync-job [{:keys [conn gigo df-clients env]}]
  (tap> "MAKE GIGS")
  (tap> gigo)
  (fn [{:job/keys [frequency initial-delay]}]
    (tap> "register gigo cache update job")
    (assert (some? gigo) "gigo creds required")
    (jobs/make-repeating-job (partial #'sync-gigs env conn gigo (:entity-types-client df-clients)) frequency initial-delay)))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (def _opts {:env        (:app.ig/env state/system)
                :gigo       (:app.ig/gigo-client state/system)
                :df-clients (:app.ig/dialogflow-session state/system)
                :conn       (:ol.datahike.ig/connection state/system)}))
  (gigo/update-cache! (:gigo _opts))

  (update-gigs-db! (:conn _opts) @gigo/gigs-cache)

  (def result (update-dialogflow-gig-entities! (-> _opts :env) (-> _opts :conn) (-> _opts :df-clients :entity-types-client)))
  (future-done? result)
  @result
  (t/day-of-week (t/date (t/inst)))
                                        ;(update-dialogflow-gig-entities! (-> _opts :env) (-> _opts :conn) (-> _opts :df-clients :entity-types-client))
  (prune-gig-entities-before! (:env _opts) (:conn _opts) (-> _opts :df-clients :entity-types-client) (t/at (t/new-date 2022 8 1) (t/midnight)))
  (create-dialogflow-gig-entities! (:env _opts) (:conn _opts) (-> _opts :df-clients :entity-types-client))
                                        ;
  )
