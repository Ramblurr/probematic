(ns app.controllers.common
  (:require
   [ctmx.form :as form]))

(defn get-conn [req]
  (-> req :system :conn))

(defn unwrap-params
  ([req] (-> req :params form/json-params-pruned))
  ([req name]
   (-> req :params form/json-params-pruned name)))

(defn unwrap-params2
  ([req] (-> req :params form/json-params))
  ([req name]
   (-> req :params form/json-params-pruned name)))
