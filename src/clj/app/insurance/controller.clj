(ns app.insurance.controller
  (:require
   [app.datomic :as d]
   [app.insurance.excel :as excel]
   [app.queries :as q]
   [app.schemas :as s]
   [app.util :as util]
   [app.util.http :as util.http]
   [clojure.data :as clojure.data]
   [clojure.string :as str]
   [com.yetanalytics.squuid :as sq]
   [datomic.client.api :as datomic]
   [malli.util :as mu]
   [medley.core :as m]
   [tick.core :as t])
  (:import
   [java.io ByteArrayOutputStream]
   [java.net URLEncoder]))

(def instrument-coverage-statuses [:instrument.coverage.status/needs-review
                                   :instrument.coverage.status/reviewed
                                   :instrument.coverage.status/coverage-active])

(def instrument-coverage-changes [:instrument.coverage.change/new
                                  :instrument.coverage.change/removed
                                  :instrument.coverage.change/changed
                                  :instrument.coverage.change/none])

(def policy-statuses [:insurance.policy.status/active
                      :insurance.policy.status/sent
                      :insurance.policy.status/draft])

(def str->instrument-coverage-status (zipmap (map name instrument-coverage-statuses) instrument-coverage-statuses))
(def str->policy-status (zipmap (map name policy-statuses) policy-statuses))
(def str->instrument-coverage-change (zipmap (map name instrument-coverage-changes) instrument-coverage-changes))

(defn policy-editable? [{:insurance.policy/keys [status]}]
  (= status :insurance.policy.status/draft))

(defn sum-by [ms k]
  ;; (tap> {:ms ms :k k})
  (when (seq ms)
    (->> ms
         (map k)
         (reduce + 0))))

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
  (assert category-factor)
  ;; (tap> {:coverage coverage :base-factor base-premium-factor :category category-factor})
  (assoc coverage :instrument.coverage/types
         (map (fn [coverage-type]
                ;; (tap> {:type coverage-type :cov coverage :cat-f category-factor :base base-premium-factor})
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
        ;; _ (tap> {:lookup (make-category-factor-lookup policy) :cat-id category-id})
        _  (assert category-factor)
        ;; _ (tap> {:cat-id category-id :cat-fact category-factor :lookup (make-category-factor-lookup policy) :policy policy})
        premium-factor (-> policy :insurance.policy/premium-factor)
        coverage  (update-coverage-price category-factor premium-factor coverage)
        ;; _ (tap> {:cov coverage})
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

        covered-instruments-txs (map-indexed (fn [idx {:instrument.coverage/keys [status instrument types private? value change]}]
                                               {:db/id (str "coverage_" idx)
                                                :instrument.coverage/coverage-id (sq/generate-squuid)
                                                :instrument.coverage/instrument (d/ref instrument :instrument/instrument-id)
                                                :instrument.coverage/types (mapv (fn [{:insurance.coverage.type/keys [type-id]}]
                                                                                   (get coverage-type-old-to-new type-id)) types)
                                                :instrument.coverage/private? private?
                                                :instrument.coverage/status status
                                                :instrument.coverage/change change
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
            :insurance.policy/status :insurance.policy.status/draft
            :insurance.policy/premium-factor premium-factor
            :insurance.policy/coverage-types coverage-type-ids
            :insurance.policy/category-factors  cat-factor-ids
            :insurance.policy/covered-instruments covered-instruments-ids}]))))

(defn transact-policy! [conn tx-data]
  (let [result (datomic/transact conn {:tx-data tx-data})]
    {:policy
     (d/find-by (:db-after result)
                :insurance.policy/policy-id
                (:insurance.policy/policy-id (last tx-data))
                q/policy-pattern)}))

(defn datestr->inst
  "Convert a string like 2022-01-01 to an instant at midnight"
  [dstr]
  (-> dstr (t/date) (t/at (t/midnight)) (t/inst)))

(defn duplicate-policy! [{:keys [db datomic-conn] :as req}]
  (let [params (util.http/unwrap-params req)
        old-pol-id (util.http/ensure-uuid (:policy-id params))
        old-policy (q/retrieve-policy db old-pol-id)
        new-policy-tx (duplicate-policy-tx old-policy)]
    (transact-policy! datomic-conn new-policy-tx)))

