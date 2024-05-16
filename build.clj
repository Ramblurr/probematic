(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'sno/probematic)
(def version (format "%s" (b/git-process {:git-args "rev-parse --short HEAD"})))
(def main 'app.main)
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn" :user :standard}))

(defn- uber-opts [opts]
  (assoc opts
         :lib lib
         :main main
         :version version
         :uber-file (format "target/%s-%s.jar" lib version)
         :basis basis
         :class-dir class-dir
         :src-dirs ["src"]
         :ns-compile [main]))

;; (defn tests "Run tests" [opts]
;;   (-> opts
;;       (assoc :lib lib :version version :main main)
;;       ;(bb/run-task [:eastwood])
;;       (bb/run-tests)))

;; (defn clean "Clean build dir" [opts]
;;   (-> opts
;;       (assoc :lib lib :version version :main main)
;;       (bb/clean)))

(defn clean [_]
  (b/delete {:path "target"}))

(defn compile-java-dev [_]
  (b/javac {:src-dirs ["src/java"]
            :class-dir "src/java-classes"
            :basis basis
            :javac-opts ["-source" "17" "-target" "17"]}))

(defn compile-java-prod [_]
  (b/javac {:src-dirs ["src/java"]
            :class-dir class-dir
            :basis basis
            :javac-opts ["-source" "17" "-target" "17"]}))

(defn uberjar "build the uberjar" [_]
  (let [opts (uber-opts {})]
    (clean nil)
    (compile-java-prod nil)
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version version
                  :basis basis
                  :src-dirs ["src"]})
    (b/copy-dir {:src-dirs ["src" "resources"]
                 :target-dir class-dir})
    (b/compile-clj {:basis basis
                    :src-dirs ["src"]
                    :class-dir class-dir})
    (b/uber opts)))

;; (defn ci "Run tests and build the jar" [opts]
;;   (-> opts
;;       (assoc :lib lib :version version :main main)
;;       ;(bb/run-task [:eastwood])
;;       (bb/clean)
;;       (bb/run-tests)
;;       (bb/uber)))
