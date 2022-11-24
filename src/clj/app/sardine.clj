(ns app.sardine
  (:import [com.github.sardine Sardine SardineFactory DavResource]
           [com.github.sardine.impl SardineImpl]
           [java.net URLEncoder URLDecoder]
           [org.apache.http.client.utils URIBuilder])
  (:require [app.file-utils :as fu] [clojure.string :as str]))

(defn build-full-path [{:keys [webdav-base-path]} remote-path]
  (fu/path-join webdav-base-path (fu/strip-leading-slash remote-path)))

(defn build-uri [{:keys [host]} full-path]
  (-> (URIBuilder.)
      (.setScheme "https")
      (.setHost host)
      (.setPort 443)
      (.setPath full-path)
      (.toString)))

(defn build-sardine [token]
  (SardineImpl. token))

(defn build-config [{:keys [host webdav-base-path token]}]
  (assert (str/ends-with? webdav-base-path "/") "webdav base path must start and end with a slash")
  (assert (str/starts-with? webdav-base-path "/") "webdav base path must start and end with a slash")
  (assert (not (str/blank? host)) "Webdav host cannot be empty")
  (assert (not (str/blank? token)) "Webdav bearer token cannot be empty")
  {:token token :host host :webdav-base-path webdav-base-path
   :client (build-sardine token)})

(defn shutdown [^SardineImpl sardine]
  (.shutdown sardine))

(defn ->dav-resource [{:keys [webdav-base-path]} ^DavResource r]
  {:full-path (.getPath r)
   :path (subs (.getPath r) (count webdav-base-path))
   :creation-date (.getCreation r)
   :modified-date (.getModified r)
   :content-type (.getContentType r)
   :content-length (.getContentLength r)
   :display-name (.getDisplayName r)
   :resource-types (.getResourceTypes r)
   :directory? (.isDirectory r)
   :file? (not (.isDirectory r))
   :uri (.getHref r)
   :name (.getName r)})

(def excluded-folder-patterns
  [#".DAV"])

(defn list-directory
  ([webdav-config remote-path]
   (list-directory webdav-config remote-path 1))
  ([{:keys [client] :as webdav-config} remote-path depth]
   (let [full-path (build-full-path webdav-config remote-path)
         result (-> client (.list (build-uri webdav-config full-path) depth))]
     (->> (for [r result]
            (->dav-resource webdav-config r))
          (remove (fn [{:keys [path]}] (some #(re-find % path) excluded-folder-patterns)))
          ;; the directory we listed is included in the results, so lets remove it
          (remove #(= (fu/add-trailing-slash full-path) (get % :full-path)))
          (sort-by (juxt :file? :name))))))

(defn stream-file [{:keys [client] :as webdav-config} remote-path]
  (let [path (build-uri webdav-config remote-path)]
    (-> client (.get path))))

(comment

  (do
    (require '[integrant.repl.state :as state])
    (def webdav-config (-> state/system :app.ig/webdav-sardine))) ;; rcf

  (build-full-path webdav-config "/Noten - Scores/aktuelle Stücke/Kingdom Come")
  (list-directory webdav-config "/Noten - Scores/aktuelle Stücke")

  (list-directory webdav-config "/Noten - Scores")
  (map (juxt  :content-type :name)
       (list-directory webdav-config "/Audio"))
  ;;
  )
