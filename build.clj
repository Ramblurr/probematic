(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(def lib 'sno/probematic)
;; if you want a version of MAJOR.MINOR.COMMITS:
(def version (format "%s" (b/git-process {:git-args "rev-parse --short HEAD"})))
(def main 'app.main)

(defn tests "Run tests" [opts]
  (-> opts
      (assoc :lib lib :version version :main main)
      ;(bb/run-task [:eastwood])
      (bb/run-tests)))

(defn clean "Clean build dir" [opts]
  (-> opts
      (assoc :lib lib :version version :main main)
      (bb/clean)))

(defn uberjar "build the uberjar" [opts]
  (-> opts
      (assoc :lib lib :version version :main main)
      (bb/uber)))

(defn ci "Run tests and build the jar" [opts]
  (-> opts
      (assoc :lib lib :version version :main main)
      ;(bb/run-task [:eastwood])
      (bb/clean)
      (bb/run-tests)
      (bb/uber)))
