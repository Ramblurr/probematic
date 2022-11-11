(ns app.controllers.common
  (:require
   [app.db :as db]
   [app.schemas :as s]
   [ctmx.form :as form]
   [app.util :as util]
   [medley.core :as m]
   [clojure.string :as str]))

(defn get-conn [req]
  (-> req :system :conn))

(defn unwrap-params
  ([req] (-> req :form-params form/json-params-pruned))
  ([req name]
   (-> req :form-params form/json-params-pruned name)))

(defn path-param [req k]
  (-> req :path-params k))

(defn no-blanks [m]
  (m/map-vals #(if (and (string? %) (str/blank? %)) nil %) m))

(defn ns-qualify-key
  [m ns]
  (m/map-keys #(keyword  (name ns) (name %)) m))

(def excluded-request-keys
  [:datomic-conn
   :db
   :system
   :reitit.core/match
   :muuntaja/response
   :reitit.core/router])

(defn remove-request-keys [req]
  (apply dissoc req excluded-request-keys))

(defn throw-validation-error [msg req schema value]
  (ex-info msg {:req (remove-request-keys req)
                :schema schema
                :value value
                :explain (s/explain-human schema value)}))

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
