(ns app.util.http
  (:require
   [app.util :as util]
   [clojure.string :as str]
   [ctmx.form :as form]
   [medley.core :as m]))

(defn unwrap-params
  ([req] (-> req :form-params form/json-params-pruned))
  ([req name]
   (-> req :form-params form/json-params-pruned name)))

(defn path-param
  "Fetches the path param k"
  [req k]
  (-> req :path-params k))

(defn query-param
  "Fetches the query param k"
  [req k]
  (-> req :params k))

(defn query-param-uuid
  "Fetches the  param k coerced as a uuid"
  [req k]
  (util/ensure-uuid (query-param req k)))

(defn query-param-uuid!
  "Like query-param-uuid but throws if the param doesn't exist."
  [req k]
  (util/ensure-uuid! (query-param req k)))

(defn path-param-uuid
  "Fetches the path param k coerced as a uuid"
  [req k]
  (util/ensure-uuid (path-param req k)))

(defn path-param-uuid!
  "Like path-param-uuid but throws if the param doesn't exist."
  [req k]
  (util/ensure-uuid! (path-param req k)))

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
