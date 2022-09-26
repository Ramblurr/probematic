(ns app.airtable
  (:require
   [org.httpkit.client :as client]
   [jsonista.core :as j]
   [puget.printer :as puget]))

(def base-url "https://api.airtable.com/v0/")
(defn add-auth [req token]
  (assert token "airtable api access requires a token")
  (assoc-in req [:headers "Authorization"] (str "Bearer " token)))

(defn add-url [req]
  (update-in req [:url] (fn [url] (str base-url url))))

(defn request [req config]
  (-> req
      (add-auth (:token config))
      add-url
      client/request))

(defn list-records!
  ([config table]
   (list-records! config table {}))
  ([config table query-opts]
   (assert (:base config) "airtable - a base id is required")
   (let [resp @(request {:url (str (:base config) "/" (:name table))
                         :query-params
                         (assoc query-opts "view" (:view table))} config)]
     (j/read-value (:body resp)))))

(defn- prepare-records-to-create [records]
  {:records
   (mapv (fn [m] {} {:fields m}) records)})

(defn create-records! [config table records]
  (assert (>= 10 (count records)) "create-records can only create maximum 10 at a time.")
  (assert (:base config) "airtable - a base id is required")
  (let [resp @(request {:method  :post
                        :url     (str (:base config) "/" (:name table))
                        :headers {"content-type" "application/json"}
                        :body    (j/write-value-as-string (prepare-records-to-create records))} config)]
    resp))

(defn patch-records! [config table records]
  (assert (>= 10 (count records)) "create-records can only create maximum 10 at a time.")
  (assert (:base config) "airtable - a base id is required")
  (let [resp @(request {:method  :patch
                        :url     (str (:base config) "/" (:name table))
                        :headers {"content-type" "application/json"}
                        :body    (j/write-value-as-string {:records records})} config)]
    resp))

(defn person-by-username [records username]
  (first (filter (fn [m]
                   (= username (get-in m ["fields" "Name"])))
                 (get records "records" []))))

(defn gigo-record-by-key [records k]
  (first (filter (fn [m]
                   (= k (get-in m ["fields" "Member Key"])))
                 (get records "records" []))))