(defn delete-policy! [{:keys [db datomic-conn] :as req}]
  (try
    (let
     [params (util.http/unwrap-params req)
      policy-id (util.http/ensure-uuid (:policy-id params))
      policy-ref     [:insurance.policy/policy-id policy-id]]
      (datomic/transact datomic-conn {:tx-data [[:db/retractEntity policy-ref]]})
      true)
    (catch Throwable t
      (if
       (re-find #".*Cannot resolve key.*" (ex-message t))
        true
        (throw t)))))

(defn create-policy! [{:keys [db datomic-conn] :as req}]
  (let [params (util.http/unwrap-params req)
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
  (let [params (util.http/unwrap-params req)
        effective-at (-> params :effective-at datestr->inst)
        effective-until (-> params :effective-until datestr->inst)
        base-factor (-> params :base-factor bigdec)
        tx-data [{:insurance.policy/policy-id  policy-id
                  :insurance.policy/currency :currency/EUR
                  :insurance.policy/name (:name params)
                  :insurance.policy/status (or (str->policy-status (get params :status)) :insurance.policy.status/draft)
                  :insurance.policy/effective-at effective-at
                  :insurance.policy/effective-until effective-until
                  :insurance.policy/premium-factor base-factor}]]
    (transact-policy! datomic-conn tx-data)))

(defn has-category-factor? [policy category-factor-id]
  (m/find-first #(= category-factor-id (:insurance.category.factor/category-factor-id %))
                (:insurance.policy/category-factors policy)))

(defn create-category-factor! [{:keys [datomic-conn] :as req} policy-id]
  (let [policy-id (util.http/ensure-uuid policy-id)
        params (util.http/unwrap-params req)
        tx-data [{:insurance.category.factor/category-factor-id (sq/generate-squuid)
                  :db/id "cat_fact"
                  :insurance.category.factor/factor (bigdec (:premium-factor params))
                  :insurance.category.factor/category [:instrument.category/category-id (util.http/ensure-uuid (:category-id params))]}
                 [:db/add [:insurance.policy/policy-id policy-id] :insurance.policy/category-factors "cat_fact"]]
        result (datomic/transact datomic-conn {:tx-data tx-data})]
    {:policy (q/retrieve-policy (:db-after result) policy-id)}))

(defn update-category-factors! [{:keys [datomic-conn] :as req} policy-id]
  (let [params (util.http/unwrap-params req)
        policy-id (util.http/ensure-uuid policy-id)
        tx-data (mapv (fn [{:keys [premium-factor category-id delete]}]
                        (if delete
                          [:db/retractEntity [:insurance.category.factor/category-factor-id (parse-uuid category-id)]]
                          {:insurance.category.factor/category-factor-id (parse-uuid category-id)
                           :insurance.category.factor/factor (bigdec premium-factor)}))
                      (if (sequential? params) params [params]))
        result (datomic/transact datomic-conn {:tx-data tx-data})]
    {:policy (q/retrieve-policy (:db-after result) policy-id)}))

(defn update-coverage-types! [{:keys [datomic-conn] :as req} policy-id]
  (let [params (util.http/unwrap-params req)
        policy-id (util.http/ensure-uuid policy-id)
        tx-data (mapv (fn [{:keys [description premium-factor name type-id delete] :as args}]
                        (if delete
                          [:db/retractEntity [:insurance.coverage.type/type-id (parse-uuid type-id)]]
                          {:insurance.coverage.type/name name
                           :insurance.coverage.type/description description
                           :insurance.coverage.type/type-id  (parse-uuid type-id)
                           :insurance.coverage.type/premium-factor (bigdec premium-factor)}))
                      (if (sequential? params) params [params]))
        result (datomic/transact datomic-conn {:tx-data tx-data})]
    {:policy (q/retrieve-policy (:db-after result) policy-id)}))

(defn create-coverage-type! [{:keys [datomic-conn] :as req} policy-id]
  (let [params (util.http/unwrap-params req)
        tx-data [{:insurance.coverage.type/type-id (sq/generate-squuid)
                  :insurance.coverage.type/name (:coverage-name params)
                  :insurance.coverage.type/description (:description params)
                  :insurance.coverage.type/premium-factor (bigdec (:premium-factor params))
                  :db/id "new-coverage"}
                 [:db/add [:insurance.policy/policy-id policy-id] :insurance.policy/coverage-types "new-coverage"]]
        result (datomic/transact datomic-conn {:tx-data tx-data})]
    {:policy (q/retrieve-policy (:db-after result) policy-id)}))

#_(defn upsert-instrument! [conn {:keys [owner-member-id category-id
                                         name make model build-year
                                         instrument-id
                                         serial-number]}]
    (let [tx-data [(util/remove-nils {:instrument/instrument-id (or  instrument-id (sq/generate-squuid))
                                      :instrument/owner [:member/member-id (util/ensure-uuid! owner-member-id)]
                                      :instrument/category [:instrument.category/category-id category-id]
                                      :instrument/name name
                                      :instrument/make make
                                      :instrument/model model
                                      :instrument/build-year build-year
                                      :instrument/serial-number serial-number})]
          result  (datomic/transact conn {:tx-data tx-data})]
      {:instrument
       (d/find-by (:db-after result)
                  :instrument/instrument-id
                  (:instrument/instrument-id (last tx-data))
                  q/instrument-pattern)}))

