(ns app.members.controller
  (:require
   [app.auth :as auth]
   [app.util.http :as common]
   [app.datomic :as d]
   [app.email :as email]
   [app.i18n :as i18n]
   [app.keycloak :as keycloak]
   [app.queries :as q]
   [app.routes.errors :as errors]
   [app.secret-box :as secret-box]
   [app.twilio :as twilio]
   [clojure.string :as str]
   [com.yetanalytics.squuid :as sq]
   [datomic.client.api :as datomic]
   [taoensso.carmine :as redis]
   [tick.core :as t]))

(defn sections [db]
  (->> (d/find-all db :section/name [:section/name])
       (mapv first)
       (sort-by :section/name)))

(defn members [db]
  (->> (d/find-all db :member/gigo-key q/member-pattern)
       (mapv #(first %))
       (sort-by (juxt :member/name :member/active))))

(defn keycloak-attrs-changed? [before-m after-m]
  (let [ks [:member/email :member/username :member/active? :member/name]]
    (not=
     (select-keys before-m ks)
     (select-keys after-m ks))))

(defn transact-member!
  [{:keys [datomic-conn system]} gigo-key tx-data]
  (let [tx-result (datomic/transact datomic-conn {:tx-data tx-data})
        before-member (q/retrieve-member (:db-before tx-result) gigo-key)
        after-member (q/retrieve-member (:db-after tx-result) gigo-key)]
    (when
     (and (keycloak-attrs-changed? before-member after-member) (:member/keycloak-id after-member))
      ;; TODO: rollback datomic tx if keycloak update fails
      (keycloak/update-user-meta! (:keycloak system) after-member))
    {:member after-member}))

(defn update-active-and-section! [{:keys [db] :as req}]
  (let [params (common/unwrap-params req)
        gigo-key (:gigo-key params)
        member (q/retrieve-member db gigo-key)
        member-ref (d/ref member :member/gigo-key)
        tx-data [[:db/add member-ref :member/section [:section/name (:section-name params)]]
                 [:db/add member-ref :member/active? (not (:member/active? member))]]]
    (transact-member! req gigo-key tx-data)))

(defn munge-unique-conflict-error [tr e]
  (let [field (cond
                (re-find #".*:member/phone.*" (ex-message e)) :error/member-unique-phone
                (re-find #".*:member/username.*" (ex-message e))  :error/member-unique-username
                (re-find #".*:member/email.*" (ex-message e))   :error/member-unique-email
                :else nil)]
    (ex-info "Validation error" {:validation/error (if field (tr [field]) (ex-message e))})))

;;  derived from https://github.com/nextcloud/server/blob/cbcf072b23970790065e0df4a43492e1affa4bf7/lib/private/User/Manager.php#L334-L337
(def username-regex #"^(?=[a-zA-Z0-9_.@\-]{3,20}$)(?!.*[_.]{2})[^_.].*[^_.]$")
(defn validate-username [tr username]
  (if (re-matches username-regex username)
    username
    (throw (ex-info "Validation error" {:validation/error (tr [:member/username-validation])}))))

(defn clean-email [email]
  (str/trim (str/lower-case email)))

(defn generate-invite-code! [{:keys [system] :as req} member]
  (let [invite-code (secret-box/random-str 32)
        key (str "invite:" invite-code)]
    (redis/wcar (:redis system)
                (redis/setex key (* 60 60 24 30) (:member/gigo-key member)))
    invite-code))

(defn create-member! [{:keys [system] :as req}]
  (let [tr (i18n/tr-from-req req)]
    (try
      (let [{:keys [create-sno-id phone email name nick username section-name active?] :as params} (-> req :params) gigo-key (str (sq/generate-squuid))
            txs [{:member/name name
                  :member/nick nick
                  :member/gigo-key gigo-key
                  :member/phone (twilio/clean-number (:env system) phone)
                  :member/username (validate-username tr username)
                  :member/active? (common/check->bool active?)
                  :member/section [:section/name section-name]
                  :member/email (clean-email email)}]
            new-member (:member (transact-member! req gigo-key txs))]
        (when (common/check->bool create-sno-id)
          (email/send-new-user-email! req new-member (generate-invite-code! req new-member)))
        new-member)

      (catch Exception e
        (throw
         (cond
           (= :db.error/unique-conflict (:db/error (ex-data e)))
           (munge-unique-conflict-error tr e)
           :else e))))))
(defn update-member! [req]
  (let [gigo-key (-> req :path-params :gigo-key)
        params (common/unwrap-params req)
        member-ref [:member/gigo-key gigo-key]
        tx-data [{:db/id member-ref
                  :member/name (:name params)
                  :member/phone (:phone params)
                  :member/email (clean-email (:email params))
                  :member/active? (common/check->bool (:active? params))
                  :member/section [:section/name (:section-name params)]}]]
    (transact-member! req gigo-key tx-data)))

(defn member-current-insurance-info [{:keys [db] :as req} member]
  (let [policy (q/insurance-policy-effective-as-of db (t/inst) q/policy-pattern)
        coverages (q/instruments-for-member-covered-by db  member policy q/instrument-coverage-detail-pattern)]
    coverages))

(defn fetch-invite-code [system invite-code]
  (let [key (str "invite:" invite-code)]
    (redis/wcar (:redis system) (redis/get key))))

(defn members-with-open-invites
  "Return the members with open invites"
  [req]
  (->> (redis/wcar (-> req :system :redis) (redis/keys "invite:*"))
       (map (fn [k]
              {:key k
               :gigo-key (redis/wcar (-> req :system :redis) (redis/get k))}))
       (map (fn [{:keys [gigo-key key] :as r}]
              (assoc (q/retrieve-member (:db req) gigo-key)
                     :member/invite-code
                     (second (str/split key #":")))))))

(defn delete-invitation [req]
  (redis/wcar (-> req :system :redis) (redis/del (str "invite:" (-> req :params :invite-code)))))

(defn load-invite
  "Load the invitation from the request, returns nil if it is not valid."
  [{:keys [system db params] :as req}]
  (try
    (let [invite-code (:code params)
          member-gigo-key (fetch-invite-code system invite-code)]
      (if-not member-gigo-key
        (throw (ex-info "Invite code expired" {:invite-code invite-code}))
        {:member (q/retrieve-member db member-gigo-key) :invite-code invite-code}))
    (catch Throwable e
      (tap> {:ex e})
      (errors/log-error! req e)
      nil)))

(defn setup-account [{:keys [datomic-conn system params db] :as req}]
  (let [{:keys [password password-confirm invite-code]} params
        member-gigo-key (fetch-invite-code system invite-code)]
    (if-not member-gigo-key
      (throw (ex-info "Invite code expired during setup" {:reason :code-expired}))
      (let [member (q/retrieve-member db member-gigo-key)
            pw-validation-result (auth/validate-password password password-confirm)]
        (if-not (= :password/valid pw-validation-result)
          (throw (ex-info "Password invalid" {:reason pw-validation-result
                                              :invite-data {:member member :invite-code invite-code}}))
          (let [new-user (keycloak/create-new-member! (keycloak/kc-from-req req) member password)]
            (:member (transact-member! req member-gigo-key [[:db/add [:member/gigo-key member-gigo-key]
                                                             :member/keycloak-id (:user/user-id new-user)]]))))))))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require  '[datomic.client.api :as datomic])
    (def env (-> state/system :app.ig/env))
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def redis (-> state/system :app.ig/redis))
    (def db (datomic/db conn))) ;; rcf

  (all-open-invites {:redis redis :db db})
  (q/retrieve-member db "2KAOMVbGXP0rB-kv1Z2NUstpzwwTtujD-dnMzSCmHsc")
  (load-invite {:db db :system {:env env} :params {:code
                                                   "TlBZDgvm3XxaegLBrDvZNkQsu3YwVRutJosggNZdC9YmCzL1jt2kUfDxdtElBEgcbhVNCLQipSS7SiOVygPHod6E6xjiDd9a3CjfsU2hEWZzXeoaVaTrEre1StrqfHHqZQXbuYZXd_nNEErC_tobIxAt7MhC7KfjleZ2_-RDzRnPoVlvDHA2sy2vsLP5mHWyeSMlTQZG"}})

  (transact-member! {:datomic-conn conn} "0185ee9c-7e67-8733-82f6-7a74aa588a92"
                    [[:db/add [:member/gigo-key "0185ee9c-7e67-8733-82f6-7a74aa588a92"]
                      :member/username "testjoe"]])
  ;;
  )
