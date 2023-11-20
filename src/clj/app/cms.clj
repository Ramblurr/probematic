(ns app.cms
  (:require
   [clojure.tools.logging :as log]
   [app.errors :as errors]
   [jsonista.core :as j]
   [org.httpkit.client :as client]
   [app.queries :as q]
   [datomic.client.api :as datomic]
   [tick.core :as t]))

(defn update-cms-req [cms-url token payload]
  {:method :post
   :url (str cms-url "/api/song/")
   :headers {"authorization" (str "Bearer " token)
             "content-type" "application/json"}
   :body (j/write-value-as-string payload)})

(defn song->wagtail [{:song/keys [title song-id composition-credits arrangement-credits active? origin lyrics arrangement-notes last-played-on]}]
  {:snorga_id song-id
   :title title
   :status (if active? "active" "retired")
   :arrangement_credits arrangement-credits
   :composition_credits composition-credits
   :lyrics lyrics
   :description origin
   :arrangement_notes arrangement-notes
   :last_played_date (when last-played-on (-> last-played-on t/date-time str))})

(defn sync-song! [{:keys [db] :as system} song-id]
  (try
    (let [{:keys [token cms-url]} (-> system :env :cms)
          song (q/retrieve-song db song-id)
          resp @(client/request (->> song
                                     song->wagtail
                                     (update-cms-req cms-url token)))]
      (log/info (str "synced with cms song-id=" song-id))
      (log/info resp)
      resp)
    (catch Exception e
      (errors/report-error! e))))

(defn sync-all-songs! [{:keys [datomic] :as system}]
  (try
    (let [conn (:conn datomic)
          {:keys [token cms-url]} (-> system :env :cms)
          songs (q/retrieve-all-songs (datomic/db conn) q/song-pattern-detail)
          requests (->> songs
                        (map song->wagtail)
                        (map (partial update-cms-req cms-url token)))
          resp (for [req requests]
                 (do
                   (Thread/sleep 200)
                   @(client/request req)))]
      resp)
    (catch Exception e
      (errors/report-error! e))))
