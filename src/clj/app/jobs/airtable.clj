(ns app.jobs.airtable
  (:require
   [ol.jobs-util :as jobs]
   [app.airtable :as airtable]
   [app.gigo :as gigo]
   [app.features :as f]))

;; This job updates the Gigo table in airtable by fetching all members from gigo itself

(defn gigo-record-by-key [records k]
  (first (filter (fn [m]
                   (= k (get-in m ["fields" "Member Key"])))
                 (get records "records" []))))

(defn match-gigo-to-airtable
  "Given the Gigo airtable records, and a map of gigo-name -> gigo-member-key, returns a map of gigo-name to gigo airtable record"
  [gigo-records names->keys]
  (mapv
   (fn [[gigo-name member-key]]
     {gigo-name
      (gigo-record-by-key gigo-records member-key)})
   names->keys))

(defn- prepare-gigo-table-update
  "Given the current airtable Gigo records, and the attendance from a gig, this function
  will return the payload to create the missing Gigo records in airtable."
  [records attendance]
  (let [names->keys (gigo/members-from-attendance attendance)
        matches (match-gigo-to-airtable records names->keys)
        nil-matches (filter (fn [k] (-> k vals first nil?)) matches)]
    (->> nil-matches
         (map keys)
         (mapv (fn [n]
                 {"Member Key"  (get names->keys (first n))
                  "Member Name" (first n)}))
         (partition 10 10 nil))))

(defn update-gigo-table!
  "Update the Gigo table in airtable with member info from gig-o-matic"
  [gigo-config airtable-config]
  (let [records (airtable/list-records! airtable-config (:gigo-table airtable-config))
        ; using next gig here as an easy way to get the member names + member keys
        next-gig (gigo/get-next-gig! gigo-config)
        attendance (:attendance next-gig)
        airtable-records (when attendance
                           (prepare-gigo-table-update records attendance))]
    (when airtable-records
      (run!
       #(airtable/create-records! airtable-config (:gigo-table airtable-config) %)
       airtable-records))))

(defn- airtable-job [gigo-config airtable-config _]
  (when (f/feature? :feat/sync-airtable)
    (update-gigo-table! gigo-config airtable-config)))

(defn make-airtable-job [{:keys [env gigo]}]
  (fn [{:job/keys [frequency initial-delay]}]
    (jobs/make-repeating-job (partial #'airtable-job gigo (:airtable env)) frequency initial-delay)))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (def _opts {:env  (:sno.gigo-sms.ig/env state/system)
                :gigo (:sno.gigo-sms.ig/gigo-client state/system)}))
  (update-gigo-table! (:gigo _opts) (-> _opts :env :airtable))

  ;
  )
