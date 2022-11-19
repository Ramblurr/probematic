(ns app.controllers.common
  (:require
   [ctmx.form :as form]
   [medley.core :as m]
   [clojure.string :as str]))

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

;;; TODO refactor usages of this fn to util/ensure-uuid
(defn ensure-uuid
  "DEPRECATED. use util/ensure-uuid"
  [v]
  (if (string? v)
    (parse-uuid v)
    v))

(defn check->bool [v]
  (= "on" v))
