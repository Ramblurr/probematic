(ns app.file-utils
  (:import [java.io File])
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]))

(defn-  file
  ^File [& args]
  (apply io/file args))

(defn  path-join
  "Joins paths together and returns the resulting path as a string. nil and empty strings are
   silently discarded. "
  ^String [& paths]
  (let [paths' (remove empty? paths)]
    (if (empty? paths')
      ""
      (str (apply file paths')))))

(defn strip-trailing-slash
  ^String [^String s]
  (if (and (str/ends-with? s "/") (> (count s) 0))
    (subs s 0 (dec (count s)))
    s))

(defn strip-leading-slash
  "Returns a new version of 'path' with the leading slash removed. "
  ^String [^String path]
  (when path (.replaceAll path "^/" "")))
(defn add-trailing-slash
  "Adds a trailing slash to 'input-string' if it doesn't already have one."
  ^String [^String input-string]
  (if-not (.endsWith input-string "/")
    (str input-string "/")
    input-string))

(defn component-paths
  "Given a string /Foo/Bar/Baz, returns a vector of strings: [/Foo /Foo/Bar /Foo/Bar/Baz]"
  [^String s]
  (assert (str/starts-with? s "/") "Path must be absolute")
  (assert (not (str/ends-with? s "/")) "Path must not end with slash")
  (let [sub (str/split s #"/")]
    (map (fn [i]
           (str "/"
                (str/join "/" (subvec sub 1 (inc i)))))
         (range 1 (inc (count (re-seq #"/" s)))))))

(defn normalize-path
  "Normalizes a file path on Unix systems by eliminating '.' and '..' from it."
  ^String [^String file-path]
  (loop [dest [] src (str/split file-path #"/")]
    (if (empty? src)
      (str/join "/" dest)
      (let [curr (first src)]
        (cond (= curr ".") (recur dest (rest src))
              (= curr "..") (recur (vec (butlast dest)) (rest src))
              :else (recur (conj dest curr) (rest src)))))))

(defn basename
  "Given a path /foo/bar/baz returns baz"
  ^String [^String path]
  (.getName (file path)))
