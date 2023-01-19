(ns container
  (:require
   [babashka.process :refer [sh]]
   [babashka.tasks :refer [shell]]
   [clojure.string :as str]))

(defn exec [cmd]
  (str/trim (:out (sh cmd))))

(def git-hash (exec "git rev-parse --short HEAD"))
(def git-branch (exec "git rev-parse --abbrev-ref HEAD"))
(def build-date (exec "date -u +%Y%m%dT%H%M%S"))
(def image (str "ghcr.io/ramblurr/probematic"))
(def tag (str git-branch "-" git-hash))

(defn build [_]
  (shell (format "podman build -f ./docker/Dockerfile -t %s:latest-local --build-arg GIT_HASH=%s --build-arg BUILD_DATE=%s ."
                 image git-hash  build-date))
  (shell (format "podman tag %s:latest-local %s:%s" image image tag)))

(defn publish [_]
  (shell (format "podman push %s:%s" image tag)))

(defn publish-latest [_]
  (prn (format "Pushing %s:%s" image tag))
  (shell (format "podman push %s:%s" image tag))
  (shell (format "podman tag %s:%s %s:latest"  image tag image))
  (prn (format "Pushing %s:latest" image))
  (shell (format "podman push %s:latest" image)))
