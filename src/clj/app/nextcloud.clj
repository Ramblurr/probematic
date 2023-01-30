(ns app.nextcloud
  (:require
   [app.sardine :as sardine]
   [reitit.coercion.malli :as rcm]
   [jsonista.core :as j]
   [org.httpkit.client :as client]))

(def base-url "https://data.streetnoise.at/ocs/v1.php/cloud")

(defn add-url [req]
  (update-in req [:url] (fn [url] (str base-url url))))

(defn add-auth [req config]
  (assoc-in req [:basic-auth] [(:username config) (:password config)]))

(defn add-ocs [req]
  (assoc-in req [:headers "OCS-APIRequest"] "true"))

(defn request [req config]
  (-> req
      (add-auth config)
      add-url
      add-ocs
      client/request))

(defn parse-body
  "Parse json in http response body"
  [response]
  (try
    (if-let [json-body (some-> response :body (j/read-value j/keyword-keys-object-mapper))]
      (assoc response :body json-body)
      response)
    (catch Exception e
      response)))

(defn get-user [config user-id]
  (let [resp @(request {:method  :get
                        :url     (str "/users/" user-id)
                        :headers {"accept" "application/json"}} config)]
    (->  resp parse-body :body :ocs :data)))

(defn list-users [config]
  (let [resp @(request {:method  :get
                        :url     (str "/users")
                        :headers {"accept" "application/json"}} config)]
    (->  resp parse-body :body :ocs :data)))

(defn routes []
  ["/nextcloud-fetch" {:parameters {:query [:map [:path string?]]}
                       :coercion rcm/coercion
                       :app.route/name :app/nextcloud-fetch
                       :handler (fn [req] (sardine/fetch-file-handler req false))}])

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require  '[datomic.client.api :as datomic])
    (require '[app.datomic :as d])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn))
    (def password (-> state/system :app.ig/env :nextcloud :password))
    (def username (-> state/system :app.ig/env :nextcloud :username))
    (def config {:username username :password password})) ;; rcf

  (list-users config)

  (get-user config "casey")

  ;;
  )
