(ns app.util
  (:require
   [medley.core :as m]
   [ctmx.form :as form]
   [clojure.string :as str]))

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

(defn remove-nils
  "Returns the map less any keys that have nil values"
  [m]
  (into {} (filter #(not (nil? (val %))) m)))
