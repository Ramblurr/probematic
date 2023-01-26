(ns app.members.controller
  (:require [tick.core :as t]
            [app.datomic :as d]
            [app.queries :as q]
            [app.controllers.common :as common]
            [datomic.client.api :as datomic]
            [com.yetanalytics.squuid :as sq]
            [app.i18n :as i18n]))

(defn sections [db]
  (->> (d/find-all db :section/name [:section/name])
       (mapv first)
       (sort-by :section/name)))

(defn members [db]
  (->> (d/find-all db :member/gigo-key q/member-pattern)
       (mapv #(first %))
       (sort-by (juxt :member/name :member/active))))

(defn transact-member!
  [conn gigo-key tx-data]
  {:member
   (d/find-by (:db-after (datomic/transact conn {:tx-data tx-data}))
              :member/gigo-key
              gigo-key
              q/member-pattern)})

(defn update-active-and-section! [{:keys [datomic-conn db] :as req}]
  (let [params (common/unwrap-params req)
        gigo-key (:gigo-key params)
        member (q/retrieve-member db gigo-key)
        member-ref (d/ref member :member/gigo-key)
        tx-data [[:db/add member-ref :member/section [:section/name (:section-name params)]]
                 [:db/add member-ref :member/active? (not (:member/active? member))]]]
    (transact-member! datomic-conn gigo-key tx-data)))

(defn munge-unique-conflict-error [req e]
  (let [tr (i18n/tr-from-req req)
        field (cond
                (re-find #".*:member/phone.*" (ex-message e)) :error/member-unique-phone
                (re-find #".*:member/username.*" (ex-message e))  :error/member-unique-username
                (re-find #".*:member/email.*" (ex-message e))   :error/member-unique-email
                :else nil)]
    (ex-info "Validation error" {:validation/error (if field (tr [field]) (ex-message e))})))

(defn create-member! [{:keys [datomic-conn db] :as req}]
  (let [{:keys [create-sno-id phone email name nick username section-name active?] :as params} (-> req :params)]
    (cond
      (q/member-by-email db email)
      (throw (ex-info "Validation error" {:validation/field "email"
                                          :validation/error "Member already exists with that e-mail address"}))
      :else
      (let [gigo-key (str (sq/generate-squuid))
            txs [{:member/name name
                  :member/nick nick
                  :member/gigo-key gigo-key
                  :member/phone phone
                  :member/active? (common/check->bool active?)
                  :member/section [:section/name section-name]
                  :member/email email}]]
        (try
          (:member (transact-member! datomic-conn gigo-key txs))
          (catch Exception e
            (throw
             (cond
               (= :db.error/unique-conflict (:db/error (ex-data e)))
               (munge-unique-conflict-error req e)
               :else e))))))))

(defn update-member! [{:keys [datomic-conn] :as req}]
  (let [gigo-key (-> req :path-params :gigo-key)
        params (common/unwrap-params req)
        member-ref [:member/gigo-key gigo-key]
        tx-data [{:db/id member-ref
                  :member/name (:name params)
                  :member/phone (:phone params)
                  :member/email (:email params)
                  :member/active? (common/check->bool (:active? params))
                  :member/section [:section/name (:section-name params)]}]]
    (transact-member! datomic-conn gigo-key tx-data)))

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
