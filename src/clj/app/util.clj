(ns app.util
  (:refer-clojure :exclude [hash])
  (:require
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [ctmx.form :as form]
   [medley.core :as m]
   [tick.core :as t])
  (:import
   (java.net URLDecoder URLEncoder)
   (java.security SecureRandom)))

(defn url-encode [v]
  (URLEncoder/encode v "UTF-8"))

(defn url-decode [v]
  (URLDecoder/decode v "UTF-8"))

(defn random-bytes [size]
  (let [seed (byte-array size)]
    (.nextBytes (SecureRandom.) seed)
    seed))

(defn clean-number [n]
  (str/replace n #"[^+\d]" ""))

(defn no-blanks [s]
  (if
   (and (string? s) (str/blank? s))
    nil
    s))

(defn namespace-keys
  "Returns a function that takes a map and returns a map with all the keys renamed to namespace.

  ((namespace-keys \"foo\") {:a 1 :b 2}) => {:foo/a 1 :foo/b 2}
  "
  [namespace]
  (fn [m]
    (m/map-keys (fn [k]
                  (keyword namespace (name k))) m)))

(defn xxx>>
  ([msg x]
   (tap> msg)
   (tap> x)
   x)
  ([x]
   (tap> x)
   x))
(defn xxx
  ([x msg]
   (tap> msg)
   (tap> x)
   x)
  ([x]
   (tap> x)
   x))

(defn isort-by
  "Like sort-by but for a case-insenstive string sort" [keyfn coll]
  (sort-by
   (comp clojure.string/lower-case keyfn) coll))

(defn unwrap-params
  [req] (-> req :params form/json-params-pruned))

(defn json-params
  [req] (-> req :params form/json-params))

(defn remove-nils
  "Returns the map less any keys that have nil values"
  [m]
  (cond (map? m)
        (into {} (filter #(not (nil? (val %))) m))
        (list? m)
        (remove #(nil? %) m)
        (vector? m)
        (filterv #(some? %) m)
        (sequential? m)
        (remove #(nil? %) m)
        :else
        (throw "remove-nils: Not implemented")))

(defn blank->nil
  "If arg is an empty string, returns nil. Otherwise returns arg."
  [arg]
  (if (and (string? arg) (str/blank? arg))
    nil
    arg))

(defn remove-empty-strings
  "Returns the map less any keys that have empty-strings as values values"
  [m]
  (into {} (filter (fn [e]
                     (if (string? (val e))
                       (not (str/blank? (val e)))
                       true))
                   m)))

(defn qp-bool
  "Parse the query parameter specified by k as a boolean"
  [req k]
  (Boolean/valueOf (-> req :query-params k)))

(defn post? [{:keys [request-method]}]
  (= :post request-method))

(defn put? [{:keys [request-method]}]
  (= :put request-method))

(defn delete? [{:keys [request-method]}]
  (= :delete request-method))

(defn make-get-request
  "Returns a new GET request based on passed in request.
    See: https://github.com/spookylukey/django-htmx-patterns/blob/master/view_restart.rst"
  ([old-req]
   (-> old-req
       (assoc :request-method :get)))
  ([old-req extra]
   (-> (merge old-req extra)
       (assoc :request-method :get))))

(defn comp-namer [var]
  (fn
    ([]
     (-> var meta :name str))
    ([s]
     (str (-> var meta :name) s))))

(defn comp-name [var]
  ((comp-namer var)))

(defn hash [kw]
  (str "#" (name kw)))

(defn id [kw]
  (name kw))

(defmacro endpoint-path
  [f]
  `(-> ~f var meta :name str))

(defn kw->str [kw]
  (when kw
    (str (namespace kw)
         "/"
         (name kw))))

(defn remove-deep
  "Walk data and in every ecountered map, dissoc all key/values in key-set"
  [key-set data]
  (walk/prewalk (fn [node] (if (map? node)
                             (apply dissoc node key-set)
                             node))
                data))

(defn replace-values [m key-set new-value]
  (m/map-kv (fn [k v]
              (if (contains? key-set k)
                [k new-value]
                [k v])) m))

(defn replace-deep
  "Walk data and in every ecountered map, replace the value of all keys in key-set with redaction-value"
  [key-set redaction-value data]
  (walk/prewalk (fn [node] (if (map? node)
                             (replace-values node key-set redaction-value)
                             node))
                data))
(comment
  (replace-values {:foo 1 :password "hunter2"} #{:password} "<REDACTED>")
  (replace-deep #{:password} "<REDACTED>" [{:user {:name "alice" :password "hunter2"}}])
  ;;
  )

(defn local-time-austria!
  "Returns a zoned date time for the local austrian time"
  [] (t/in (t/instant) "Europe/Vienna"))

(defn time-inside?
  "Returns true if   start <= reference < end"
  [start end reference]
  (and (t/>= reference start)
       (t/< reference end)))

(defn time-window [zdt]
  (let [time (t/time zdt)]
    (cond
      (time-inside? (t/time "00:00") (t/time "05:00") time)
      :nightowl

      (time-inside? (t/time "05:00") (t/time "12:00") time)
      :morning

      (time-inside? (t/time "12:00") (t/time "17:00") time)
      :afternoon

      (time-inside? (t/time "17:00") (t/time "23:59") time)
      :evening
      :else :wut?)))

(defn ensure-coll
  "If v is nil, returns nil. If v is a collection, returns a collection. Otherwise returns [v].
  Useful when you have a value that may be a collection or a single value, but you want to operate on a collection in any case.
  "
  [v]
  (when v
    (if (coll? v)
      v
      [v])))

(defn index-sort-by
  "Sorts the items in coll by k according to the order of values of k in manifest.
  Probably too slow on large collections."
  [manifest k coll]
  (sort-by (fn [item]
             (.indexOf manifest (get item k)))
           coll))

(defn ensure-uuid [v]
  (if (string? v)
    (parse-uuid v)
    v))

(defn ensure-uuid! [v]
  (parse-uuid v))

(defn log-pprint
  "A pretty-print function suitable for use with
  `clojure.tools.logging` functions."
  [& args]
  (->> args
       (apply pprint/pprint)
       (with-out-str)
       (str "\n")))

(comment
  (index-sort-by [3 2 1] :id [{:id 1} {:id 2} {:id 3}])

  ;;
  )
