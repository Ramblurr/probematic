(ns app.insurance.controller
  (:require

   [app.queries :as q]
   [app.datomic :as d]
   [tick.core :as t]
   [datomic.client.api :as datomic]
   [com.yetanalytics.squuid :as sq]
   [app.util :as util]
   [app.controllers.common :as common]
   [app.debug :as debug]
   [ctmx.form :as form]
   [ctmx.core :as ctmx]
   [app.db :as db]
   [medley.core :as m]))

(defn sum-by [ms k]
  (->> ms
       (map k)
       (reduce + 0)))

(defn make-category-factor-lookup
  "Given a policy, create a map where the keys are the category ids and the values are the category factors"
  [policy]
  (->> (-> policy :insurance.policy/category-factors)
       (map (fn [factor]
              {:instrument.category/category-id (-> factor :insurance.category.factor/category :instrument.category/category-id)
               :insurance.category.factor/factor (:insurance.category.factor/factor factor)}))
       (reduce (fn [r m]
                 (assoc r (:instrument.category/category-id m) (:insurance.category.factor/factor m))) {})))

(defn update-coverage-price [category-factor base-premium-factor coverage]
  ;; (tap> {:coverage coverage :base-factor base-premium-factor :category category-factor})
  (assoc coverage :instrument.coverage/types
         (map (fn [coverage-type]
                ;; (tap> {:type coverage-type})
                (assoc coverage-type :insurance.coverage.type/cost
                       (* (:instrument.coverage/value coverage)
                          category-factor
                          base-premium-factor
                          (:insurance.coverage.type/premium-factor coverage-type)))) (:instrument.coverage/types coverage))))

(defn update-total-coverage-price
  "Given a policy and a specific instrument coverage, calculate the total price for the instrument"
  [policy {:instrument.coverage/keys [value instrument] :as coverage}]
    ;;  value * category factor * premium factor * coverage factor
  (let [category-id (-> instrument :instrument/category :instrument.category/category-id)
        category-factor (get  (make-category-factor-lookup policy) category-id)
        ;; _ (tap> {:cat-id category-id :cat-fact category-factor :lookup (make-category-factor-lookup policy) :policy policy})
        premium-factor (-> policy :insurance.policy/premium-factor)
        coverage  (update-coverage-price category-factor premium-factor coverage)
        total-cost (sum-by (:instrument.coverage/types coverage) :insurance.coverage.type/cost)]
    (assoc coverage :instrument.coverage/cost total-cost)))

(defn create-policy-txs [{:keys [name effective-at effective-until
                                 base-factor overnight-factor proberaum-factor
                                 category-factors]}]
  (let [category-factor-txs (mapv (fn [{:keys [category-id factor]}]
                                    {:db/id category-id
                                     :insurance.category.factor/category-factor-id (sq/generate-squuid)
                                     :insurance.category.factor/category [:instrument.category/category-id category-id]
                                     :insurance.category.factor/factor (bigdec factor)})
                                  category-factors)
        category-factor-tx-ids (mapv :db/id category-factor-txs)
        policy-txs [{:db/id "ct_basic"
                     :insurance.coverage.type/type-id (sq/generate-squuid)
                     :insurance.coverage.type/name "Basic Coverage"
                     :insurance.coverage.type/description ""
                     :insurance.coverage.type/premium-factor (bigdec 1.0)}

                    {:db/id "ct_overnight"
                     :insurance.coverage.type/type-id (sq/generate-squuid)
                     :insurance.coverage.type/name "Auto/Overnight"
                     :insurance.coverage.type/description ""
                     :insurance.coverage.type/premium-factor (bigdec overnight-factor)}
                    {:db/id "ct_proberaum"
                     :insurance.coverage.type/type-id (sq/generate-squuid)
                     :insurance.coverage.type/name "Proberaum"
                     :insurance.coverage.type/description ""
                     :insurance.coverage.type/premium-factor (bigdec proberaum-factor)}

                    {:insurance.policy/policy-id (sq/generate-squuid)
                     :insurance.policy/currency :currency/EUR
                     :insurance.policy/name name
                     :insurance.policy/effective-at effective-at
                     :insurance.policy/effective-until effective-until
                     :insurance.policy/covered-instruments []
                     :insurance.policy/coverage-types ["ct_basic" "ct_overnight" "ct_proberaum"]
                     :insurance.policy/premium-factor base-factor
                     :insurance.policy/category-factors category-factor-tx-ids}]]
    (concat category-factor-txs policy-txs)))