#_(defn create-instrument! [{:keys [datomic-conn] :as req}]
    (let [params (-> (util.http/unwrap-params req)
                     (update :category-id parse-uuid))]

      (upsert-instrument! datomic-conn params)))

#_(defn update-instrument! [{:keys [datomic-conn] :as req} instrument-id]
    (let [params (-> (util.http/unwrap-params req)
                     (update :category-id parse-uuid)
                     (assoc :instrument-id instrument-id))]
      (upsert-instrument! datomic-conn params)))

(defn add-instrument-coverage! [conn {:keys [instrument-id policy-id private? value coverage-type-ids]}]
  (datomic/transact conn {:tx-data [{:db/id "covered_instrument"
                                     :instrument.coverage/coverage-id (sq/generate-squuid)
                                     :instrument.coverage/instrument [:instrument/instrument-id instrument-id]
                                     :instrument.coverage/types (mapv (fn [type-id] [:insurance.coverage.type/type-id type-id]) coverage-type-ids)
                                     :instrument.coverage/private? private?
                                     :instrument.coverage/value (bigdec value)}
                                    [:db/add [:insurance.policy/policy-id policy-id] :insurance.policy/covered-instruments "covered_instrument"]]}))

(defn remove-instrument-coverage! [{:keys [datomic-conn] :as req} coverage-id]
  (let [result (datomic/transact datomic-conn {:tx-data [[:db/retractEntity [:instrument.coverage/coverage-id (util.http/ensure-uuid coverage-id)]]]})]
    {:policy (q/retrieve-policy (:db-after result) (-> req :policy :insurance.policy/policy-id))}))

(defn create-instrument-coverage! [{:keys [datomic-conn] :as req} policy-id]
  (let [params (-> (util.http/unwrap-params req))
        policy-id (util.http/ensure-uuid policy-id)
        instrument-id (parse-uuid (:instrument-id params))
        coverage-types (mapv util.http/ensure-uuid  (util/ensure-coll (:coverage-types params)))
        coverage-types-tx (mapv (fn [coverage-type-id]
                                  [:db/add "covered_instrument" :instrument.coverage/types [:insurance.coverage.type/type-id coverage-type-id]]) coverage-types)
        tx-data (concat coverage-types-tx  [{:instrument.coverage/coverage-id (sq/generate-squuid)
                                             :instrument.coverage/instrument [:instrument/instrument-id instrument-id]
                                             :instrument.coverage/value (bigdec (:value params))
                                             :instrument.coverage/private? (= "private" (:band-or-private params))
                                             :db/id "covered_instrument"}
                                            [:db/add [:insurance.policy/policy-id policy-id] :insurance.policy/covered-instruments "covered_instrument"]])

        result (datomic/transact datomic-conn {:tx-data tx-data})]
    {:policy (q/retrieve-policy (:db-after result) policy-id)}))

