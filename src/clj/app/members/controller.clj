(ns app.members.controller
  (:require
   [app.controllers.common :as common]
   [app.datomic :as d]
   [app.i18n :as i18n]
   [app.queries :as q]
   [app.twilio :as twilio]
   [com.yetanalytics.squuid :as sq]
   [datomic.client.api :as datomic]
   [tick.core :as t]
   [app.keycloak :as keycloak]
   [app.debug :as debug]))

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
    (when (keycloak-attrs-changed? before-member after-member)
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
  (tap> {:u username})
  (if (re-matches username-regex username)
    username
    (throw (ex-info "Validation error" {:validation/error (tr [:member/username-validation])}))))

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
                  :member/email email}]]
        (:member (transact-member! req gigo-key txs)))
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
                  :member/email (:email params)
                  :member/active? (common/check->bool (:active? params))
                  :member/section [:section/name (:section-name params)]}]]
    (transact-member! req gigo-key tx-data)))

(defn member-current-insurance-info [{:keys [db] :as req} member]
  (let [policy (q/insurance-policy-effective-as-of db (t/inst) q/policy-pattern)
        coverages (q/instruments-for-member-covered-by db  member policy q/instrument-coverage-detail-pattern)]
    coverages))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require  '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn))) ;; rcf
  (sections db)
  (def section-defaults ["percussion"
                         "bass"
                         "trombone/bombardino"
                         "sax tenor"
                         "sax alto"
                         "trumpet"
                         "sax soprano/clarinet"
                         "flute"
                         "No Section"])

  (sections (datomic/db conn))
  (d/transact conn {:tx-data
                    (map (fn [n] {:section/name n}) section-defaults)})

  (->>
   (datomic/q '[:find ?e
                :in $  ?now
                :where [?e :insurance.policy/effective-until ?date]
                [(>= ?date ?now)]]
              db  (t/now))
   (map #(datomic/entity db %)))
  (map first)
  (sort-by :insurance.policy/effective-until)
  reverse
  first
  ;;
  )
