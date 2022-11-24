(ns app.sardine
  (:import [com.github.sardine DavResource]
           [com.github.sardine.impl SardineImpl SardineException]
           [java.net URLEncoder]
           [org.apache.http.client.utils URIBuilder])
  (:require
   [app.file-utils :as fu] [clojure.string :as string]
   [app.routes.errors :as errors]))

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
  (assert (string/ends-with? webdav-base-path "/") "webdav base path must start and end with a slash")
  (assert (string/starts-with? webdav-base-path "/") "webdav base path must start and end with a slash")
  (assert (not (string/blank? host)) "Webdav host cannot be empty")
  (assert (not (string/blank? token)) "Webdav bearer token cannot be empty")
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
  (let [uri (build-uri webdav-config (build-full-path webdav-config remote-path))]
    (-> client (.get uri))))

(defn content-disposition-filename [prefix name]
  (format "%s; filename*=UTF-8''%s" prefix (URLEncoder/encode name "UTF-8")))

(defn fetch-file-handler [req inline?]
  (let [path (-> req :params :path)]
    (try
      (let [{:keys [content-type content-length name] :as file-info} (list-directory (:webdav req) path)]
        {:status 200
         :headers {"Content-Disposition" (content-disposition-filename (if inline?
                                                                         "inline" "attachment")
                                                                       name)
                   "Content-Type" content-type
                   "Content-Length" (str content-length)}
         :body (stream-file (:webdav req) path)})
      (catch SardineException e
        (cond
          (string/includes? (ex-message e) "404")
          (errors/not-found-error req (ex-info "Nextcloud file not found" {:file-path path} e))
          :else
          (errors/unknown-error req (ex-info "Error fetching nextcloud file" {:file-path path} e)))))))

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
