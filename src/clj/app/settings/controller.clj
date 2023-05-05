(ns app.settings.controller
  (:require
   [app.queries :as q]
   [com.yetanalytics.squuid :as sq]
   [app.util.http :as common]
   [datomic.client.api :as datomic]
   [app.util :as util]))

(defn create-discount-type [{:keys [datomic-conn db] :as req}]
  (let [discount-type-name (-> req :params :discount-type-name)
        {:keys [db-after]} (datomic/transact datomic-conn {:tx-data [{:travel.discount.type/discount-type-id (sq/generate-squuid)
                                                                      :travel.discount.type/enabled? true
                                                                      :travel.discount.type/discount-type-name discount-type-name}]})]

    db-after))

(defn update-discount-type [{:keys [datomic-conn db] :as req}]
  (let [{:keys [discount-type-name discount-type-id enabled?]} (:params req)
        discount-type-id (util/ensure-uuid! discount-type-id)
        tx-data [{:travel.discount.type/discount-type-id discount-type-id
                  :travel.discount.type/enabled? (common/check->bool enabled?)
                  :travel.discount.type/discount-type-name discount-type-name}]
        {:keys [db-after]} (datomic/transact datomic-conn {:tx-data tx-data})]

    (q/retrieve-discount-type db-after discount-type-id)))

(defn create-section [{:keys [datomic-conn] :as req}]
  (let [section-name (-> req :params :section-name)
        {:keys [db-after]} (datomic/transact datomic-conn {:tx-data [{:section/active? true
                                                                      :section/name section-name}]})]

    db-after))

(defn update-section [{:keys [datomic-conn] :as req}]
  (let [{:keys [old-section-name section-name active?]} (:params req)
        tx-data [[:db/add [:section/name old-section-name] :section/name section-name]
                 [:db/add [:section/name old-section-name] :section/active? (common/check->bool active?)]]
        {:keys [db-after]} (datomic/transact datomic-conn {:tx-data tx-data})]

    (q/retrieve-section-by-name db-after section-name)))

(defn order-sections [{:keys [datomic-conn] :as req}]
  (tap> {:p (:params req)
         :u (common/unwrap-params req)})
  (let [sections (-> req common/unwrap-params :settings-page :sections :section-single)
        tx-data (map (fn [{:keys [section-name position]}]
                       [:db/add [:section/name section-name] :section/position (common/parse-long position)]) sections)]
    (:db-after (datomic/transact datomic-conn {:tx-data tx-data}))))
