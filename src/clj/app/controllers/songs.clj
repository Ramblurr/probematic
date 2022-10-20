(ns app.controllers.songs
  (:require
   [ctmx.form :as form]
   [app.db :as db]))

(defn get-conn [req]
  (-> req :system :conn))

(defn unwrap-params
  ([req] (-> req :params form/json-params-pruned))
  ([req name]
   (-> req :params form/json-params-pruned name)))

(defn create-song! [req]
  (let [song (-> req
                 :params form/json-params-pruned
                 :song)
        conn (-> req :system :conn)
        result (db/create-song! conn song)
        error (-> result :error)]
    (if  (= error :transact/unique)
      {:error
       :already-exists}
      {:song result})))

(defn log-play! [req]
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
