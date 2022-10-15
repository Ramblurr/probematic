(ns app.controllers.songs
  (:require
   [ctmx.form :as form]
   [app.db :as db]))

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