(defn duplicate-policy-tx [{:insurance.policy/keys [name effective-at effective-until premium-factor category-factors coverage-types covered-instruments currency] :as old-policy}]
  (let [cat-factor-txs (map-indexed (fn [idx {:insurance.category.factor/keys [category factor]}]
                                      {:db/id (str "cat_fact_" idx)
                                       :insurance.category.factor/category-factor-id (sq/generate-squuid)
                                       :insurance.category.factor/category [:instrument.category/category-id (:instrument.category/category-id category)]
                                       :insurance.category.factor/factor (bigdec factor)})
                                    category-factors)
        cat-factor-ids (map :db/id cat-factor-txs)
        coverage-type-txs (map-indexed (fn [idx {:insurance.coverage.type/keys [type-id name description premium-factor]}]
                                         {:db/id (str "cat_type_" idx)
                                          :insurance.coverage.type/name name
                                          :insurance.coverage.type/type-id (sq/generate-squuid)
                                          :old-type-id type-id
                                          :insurance.coverage.type/description description
                                          :insurance.coverage.type/premium-factor premium-factor})
                                       coverage-types)

        coverage-type-ids (map :db/id coverage-type-txs)
        coverage-type-old-to-new (reduce (fn [m tx]
                                           (assoc m (:old-type-id tx) (:db/id tx))) {} coverage-type-txs)
        coverage-type-txs (map #(dissoc % :old-type-id) coverage-type-txs)

        covered-instruments-txs (map-indexed (fn [idx {:instrument.coverage/keys [instrument types private? value]}]
                                               {:db/id (str "coverage_" idx)
                                                :instrument.coverage/coverage-id (sq/generate-squuid)
                                                :instrument.coverage/instrument (d/ref instrument :instrument/instrument-id)
                                                :instrument.coverage/types (mapv (fn [{:insurance.coverage.type/keys [type-id]}]
                                                                                   (get coverage-type-old-to-new type-id)) types)
                                                :instrument.coverage/private? private?
                                                :instrument.coverage/value value})
                                             covered-instruments)

        covered-instruments-ids (map :db/id covered-instruments-txs)]

    (map util/remove-nils
         (concat
          cat-factor-txs
          coverage-type-txs
          covered-instruments-txs
          [{:insurance.policy/policy-id (sq/generate-squuid)
            :insurance.policy/name (str "Copy of " name)
            :insurance.policy/effective-at (t/inst effective-at)
            :insurance.policy/effective-until (t/inst effective-until)
            :insurance.policy/currency currency
            :insurance.policy/premium-factor premium-factor
            :insurance.policy/coverage-types coverage-type-ids
            :insurance.policy/category-factors  cat-factor-ids
            :insurance.policy/covered-instruments covered-instruments-ids}]))))

(defn transact-policy! [conn tx-data]
  (let [result (d/transact conn {:tx-data tx-data})]
    (if  (d/db-ok? result)
      {:policy
       (d/find-by (datomic/db conn)
                  :insurance.policy/policy-id
                  (:insurance.policy/policy-id (last tx-data))
                  q/policy-pattern)}
      result)))

(defn datestr->inst
  "Convert a string like 2022-01-01 to an instant at midnight"
  [dstr]
  (-> dstr (t/date) (t/at (t/midnight)) (t/inst)))

(defn ->policy [policy]
  (-> policy
      (update :insurance.policy/effective-at t/zoned-date-time)
      (update :insurance.policy/effective-until t/zoned-date-time)))

(defn policies [db]
  (->> (d/find-all db :insurance.policy/policy-id q/policy-pattern)
       (mapv #(->policy (first %)))
       (sort-by (juxt :insurance.policy/effective-until :insurance.policy/name))))

(defn retrieve-policy [db policy-id]
  (->policy (d/find-by db :insurance.policy/policy-id policy-id q/policy-pattern)))

(defn duplicate-policy [{:keys [db datomic-conn] :as req}]
  (let [params (common/unwrap-params req)
        old-pol-id (common/ensure-uuid (:policy-id params))
        old-policy (retrieve-policy db old-pol-id)
        new-policy-tx (duplicate-policy-tx old-policy)]
    (transact-policy! datomic-conn new-policy-tx)))

(defn create-policy! [{:keys [db datomic-conn] :as req}]
  (let [params (common/unwrap-params req)
        effective-at (-> params :effective-at datestr->inst)
        effective-until (-> params :effective-until datestr->inst)
        base-factor (-> params :base-factor bigdec)
        tx-data [{:insurance.policy/policy-id (sq/generate-squuid)
                  :insurance.policy/currency :currency/EUR
                  :insurance.policy/name (:name params)
                  :insurance.policy/effective-at effective-at
                  :insurance.policy/effective-until effective-until
                  :insurance.policy/premium-factor base-factor}]]
    (transact-policy! datomic-conn tx-data)))

(defn update-policy! [{:keys [db datomic-conn] :as req} policy-id]
  (let [params (common/unwrap-params req)
        effective-at (-> params :effective-at datestr->inst)
        effective-until (-> params :effective-until datestr->inst)
        base-factor (-> params :base-factor bigdec)
        tx-data [{:insurance.policy/policy-id  policy-id
                  :insurance.policy/currency :currency/EUR
                  :insurance.policy/name (:name params)
                  :insurance.policy/effective-at effective-at
                  :insurance.policy/effective-until effective-until
                  :insurance.policy/premium-factor base-factor}]]
    (transact-policy! datomic-conn tx-data)))

(defn has-category-factor? [policy category-factor-id]
  (m/find-first #(= category-factor-id (:insurance.category.factor/category-factor-id %))
                (:insurance.policy/category-factors policy)))

(defn create-category-factor! [{:keys [datomic-conn] :as req} policy-id]
  (let [policy-id (common/ensure-uuid policy-id)
        params (common/unwrap-params req)
        tx-data [{:insurance.category.factor/category-factor-id (sq/generate-squuid)
                  :db/id "cat_fact"
                  :insurance.category.factor/factor (bigdec (:premium-factor params))
                  :insurance.category.factor/category [:instrument.category/category-id (common/ensure-uuid (:category-id params))]}
                 [:db/add [:insurance.policy/policy-id policy-id] :insurance.policy/category-factors "cat_fact"]]
        result (d/transact datomic-conn {:tx-data tx-data})]
    (if (d/db-ok? result)
      {:policy (retrieve-policy (:db-after result) policy-id)}
      result)))

(defn update-category-factors! [{:keys [datomic-conn] :as req} policy-id]
  (let [params (common/unwrap-params req)
        policy-id (common/ensure-uuid policy-id)
        tx-data (mapv (fn [{:keys [premium-factor category-id delete]}]
                        (if delete
                          [:db/retractEntity [:insurance.category.factor/category-factor-id (parse-uuid category-id)]]
                          {:insurance.category.factor/category-factor-id (parse-uuid category-id)
                           :insurance.category.factor/factor (bigdec premium-factor)}))
                      (if (sequential? params) params [params]))
        result (d/transact datomic-conn {:tx-data tx-data})]
    (if (d/db-ok? result)
      {:policy (retrieve-policy (:db-after result) policy-id)}
      result)))

(defn update-coverage-types! [{:keys [datomic-conn] :as req} policy-id]
  (let [params (common/unwrap-params req)
        policy-id (common/ensure-uuid policy-id)
        tx-data (mapv (fn [{:keys [premium-factor name type-id delete]}]
                        (if delete
                          [:db/retractEntity [:insurance.coverage.type/type-id (parse-uuid type-id)]]
                          {:insurance.coverage.type/name name
                           :insurance.coverage.type/type-id  (parse-uuid type-id)
                           :insurance.coverage.type/premium-factor (bigdec premium-factor)}))
                      (if (sequential? params) params [params]))
        result (d/transact datomic-conn {:tx-data tx-data})]
    (if (d/db-ok? result)
      {:policy (retrieve-policy (:db-after result) policy-id)}
      result)))

(defn create-coverage-type! [{:keys [datomic-conn] :as req} policy-id]
  (let [params (common/unwrap-params req)
        tx-data [{:insurance.coverage.type/type-id (sq/generate-squuid)
                  :insurance.coverage.type/name (:coverage-name params)
                  :insurance.coverage.type/description ""
                  :insurance.coverage.type/premium-factor (bigdec (:premium-factor params))
                  :db/id "new-coverage"}
                 [:db/add [:insurance.policy/policy-id policy-id] :insurance.policy/coverage-types "new-coverage"]]
        result (d/transact datomic-conn {:tx-data tx-data})]
    (if  (d/db-ok? result)
      {:policy (retrieve-policy (:db-after result) policy-id)}
      result)))

(defn upsert-instrument! [conn {:keys [owner-gigo-key category-id
                                       name make model build-year
                                       instrument-id
                                       serial-number]}]
  (let [tx-data [(util/remove-nils {:instrument/instrument-id (or  instrument-id (sq/generate-squuid))
                                    :instrument/owner [:member/gigo-key owner-gigo-key]
                                    :instrument/category [:instrument.category/category-id category-id]
                                    :instrument/name name
                                    :instrument/make make
                                    :instrument/model model
                                    :instrument/build-year build-year
                                    :instrument/serial-number serial-number})]
        result  (d/transact conn {:tx-data tx-data})]
    (if  (d/db-ok? result)
      {:instrument
       (d/find-by (:db-after result)
                  :instrument/instrument-id
                  (:instrument/instrument-id (last tx-data))
                  q/instrument-pattern)}
      result)))

(defn create-instrument! [{:keys [datomic-conn] :as req}]
  (let [params (-> (common/unwrap-params req)
                   (update :category-id parse-uuid))]

    (upsert-instrument! datomic-conn params)))

(defn update-instrument! [{:keys [datomic-conn] :as req} instrument-id]
  (let [params (-> (common/unwrap-params req)
                   (update :category-id parse-uuid)
                   (assoc :instrument-id instrument-id))]
    (upsert-instrument! datomic-conn params)))

(defn add-instrument-coverage! [conn {:keys [instrument-id policy-id private? value coverage-type-ids]}]
  (d/transact conn {:tx-data [{:db/id "covered_instrument"
                               :instrument.coverage/coverage-id (sq/generate-squuid)
                               :instrument.coverage/instrument [:instrument/instrument-id instrument-id]
                               :instrument.coverage/types (mapv (fn [type-id] [:insurance.coverage.type/type-id type-id]) coverage-type-ids)
                               :instrument.coverage/private? private?
                               :instrument.coverage/value (bigdec value)}
                              [:db/add [:insurance.policy/policy-id policy-id] :insurance.policy/covered-instruments "covered_instrument"]]}))

(defn remove-instrument-coverage! [{:keys [datomic-conn] :as req} coverage-id]
  (let [result (d/transact datomic-conn {:tx-data [[:db/retractEntity [:instrument.coverage/coverage-id (common/ensure-uuid coverage-id)]]]})]
    (if (d/db-ok? result)
      {:policy (retrieve-policy (:db-after result) (-> req :policy :insurance.policy/policy-id))}
      result)))

(defn create-instrument-coverage! [{:keys [datomic-conn] :as req} policy-id]
  (let [params (-> (common/unwrap-params req))
        policy-id (common/ensure-uuid policy-id)
        instrument-id (parse-uuid (:instrument-id params))
        tx-data [{:instrument.coverage/coverage-id (sq/generate-squuid)
                  :instrument.coverage/instrument [:instrument/instrument-id instrument-id]
                  :instrument.coverage/value (bigdec (:value params))
                  :instrument.coverage/private? (common/check->bool (:private? params))
                  :db/id "covered_instrument"}
                 [:db/add [:insurance.policy/policy-id policy-id] :insurance.policy/covered-instruments "covered_instrument"]]
        result (d/transact datomic-conn {:tx-data tx-data})]
    (if (d/db-ok? result)
      {:policy (retrieve-policy (:db-after result) policy-id)}
      result)))

(defn retrieve-coverage [db coverage-id]
  (d/find-by db :instrument.coverage/coverage-id (common/ensure-uuid coverage-id) q/instrument-coverage-detail-pattern))

(defn get-coverage-type-from-coverage [instrument-coverage type-id]
  (m/find-first #(= type-id (:insurance.coverage.type/type-id %))
                (:instrument.coverage/types instrument-coverage)))

(defn toggle-instrument-coverage-type! [{:keys [datomic-conn db]} coverage-id type-id]
  (let [coverage-id (common/ensure-uuid coverage-id)
        type-id (common/ensure-uuid type-id)
        coverage (retrieve-coverage db coverage-id)
        operation (if (get-coverage-type-from-coverage coverage type-id) :db/retract :db/add)
        tx-data [[operation [:instrument.coverage/coverage-id coverage-id] :instrument.coverage/types [:insurance.coverage.type/type-id (common/ensure-uuid type-id)]]]
        result (d/transact datomic-conn {:tx-data tx-data})]
    (if (d/db-ok? result)
      {:coverage (retrieve-coverage (:db-after result) coverage-id)}
      result)))

(defn ->instrument [query-result]
  query-result)

(defn instruments [db]
  (->> (d/find-all db :instrument/instrument-id q/instrument-pattern)
       (mapv #(->instrument (first %)))
       (sort-by (juxt #(get-in % [:instrument/owner :member/name]) :instrument/name))))

(defn retrieve-instrument [db instrument-id]
  (->instrument (d/find-by db :instrument/instrument-id instrument-id q/instrument-detail-pattern)))

(defn instrument-categories [db]
  (mapv first
        (d/find-all db :instrument.category/category-id  [:instrument.category/name :instrument.category/category-id])))

(def category-default-factors
  {"Blech, Sax, Elektrisch" (bigdec 2.0)
   "Holz" (bigdec 1.3)
   "Schlag" (bigdec 1.6)
   "Etui/Zubehör/Mundstück" (bigdec 0.8)
   "Zupf und Bögen" (bigdec 1.0)
   "Akkordeon" (bigdec 2.0)
   "Streich" (bigdec 1.5)})

(def category-default-tx
  [{:instrument.category/category-id (sq/generate-squuid)
    :instrument.category/name "Blech, Sax, Elektrisch"
    :instrument.category/code "1"}
   {:instrument.category/category-id (sq/generate-squuid)
    :instrument.category/name "Holz"
    :instrument.category/code "2"}
   {:instrument.category/category-id (sq/generate-squuid)
    :instrument.category/name "Schlag"
    :instrument.category/code "3"}

   {:instrument.category/category-id (sq/generate-squuid)
    :instrument.category/name "Etui/Zubehör/Mundstück"
    :instrument.category/code "4"}

   {:instrument.category/category-id (sq/generate-squuid)
    :instrument.category/name "Zupf und Bögen"
    :instrument.category/code "5"}

   {:instrument.category/category-id (sq/generate-squuid)
    :instrument.category/name "Akkordeon"
    :instrument.category/code "6"}

   {:instrument.category/category-id (sq/generate-squuid)
    :instrument.category/name "Streich"
    :instrument.category/code "7"}])

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require  '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn))) ;; rcf

  (retrieve-policy db
                   (->
                    (policies db)
                    first
                    :insurance.policy/policy-id
                    str
                    parse-uuid))

  (d/transact conn {:tx-data category-default-tx})

  (defn rename-category [conn code new-name]
    (let [ref (d/ref (d/find-by (datomic/db conn) :instrument.category/code code
                                [:instrument.category/category-id :instrument.category/name])
                     :instrument.category/category-id)]
      (d/transact conn {:tx-data [[:db/add ref :instrument.category/name new-name]]})))

  (rename-category conn "4" "Etui/Zubehör/Mundstück")
  (rename-category conn "5" "Zupf und Bögen")

  (def category-blech-ref
    (d/ref (d/find-by (datomic/db conn) :instrument.category/code "4"
                      [:instrument.category/category-id :instrument.category/name])
           :instrument.category/category-id))

  (d/find-all db :member/name [:member/gigo-key])

  (def casey-ref
    (d/ref
     (d/find-by db :member/name "Casey Link" [:member/gigo-key])
     :member/gigo-key))

  (d/transact conn {:tx-data [{:instrument/owner casey-ref
                               :instrument/category category-blech-ref
                               :instrument/name "Marching Trombone"
                               :instrument/instrument-id (sq/generate-squuid)
                               :instrument/make "King"
                               :instrument/model "Flugelbone"
                               :instrument/build-year "1971"
                               :instrument/serial-number "12345"}]})

  (def caseys-instrument-ref (d/ref
                              (d/find-by (datomic/db conn) :instrument/owner casey-ref [:instrument/instrument-id])
                              :instrument/instrument-id))

  (d/transact conn {:tx-data [{:db/ident :insurance.policy/effective-until
                               :db/doc "The instant at which this policy ends"
                               :db/valueType :db.type/instant
                               :db/cardinality :db.cardinality/one}]})

  (def txns [{:db/id "cat_factor_blech"
              :insurance.category.factor/category-factor-id (sq/generate-squuid)
              :insurance.category.factor/category category-blech-ref
              :insurance.category.factor/factor (bigdec 2.0)}
             {:db/id "ct_basic"
              :insurance.coverage.type/type-id (sq/generate-squuid)
              :insurance.coverage.type/name "Basic Coverage"
              :insurance.coverage.type/description ""
              :insurance.coverage.type/premium-factor (bigdec 1.0)}

             {:db/id "ct_overnight"
              :insurance.coverage.type/type-id (sq/generate-squuid)
              :insurance.coverage.type/name "Auto/Overnight"
              :insurance.coverage.type/description ""
              :insurance.coverage.type/premium-factor (bigdec 0.25)}
             {:db/id "ct_proberaum"
              :insurance.coverage.type/type-id (sq/generate-squuid)
              :insurance.coverage.type/name "Proberaum"
              :insurance.coverage.type/description ""
              :insurance.coverage.type/premium-factor (bigdec 0.2)}

             {:instrument.coverage/coverage-id (sq/generate-squuid)
              :instrument.coverage/instrument caseys-instrument-ref
              :instrument.coverage/types ["ct_basic" "ct_overnight" "ct_proberaum"]
              :instrument.coverage/private? false
              :instrument.coverage/value (bigdec 1000)}

             {:insurance.policy/policy-id (sq/generate-squuid)
              :insurance.policy/currency :currency/EUR
              :insurance.policy/name "2022-2023"
              :insurance.policy/effective-at  (t/inst)
              :insurance.policy/effective-until
              (t/inst
               (t/>>
                (t/inst)
                (t/new-duration 365 :days)))
              :insurance.policy/covered-instruments []
              :insurance.policy/coverage-types ["ct_basic" "ct_overnight" "ct_proberaum"]
              :insurance.policy/premium-factor (* (bigdec 1.07) (bigdec 0.00447))
              :insurance.policy/category-factors  ["cat_factor_blech"]}])

  (d/transact conn {:tx-data txns})

  (def policy-ref (d/ref (d/find-by (datomic/db conn) :insurance.policy/name "2022-2023" [:insurance.policy/policy-id])
                         :insurance.policy/policy-id))

  (def caseys-covered-ref    (d/ref
                              (ffirst
                               (d/find-all (datomic/db conn)
                                           :instrument.coverage/coverage-id
                                           [:instrument.coverage/coverage-id]))
                              :instrument.coverage/coverage-id))

  (d/transact conn {:tx-data [{:db/id policy-ref
                               :insurance.policy/covered-instruments [caseys-covered-ref]}]})

  (d/transact conn {:tx-data [{:db/id caseys-covered-ref
                               :instrument.coverage/value (bigdec 3000)}]})

  (def policy
    (d/find-by (datomic/db conn) :insurance.policy/name "2022-2023"
               q/policy-pattern))

  (sum-by
   (-> policy
       :insurance.policy/covered-instruments
       first
       :instrument.coverage/types)
   :insurance.coverage.type/premium-factor)

  (make-category-factor-lookup policy)

  (update-total-coverage-price policy
                               (-> policy
                                   :insurance.policy/covered-instruments
                                   first))

  (update-coverage-price
   (bigdec 2.0)
   (:insurance.policy/premium-factor policy)
   (-> policy
       :insurance.policy/covered-instruments
       first))

  ;; end
  )
