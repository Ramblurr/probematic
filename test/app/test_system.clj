(ns app.test-system
  (:require
   [migratus.core :as migratus]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [next.jdbc :as jdbc]
   [integrant.core :as ig]
   [ol.system])
  (:import
   java.util.regex.Pattern))

(def ^:dynamic *tx* nil)
(def ^:dynamic *system* nil)

(defmacro ^:private with-system
  [system ks & body]
  `(let [system# ~system
         s#      (ig/init system# (or ~ks (keys system#)))]
     (try
       (binding [*system* s#]
         ~@body)
       (finally
         (ig/halt! s#)))))

(defn- default-system
  []
  (ol.system/system-config
   {:profile :test}))

(defn with-system-fixture
  ([]
   (with-system-fixture default-system))
  ([system]
   (fn [f]
     (with-system (system) nil
       (f)))))

(defn with-subsystem-fixture
  ([ks]
   (with-subsystem-fixture default-system ks))
  ([system ks]
   (fn [f]
     (with-system (system) ks
       (f)))))

(defn with-rollback-fixture
  "A test fixture that runs the test fn in a transaction that is always rolled back.
  Not suitable when the code under test itself uses a transaction."
  []
  (fn [f]
    (jdbc/with-transaction [txn
                            (:ol.hikari-cp.ig/hikari-connection *system*)
                            {:rollback-only true}]
      (binding [*tx* txn] (f)))))

(def ^Pattern sep (Pattern/compile "^.*--;;.*\r?\n" Pattern/MULTILINE))
(def ^Pattern sql-comment (Pattern/compile "^--.*" Pattern/MULTILINE))
(def ^Pattern empty-line (Pattern/compile "^[ ]+" Pattern/MULTILINE))

(defn sanitize [command]
  (-> command
      (str/replace   sql-comment "")
      (str/replace empty-line "")))

(defn split-commands [commands]
  (->> (.split sep commands)
       (map #(sanitize %))
       (remove empty?)
       (not-empty)))

(defn- read-resource [name]
  (slurp (io/resource name)))

(defn- load-sql-file [file-name]
  (split-commands
   (read-resource  file-name)))

(defn- load-sql [fns]
  (reduce (fn [box [key value]]
            (assoc box key (load-sql-file value)))
          {}
          fns))

(def sql-files
  {:drop-all-tables "drop-all-tables.sql"})

(def sql (load-sql sql-files))

(defn system []
  {:ds (:ol.hikari-cp.ig/hikari-connection *system*)})

(defn drop-all-tables [ds]
  (doseq [c (get sql :drop-all-tables)]
    ; (tap> c)
    (jdbc/execute-one! ds [c])))

(defn with-clean-database-fixture
  "A test fixture that applies database migrations, then runs the test code, then drops the database.
  Slow, but an alternative to the rollback fixture when the code under test uses transactionl"
  []
  (fn [f]
    (assert *system* "with-clean-database-fixture must be called inside with-system-fixture")
    (assert (:ol.hikari-cp.ig/hikari-connection *system*) "with-clean-database-fixture must be called with the hikari connection")
    (assert (:ol.sql.migratus.ig/migratus *system*))
    (try
      (let [migratus (:ol.sql.migratus.ig/migratus *system*)]
        ; (migratus/init migratus)
        (migratus/migrate migratus)
        (f))
      (finally
        (drop-all-tables (:ol.hikari-cp.ig/hikari-connection *system*))))))
