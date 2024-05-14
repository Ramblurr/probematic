(ns app.file-utils
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str])
  (:import
   [java.io File]
   [java.nio.file Path Paths]
   [java.nio.file Files]))

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

(defn dirname
  "Given a path /foo/bar/baz returns /foo"
  ^String [^String path]
  (.getParent (file path)))

(defn validate-base-path
  "Helper to prevent path traversal attacks. If full-path is not contained inside base-path, will return false, otherwise true"
  [base-path full-path]
  (str/starts-with? (-> (file full-path) (.getCanonicalPath))
                    base-path))

(defn validate-base-path!
  "Helper to prevent path traversal attacks. If full-path is not contained inside base-path, will throw an exception"
  [base-path full-path]
  (when-not (validate-base-path base-path full-path)
    (throw (ex-info "Path traversal attack detected" {:base-path base-path
                                                      :full-path full-path}))))

(defn create-tempfile
  "Low level tempfile create function. Use tempfile instead."
  [& {:keys [prefix suffix]}]
  (File/createTempFile prefix suffix))

(defn delete-on-exit!
  [path]
  (.deleteOnExit ^File (file path)))

(defn tempfile
  "Create a temporary file."
  [& {:keys [suffix prefix]
      :or {prefix "probematic."
           suffix ".tmp"}}]
  (let [path (create-tempfile
              :suffix suffix
              :prefix prefix)]
    (delete-on-exit! path)
    path))

(defn to-path [v]
  (cond
    (string? v) (Paths/get v (into-array String []))
    (instance? java.nio.file.Path v) v
    (instance? java.io.File v) (.toPath v)
    :else (throw (ex-info "Unsupported type" {:type (type v)}))))

(defn size
  "Return the file size."
  [path]
  (-> path (to-path) (Files/size)))

(defn split-ext
  "Returns a vector of `[^String name ^String extension]`."
  [path]
  (let [^Path path (to-path path)
        ^String path-str (.toString path)
        i (.lastIndexOf path-str ".")]
    (if (pos? i)
      [(subs path-str 0 i)
       (subs path-str i)]
      [path-str nil])))

(defn ext
  "Return the extension part of a file."
  [path]
  (some-> (last (split-ext path))
          (subs 1)))

(defn base
  "Return the base part of a file (without the extension)"
  [path]
  (first (split-ext path)))

(defn delete-if-exists [f]
  (Files/deleteIfExists (to-path f)))

(defn exists?
  "Checks for the existence of file/directory.  File can be a path or a File object."
  [file] (.exists (io/file file)))

(defn writeable?
  "Checks if the file is writeable.  File can be a path or a File object."
  [file]
  (.canWrite (io/file file)))

(defn program-exists?
  "Checks if the program exists in the PATH."
  [program]
  (let [cmd "which"]
    (try
      (= 0 (:exit (sh cmd program)))
      (catch Exception e ;in the unlikely event where which is unavailable
        (throw (ex-info (format "Unable to determine whether '%s' exists. Notifications may not work." program) {}))))))

(comment
  (= true (validate-base-path "/" "/foo/bar"))
  (= true (validate-base-path "/foo/bar" "/foo/bar"))
  (= false (validate-base-path "/foo/bar/baz" "/foo/bar"))
  (ext "foobar")

  ;;
  )
