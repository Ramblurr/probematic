(ns app.jobs.gig-events
  (:require
   [app.discourse :as discourse]
   [app.email :as email]
   [chime.core :as chime]
   [clojure.data]
   [app.routes.errors :as errors]
   [datomic.client.api :as datomic])
  (:import [java.time Instant]))

(defn update-system [{:keys [system]}]
  (assoc system :db (datomic/db (-> system :datomic :conn))))

(defn handle-gig-details-edited [req notify? gig-id {:keys [gig-before gig db-after]}]
  (let [new-system (update-system req)]
    (when notify?
      (email/send-gig-updated! req gig-id
                               (keys (second (clojure.data/diff gig-before gig)))))
    (discourse/upsert-thread-for-gig! new-system gig-id)))

(defn handle-gig-edited [req gig-id edit-type]
  (let [new-system (update-system req)]
    (discourse/upsert-thread-for-gig! new-system gig-id)))

(defn handle-gig-created [req notify? gig-id]
  (let [new-system (update-system req)]
    (when notify?
      (email/send-gig-created! req gig-id))
    (discourse/upsert-thread-for-gig! new-system gig-id)))

(defn exec-later [fn-name & args]
  (chime/chime-at [(.plusSeconds (Instant/now) 1)]
                  (fn [_]
                    (try
                      (apply fn-name args)
                      (catch Throwable e
                        (tap> e)
                        (errors/report-error! e))))))

(defn trigger-gig-details-edited [req notify? transact-result]
  (exec-later handle-gig-details-edited req notify? (-> transact-result :gig :gig/gig-id) transact-result))

(defn trigger-gig-edited [req gig-id edit-type]
  (tap> {:edit-type edit-type :gig-id gig-id})
  (exec-later handle-gig-edited req gig-id edit-type))

(defn trigger-gig-created [req notify? gig-id]
  (exec-later handle-gig-created req notify? gig-id))