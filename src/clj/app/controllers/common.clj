(ns app.controllers.common
  (:require
   [app.db :as db]

   [ctmx.form :as form]))

(defn get-conn [req]
  (-> req :system :conn))

(defn unwrap-params
  ([req] (-> req :form-params form/json-params-pruned))
  ([req name]
   (-> req :form-params form/json-params-pruned name)))

(defn unwrap-params2
  ([req] (-> req :params form/json-params))
  ([req name]
   (-> req :params form/json-params-pruned name)))

(defn save-log-play! [conn gig-id song-title rating emphasis]
  (let [gig (db/gig-by-id @conn gig-id)
        song (db/song-by-title @conn song-title)
        result (db/create-play! conn gig song rating emphasis)
        error (-> result :error)]
    (if error
      result
      {:play result})))

(defn ensure-uuid [v]
  (if (string? v)
    (parse-uuid v)
    v))

(defn check->bool [v]
  (= "on" v))
