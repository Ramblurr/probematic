(ns app.keycloak
  (:require
   [app.datomic :as d]
   [app.queries :as q]
   [app.util :as util]
   [clojure.string :as str]
   [datomic.client.api :as datomic]
   [keycloak.admin :as admin]
   [keycloak.deployment :as keycloak]
   [keycloak.user :as user]
   [medley.core :as m])
  (:import
   (org.keycloak.representations.idm UserRepresentation)))

(defn create-client [sys]
  (let [kc-env (-> sys :env :keycloak)]
    (-> (keycloak/client-conf (dissoc kc-env :client-secret))
        (keycloak/keycloak-client (:client-secret kc-env)))))

(defn close-client! [client]
  (when client
    (.close client)))

(defn init! [sys]
  {:client (create-client sys)
   :realm (-> sys :env :keycloak :realm)})

(defn halt! [sys]
  (close-client! (:client sys)))

(defn client [{:keys [client]}]
  client)

(defn realm [{:keys [realm]}]
  realm)

(defn user-representation-> [^UserRepresentation u]
  {:user/user-id (.getId u)
   :user/username (.getUsername u)
   :user/email (.getEmail u)
   :user/first-name (.getFirstName u)
   :user/last-name (.getLastName u)
   :user/enabled? (.isEnabled u)})

(defn list-users [kc]
  (->> (admin/list-users (client kc) (realm kc))
       (map user-representation->)))

(defn match-members [members kc]
  (let [users (list-users kc)
        all (for [{:member/keys [email username gigo-key] :as m} members]
              (if-let [matched-user (m/find-first #(= email  (:user/email %)) users)]
                (-> m
                    (assoc :member/keycloak-id  (:user/user-id matched-user))
                    (assoc :member/username  (:user/username matched-user)))
                m))

        matched (remove #(nil? (:member/keycloak-id %)) all)
        unmatched (filter #(nil? (:member/keycloak-id %)) all)]
    {:matched matched
     :unmatched unmatched}))

(defn match-txs [matches]
  (mapcat (fn [m]
            [[:db/add [:member/gigo-key (:member/gigo-key m)] :member/keycloak-id (:member/keycloak-id m)]
             [:db/add [:member/gigo-key (:member/gigo-key m)] :member/username (:member/username m)]])
          matches))

(defn find-members-matches [{:keys [db kc]}]
  (->
   (->> (d/find-all db :member/gigo-key q/member-pattern)
        (mapv #(first %)))
   (match-members  kc)
   :matched
   match-txs))

(defn match-members-to-keycloak!
  "Attaches :member/keycloak-id to all members whose email address matches exactly to a keycloak user.
  Will also update the :member/username field for matched"
  [{:keys [datomic-conn] :as sys}]
  (datomic/transact datomic-conn {:tx-data (find-members-matches sys)}))

(defn link-user-edit
  "Return a string containing the URL to edit a user in the keycloak admin console."
  [env keycloak-id]
  (let [server-url (-> env :keycloak :auth-server-url)
        realm (-> env :keycloak :realm)]
    (str server-url "/admin/master/console/#/" realm "/users/" keycloak-id "/setttings")))

(defn get-user! [kc keycloak-id]
  (-> (admin/get-user (client kc) (realm kc) keycloak-id)
      (user-representation->)))

(defn update-user-meta! [kc {:member/keys [username email active? name keycloak-id] :as member}]
  (tap> {:updating-member member})
  (assert (not (str/blank? email)))
  (assert (not (str/blank? username)))
  (assert (not (str/blank? name)))
  (assert (not (str/blank? keycloak-id)))
  (-> (client kc)
      (.realm (realm kc))
      (.users)
      (.get (:member/keycloak-id member))
      (.update (user/user-for-update
                (util/remove-nils
                 {:username username
                  :email email
                  :enabled active?
                  :first-name name}))))
  (get-user! kc keycloak-id))

(defn kc-from-req [req]
  (-> req :system :keycloak))

(comment

  (do
    (require '[integrant.repl.state :as state])
    (require '[datomic.client.api :as datomic])
    (require '[keycloak.admin :as admin])
    (def env (-> state/system :app.ig/env))
    (def kc (-> state/system :app.ig/keycloak))
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn)))
  ;; rcf

  (find-members-matches {:db db :kc kc})
  (match-members-to-keycloak! {:db db :kc kc :datomic-conn conn})

  (get-user! kc "bcaa73f1-e080-420f-ac15-27882dbcb330")
  (get-user! kc "db15536d-9708-42fb-a1e5-61ddf6d7c190")
  (update-user-meta! kc
                     {:member/username "testusertest"
                      :member/keycloak-id  "bcaa73f1-e080-420f-ac15-27882dbcb330"
                      :member/email "testuser+test@example.com"
                      :member/active? true
                      :member/name "Testuser Testing"})

  ;; rcf

  ;;
  )