(defn reconcile-coverage-types [eid existing-types new-types]
  (let [[added removed] (clojure.data/diff (set existing-types)  (set new-types))
        ;; _ (tap> {:added added :removed removed})
        add-tx (map #(-> [:db/add eid :instrument.coverage/types [:insurance.coverage.type/type-id  %]]) (filter some? added))
        remove-tx (map #(-> [:db/retract eid :instrument.coverage/types [:insurance.coverage.type/type-id  %]]) (filter some? removed))]
    (concat add-tx remove-tx)))

(def UpdateInstrument
  "This schema describes the http post we receive when updating an instrument"
  (s/schema
   [:map {:name ::UpdateInstrument}
    [:instrument-id {:optional true} [:or :uuid [:string {:min 0 :max 0}]]]
    [:category-id :uuid]
    [:owner-member-id :uuid]
    [:description :string]
    [:build-year :string]
    [:serial-number :string]
    [:make ::s/non-blank-string]
    [:model :string]
    [:instrument-name ::s/non-blank-string]]))

(def UpdateCoverage
  "This schema describes the http post we receive when updating a coverage"
  (s/schema
   [:map {:name ::UpdateInstrumentAndCoverage}
    [:instrument-id :uuid]
    [:policy-id :uuid]
    [:coverage-id {:optional true} [:or :uuid [:string {:min 0 :max 0}]]]
    [:coverage-types [:vector {:min 1} :uuid]]
    [:value [:int {:min 1}]]
    [:item-count [:int {:min 1}]]
    [:private-band [:enum "band" "private"]]]))

(def UpdateInstrumentAndCoverage
  "This schema describes the http post we receive when updating an instrument and coverage "
  (mu/merge UpdateInstrument UpdateCoverage))

(defn update-coverage-txs [{:keys [value coverage-types item-count private-band] :as decoded} coverage]
  (let [coverage-ref (d/ref coverage)
        before-types (mapv :insurance.coverage.type/type-id (:instrument.coverage/types coverage))
        after-types (util/remove-dummy-uuid (mapv util.http/ensure-uuid (util/ensure-coll coverage-types)))
        coverage-changes (reconcile-coverage-types coverage-ref after-types before-types)
        ;; if these things have changed then we need to ensure the changes get marked so we can send them upstream
        has-upstream-change? (or
                              (not (== (:instrument.coverage/value coverage) value))
                              (not (== (:instrument.coverage/item-count coverage 1) item-count))
                              (boolean (seq coverage-changes)))

        _ (tap> {:value-new value
                 :value-old (:instrument.coverage/value coverage)
                 :value-changed (not (== (:instrument.coverage/value coverage) value))
                 :count-new item-count
                 :count-old (:instrument.coverage/item-count coverage)
                 :count-changed (not= (:instrument.coverage/item-count coverage) item-count)
                 :coverage-changes coverage-changes
                 :covchanged (not-empty coverage-changes)
                 :has-upstream-change? has-upstream-change?})]

    (concat coverage-changes
            [[:db/add coverage-ref :instrument.coverage/value (bigdec value)]
             [:db/add coverage-ref :instrument.coverage/status (if has-upstream-change? :instrument.coverage.status/needs-review (:instrument.coverage/status coverage))]
             [:db/add coverage-ref :instrument.coverage/change (if has-upstream-change?  :instrument.coverage.change/changed  (:instrument.coverage/change coverage))]
             [:db/add coverage-ref :instrument.coverage/private? (= "private" private-band)]
             [:db/add coverage-ref :instrument.coverage/item-count item-count]])))

(defn create-coverage-txs [{:keys [value coverage-types item-count private-band policy-id instrument-id] :as decoded}]
  (->> (mapv (fn [coverage-type-id]
               [:db/add "covered_instrument" :instrument.coverage/types [:insurance.coverage.type/type-id coverage-type-id]])
             (util/remove-dummy-uuid coverage-types))
       (concat [{:instrument.coverage/coverage-id (sq/generate-squuid)
                 :instrument.coverage/instrument [:instrument/instrument-id instrument-id]
                 :instrument.coverage/value (bigdec value)
                 :instrument.coverage/item-count item-count
                 :instrument.coverage/status :instrument.coverage.status/needs-review
                 :instrument.coverage/change :instrument.coverage.change/new
                 :instrument.coverage/private? (= "private" private-band)
                 :db/id "covered_instrument"}
                [:db/add [:insurance.policy/policy-id policy-id] :insurance.policy/covered-instruments "covered_instrument"]])))

(defn update-instrument-txs [{:keys [description build-year serial-number make instrument-name owner-member-id model category-id] :as decoded} instrument-id]
  [{:instrument/instrument-id instrument-id
    :instrument/description description
    :instrument/build-year build-year
    :instrument/serial-number serial-number
    :instrument/make make
    :instrument/model model
    :instrument/name instrument-name
    :instrument/owner [:member/member-id owner-member-id]
    :instrument/category [:instrument.category/category-id category-id]}])

(defn upsert-instrument! [req]
  (let [decoded (util/remove-nils (s/decode UpdateInstrument (util.http/unwrap-params req)))]
    (if (s/valid? UpdateInstrument decoded)
      (let [instrument-id (if (str/blank? (:instrument-id decoded))
                            (sq/generate-squuid)
                            (:instrument-id decoded))
            txs (update-instrument-txs decoded instrument-id)
            {:keys [db-after]} (d/transact-wrapper req {:tx-data txs})]
        {:instrument (q/retrieve-instrument db-after instrument-id)})
      {:error (s/explain-human UpdateInstrument decoded)})))

(defn upsert-coverage! [{:keys [db] :as req}]
  (let [params (util.http/unwrap-params req)
        decoded (util/remove-nils (s/decode UpdateCoverage params))
        coverage-id (util.http/ensure-uuid (:coverage-id decoded))
        policy-id (:policy-id decoded)
        policy (q/retrieve-policy db policy-id)
        valid-change? (s/valid? UpdateCoverage decoded)]
    (cond
      (policy-editable? policy)
      {:error {:form-error :insurance/error-edit-frozen-policy} :policy policy}
      valid-change?
      (let [txs (if coverage-id
                  ;; upsert
                  (update-coverage-txs decoded (q/retrieve-coverage db coverage-id))
                  ;; create
                  (create-coverage-txs decoded))
            {:keys [db-after]} (d/transact-wrapper req {:tx-data txs})]
        {:policy (q/retrieve-policy db-after policy-id)})
      :else
      {:error (s/explain-human UpdateCoverage decoded)
       :policy (q/retrieve-policy db policy-id)})))

(defn update-instrument-and-coverage! [{:keys [db] :as req}]
  (let [params (util.http/unwrap-params req)
        decoded  (util/remove-nils (s/decode UpdateInstrumentAndCoverage params))
        policy (q/retrieve-policy db (:policy-id decoded))
        change-valid? (s/valid? UpdateInstrumentAndCoverage decoded)]
    (cond
      (policy-editable? policy)
      {:error {:form-error :insurance/error-edit-frozen-policy} :policy policy}

      change-valid?
      (let [coverage (q/retrieve-coverage db (util.http/ensure-uuid (:coverage-id decoded)))
            _ (assert coverage)

            txs (concat (update-coverage-txs decoded coverage)
                        (update-instrument-txs decoded (:instrument-id decoded)))
            ;; _ (tap> {:tx txs})
            {:keys [db-after]} (d/transact-wrapper req {:tx-data txs})]
        {:policy (q/retrieve-policy db-after (:policy-id decoded))})

      :else {:error (s/explain-human UpdateInstrumentAndCoverage decoded)
             :policy (q/retrieve-policy db (:policy-id decoded))})))

(defn delete-coverage! [{:keys [db] :as req}]
  (let [coverage-id (-> req (util.http/unwrap-params) :coverage-id util/ensure-uuid!)
        ;; TODO  do we want to remove the instrument if there are no other coverages?
        coverage (q/retrieve-coverage db coverage-id)
        #_related-coverages #_(->> (q/coverages-for-instrument db (-> coverage :instrument.coverage/instrument :instrument/instrument-id) q/instrument-coverage-detail-pattern)
                                   (remove #(= coverage-id (:instrument.coverage/coverage-id %))))
        txs [[:db/retractEntity [:instrument.coverage/coverage-id coverage-id]]]
        result (d/transact-wrapper req {:tx-data txs})]
    {:policy (q/retrieve-policy (:db-after result) (-> coverage :insurance.policy/_covered-instruments  :insurance.policy/policy-id))}))

(defn get-coverage-type-from-coverage [instrument-coverage type-id]
  (m/find-first #(= type-id (:insurance.coverage.type/type-id %))
                (:instrument.coverage/types instrument-coverage)))

(defn toggle-instrument-coverage-type! [{:keys [datomic-conn db]} coverage-id type-id]
  (let [coverage-id (util.http/ensure-uuid coverage-id)
        type-id (util.http/ensure-uuid type-id)
        coverage (q/retrieve-coverage db coverage-id)
        operation (if (get-coverage-type-from-coverage coverage type-id) :db/retract :db/add)
        tx-data [[operation [:instrument.coverage/coverage-id coverage-id] :instrument.coverage/types [:insurance.coverage.type/type-id (util.http/ensure-uuid type-id)]]]
        result (datomic/transact datomic-conn {:tx-data tx-data})]
    {:coverage (q/retrieve-coverage (:db-after result) coverage-id)}))

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

(defn enrich-coverages [policy coverage-types coverages]
  (mapv (fn [coverage]
          (let [coverage (update-total-coverage-price policy coverage)]
            (assoc coverage :types
                   (mapv (fn [{:insurance.coverage.type/keys [type-id]}]
                           (when-let [coverage-type (get-coverage-type-from-coverage coverage type-id)]
                             coverage-type))
                         coverage-types))))
        coverages))

(defn coverages-grouped-by-owner [policy]
  (->> (:insurance.policy/covered-instruments policy)
       (util/group-by-into-list :coverages (fn [c] (get-in c [:instrument.coverage/instrument :instrument/owner])))
       (mapv (fn [r] (update r :coverages #(sort-by (fn [c] (get-in c [:instrument.coverage/instrument :instrument/name])) %))))
       (mapv (fn [r] (update r :coverages #(enrich-coverages policy (:insurance.policy/coverage-types policy) %))))
       (mapv (fn [{:keys [coverages] :as person}]
               (assoc person :total (sum-by coverages :instrument.coverage/cost))))
       (sort-by :member/name)))

(def MarkAsSchema
  (s/schema
   [:map {:name ::MarkAs}
    [:policy-id :uuid]
    [:mark-as [:enum {:kw-namespace true} :instrument.coverage.status/needs-review :instrument.coverage.status/reviewed :instrument.coverage.status/coverage-active]]
    [:coverage-ids [:vector {:vectorize true} :uuid]]]))

(defn mark-coverages-as! [req]
  (let [{:keys [policy-id coverage-ids mark-as] :as p} (s/decode MarkAsSchema (:params req))]
    (if (s/valid? MarkAsSchema p)
      (let [txs (map (fn [cid]
                       [:db/add [:instrument.coverage/coverage-id cid] :instrument.coverage/status
                        mark-as]) coverage-ids)
            {:keys [db-after]} (d/transact-wrapper req {:tx-data txs})]
        {:policy (q/retrieve-policy db-after policy-id)
         :db-after db-after})
      (s/throw-error "Invalid arguments" nil MarkAsSchema p))))

(defn download-changes-excel [{:keys [db] :as req}]
  (let [policy-id (util.http/path-param-uuid! req :policy-id)

        policy (q/retrieve-policy db policy-id)
        {:keys [attachment-filename]} (util.http/unwrap-params req)
        _ (assert "attachment-filename")
        output-stream (ByteArrayOutputStream.)
        file-bytes (.toByteArray (excel/generate-excel-changeset! policy output-stream))]
    {:status 200
     :headers {"Content-Disposition" (format "%s; filename*=UTF-8''%s" "attachment" (URLEncoder/encode attachment-filename "UTF-8"))
               "Content-Type" "application/vnd.ms-excel"
               "Content-Length" (str (count file-bytes))}
     :body (java.io.ByteArrayInputStream. file-bytes)}))

(defn- mark-policy-status [{:keys [datomic-conn] :as req} policy-id status]
  (when-not ((set policy-statuses) status)
    (throw (ex-info "Invalid insurance policy status" {:status status})))
  (let [tx-data [{:insurance.policy/policy-id  policy-id
                  :insurance.policy/status status}]]
    (transact-policy! datomic-conn tx-data)))

(defn confirm-changes! [{:keys [db] :as req}]
  (mark-policy-status req (util.http/path-param-uuid! req :policy-id) :insurance.policy.status/active))

(defn send-changes! [{:keys [db] :as req}]
  (let [policy-id (util.http/path-param-uuid! req :policy-id)
        {:keys [attachment-filename] :as p} (util.http/unwrap-params req)
        _ (assert "attachment-filename")
        policy (q/retrieve-policy db policy-id)
        {:keys [subject recipient body]} p
        smtp (-> req :system :env :smtp-sno)
        from (:user smtp)]
    (excel/send-email! policy smtp from recipient subject body attachment-filename)
    (mark-policy-status req (util.http/path-param-uuid! req :policy-id) :insurance.policy.status/active)))

(defn instrument-coverage-history [{:keys [db] :as req} {:instrument.coverage/keys [coverage-id instrument] :as coverage}]
  (let [instr-id (:instrument/instrument-id instrument)
        instr-history (d/entity-history db :instrument/instrument-id instr-id)
        coverage-history (d/entity-history db :instrument.coverage/coverage-id coverage-id)]
    ;; (tap> {:instr-history instr-history :coverage-history coverage-history})
    (->>
     (concat instr-history
             coverage-history)
     (group-by :tx-id)
     (vals)
     (map (fn [txs]
            (reduce (fn [agg {:keys [audit changes timestamp tx-id]}]
                      (-> agg
                          (assoc :tx-id tx-id)
                          (assoc :timestamp timestamp)
                          (assoc :audit audit)
                          (update :changes concat changes))) {} txs)))
     (sort-by :timestamp)
     (reverse)
      ;; (vals)
      ;; (flatten)
     )))

(defn policy-totals [policy]
  (let [covered-instruments (:insurance.policy/covered-instruments policy)
        grouped-by-owner (coverages-grouped-by-owner policy)]
    {:total-cost (sum-by grouped-by-owner :total)
     :total-instruments  (count covered-instruments)
     :total-private-count (count (filter :instrument.coverage/private? covered-instruments))
     :total-band-count (count (remove :instrument.coverage/private? covered-instruments))
     :total-needs-review (count (filter #(= :instrument.coverage.status/needs-review (:instrument.coverage/status %)) covered-instruments))
     :total-reviewed (count (filter #(= :instrument.coverage.status/reviewed (:instrument.coverage/status %)) covered-instruments))
     :total-coverage-active (count (filter #(= :instrument.coverage.status/coverage-active (:instrument.coverage/status %)) covered-instruments))
     :total-changed (count (filter #(= :instrument.coverage.change/changed (:instrument.coverage/change %)) covered-instruments))
     :total-removed (count (filter #(= :instrument.coverage.change/removed (:instrument.coverage/change %)) covered-instruments))
     :total-new (count (filter #(= :instrument.coverage.change/new (:instrument.coverage/change %)) covered-instruments))}))

(defn policies-with-todos [db]
  (->> (q/policies db)
       (mapv (fn [policy]
               (merge policy (policy-totals policy))))
       (filter #(> (:total-needs-review %) 0))))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn))) ;; rcf
  (q/retrieve-coverage db #uuid "018e15c2-ddbf-89a1-bb60-61e5e01189b9")
  (q/retrieve-coverage db #uuid "018e15c2-ddbf-89a1-bb60-61e5e01189b7")

  (mark-policy-status {:datomic-conn conn} #uuid "018e15c2-ddbe-8b4f-b814-bddd26f8aec2" :insurance.policy.status/draft)

  (q/retrieve-policy db
                     (->
                      (q/policies db)
                      first
                      :insurance.policy/policy-id
                      str
                      parse-uuid))

  (datomic/transact conn {:tx-data category-default-tx})

  (defn rename-category [conn code new-name]
    (let [ref (d/ref (d/find-by (datomic/db conn) :instrument.category/code code
                                [:instrument.category/category-id :instrument.category/name])
                     :instrument.category/category-id)]
      (datomic/transact conn {:tx-data [[:db/add ref :instrument.category/name new-name]]})))

  (rename-category conn "4" "Etui/Zubehör/Mundstück")
  (rename-category conn "5" "Zupf und Bögen")

  (def category-blech-ref
    (d/ref (d/find-by (datomic/db conn) :instrument.category/code "4"
                      [:instrument.category/category-id :instrument.category/name])
           :instrument.category/category-id))

  (d/find-all db :member/name [:member/member-id])

  (def casey-ref
    (d/ref
     (d/find-by db :member/name "Casey Link" [:member/member-id])
     :member/member-id))

  (datomic/transact conn {:tx-data [{:instrument/owner casey-ref
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

  (datomic/transact conn {:tx-data [{:db/ident :insurance.policy/effective-until
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
              :insurance.policy/effective-at (t/inst)
              :insurance.policy/effective-until
              (t/inst
               (t/>>
                (t/inst)
                (t/new-duration 365 :days)))
              :insurance.policy/covered-instruments []
              :insurance.policy/coverage-types ["ct_basic" "ct_overnight" "ct_proberaum"]
              :insurance.policy/premium-factor (* (bigdec 1.07) (bigdec 0.00447))
              :insurance.policy/category-factors ["cat_factor_blech"]}])

  (datomic/transact conn {:tx-data txns})

  (def policy-ref (d/ref (d/find-by (datomic/db conn) :insurance.policy/name "2022-2023" [:insurance.policy/policy-id])
                         :insurance.policy/policy-id))

  (def caseys-covered-ref (d/ref
                           (ffirst
                            (d/find-all (datomic/db conn)
                                        :instrument.coverage/coverage-id
                                        [:instrument.coverage/coverage-id]))
                           :instrument.coverage/coverage-id))

  (datomic/transact conn {:tx-data [{:db/id policy-ref
                                     :insurance.policy/covered-instruments [caseys-covered-ref]}]})

  (datomic/transact conn {:tx-data [{:db/id caseys-covered-ref
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

  (defn ident [db attr]
    (:db/ident (datomic/pull db '[:db/ident] attr)))

  (defn ref? [db attr]
    (= :db.type/ref
       (->
        (datomic/pull db '[*] attr)
        :db/valueType
        :db/ident)))

  (defn resolve-ref [db eid]
    (datomic/pull db '[*] eid))

  (require '[app.debug :refer [xxx xxx>>]])

  (instrument-coverage-history {:db  (datomic/db conn)} (q/retrieve-coverage (datomic/db conn) #uuid "018e15c2-ddbf-89a1-bb60-61e5e01189b8")) ;; rcf

  (let [db (datomic/db conn)]
    (->> (datomic/q '{:find [?tx ?attr ?val ?added]
                      :in [$ ?coverage-id]
                      :where [[?e :instrument.coverage/coverage-id ?coverage-id]
                              [?e ?attr ?val ?tx ?added]]}
                    (datomic/history db)
                    #uuid "018e15c2-ddbf-89a1-bb60-61e5e01189b8")
         (xxx>>)
         (group-by first)
         (map (fn [[tx transactions]]
                (let [tx-info (datomic/pull db '[*] tx)]
                  {:timestamp (:db/txInstant tx-info)
                   :audit (select-keys tx-info [:audit/user])
                   :changes (->> transactions
                                 (map (fn [[_ attr val added]]
                                        [(ident db attr) (if (ref? db attr)
                                                           (resolve-ref db val)
                                                           val) (if added :added :retracted)]))
                                 (sort-by last))})))
         (sort-by :timestamp)))

  (map
   first (d/find-all db :insurance.category.factor/category-factor-id '[*]))

  (d/find-by db :instrument.coverage/coverage-id #uuid "0186c248-07ba-82c0-bfda-237986a7573b" q/instrument-coverage-detail-pattern)

  ;; end
  )
