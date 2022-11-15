(ns app.discourse
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [integrant.repl.state :as state]
            [jsonista.core :as j]
            [martian.core :as martian]
            [martian.httpkit :as martian-http]))

(defn add-authentication-header [api-key username]
  {:name ::add-authentication-header
   :enter (fn [ctx]
            (-> ctx
                (assoc-in [:request :headers "Api-Key"] api-key)
                (assoc-in [:request :headers "Api-Username"] username)))})

(def url-discourse-open-api "https://docs.discourse.org/openapi.json")

(defn list-users [m]
  (let [{:keys [status body] :as r}
        @(martian/response-for m :admin-list-users {:flag "active" :show_emails true})]
    (if (= 200 status)
      (j/read-value body j/keyword-keys-object-mapper)
      {:error r})))

(comment
  (martian/explore m)
  (martian/explore m :admin-list-users)

  (do
    (require '[integrant.repl.state :as state])
    (require  '[datomic.client.api :as datomic])
    (require '[app.datomic :as d])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn))
    (def api-key (-> state/system :app.ig/env :discourse :api-key))
    (def username (-> state/system :app.ig/env :discourse :username))) ;; rcf

  (do

    (def m (martian-http/bootstrap-openapi url-discourse-open-api {:server-url "https://forum.streetnoise.at"
                                                                   :interceptors (concat martian/default-interceptors
                                                                                         [(add-authentication-header api-key username)]
                                                                                         [martian-http/perform-request])}))

    (def user-list
      (->>
       (list-users m)))

    (def members
      (->>
       (d/find-all db :member/gigo-key [:member/name :member/email :member/gigo-key])
       (map first)
       (map #(update % :member/email str/lower-case))))

    (def joined (set/join user-list members {:email :member/email}))
    (count user-list)
    (count members)
    (count joined)

    (def txs
      (->> joined
           (map #(select-keys % [:member/gigo-key :avatar_template :id :username]))
           (map #(update % :id str))
           (map #(set/rename-keys % {:avatar_template :member/avatar-template :id :member/discourse-id :username :member/nick}))))

    (d/transact conn {:tx-data txs})) ;; add avatar txs

  (map :email user-list)
  (map :member/email members)

  (d/find-all db :member/gigo-key [:member/name :member/email :member/gigo-key :member/avatar-template :member/discourse-id :member/nick])

  ;;
  )
