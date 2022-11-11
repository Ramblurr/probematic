(ns app.schemas
  (:require [malli.util :as mu]
            [medley.core :as medley]
            [app.schema-helpers :as schemas]
            [malli.core :as m]
            [malli.error :as me]
            [tick.core :as t]
            [malli.registry :as mr]
            [malli.transform :as mt]
            [app.schemas.domain :as domain]
            [app.schemas.http-api :as http-api]))

(def registry
  (mr/composite-registry
   (m/default-schemas)
   app.schema-helpers/common-registry
   domain/registry
   http-api/registry))

(def malli-opts {:registry registry})

(defn valid? [doc-type doc]
  (m/validate doc-type doc malli-opts))

(defn explain [doc-type doc]
  (m/explain doc-type doc malli-opts))

(defn explain-human [doc-type doc]
  (me/humanize (m/explain doc-type doc malli-opts)))

(defn encode [doc-type doc]
  (m/encode doc-type doc malli-opts mt/string-transformer))

(defn decode [doc-type doc]
  (m/decode doc-type doc malli-opts (mt/transformer mt/string-transformer mt/strip-extra-keys-transformer)))

(defn datomic-transformer []
  (mt/transformer
   {:name :datomic
    :decoders (mt/-string-decoders)
    :encoders (mt/-string-encoders)}))

(defn decode-datomic [doc-type doc]
  (m/decode doc-type doc malli-opts (mt/transformer datomic-transformer mt/strip-extra-keys-transformer)))
(defn encode-datomic [doc-type doc]
  (m/encode doc-type doc malli-opts (mt/transformer datomic-transformer mt/strip-extra-keys-transformer)))

(defn schema
  ([s]
   (m/schema s malli-opts))
  ([s local-registry]
   (m/schema s
             (update malli-opts :registry mr/composite-registry local-registry))))

(defn sanitize-error
  "Sanitizes an error for printing in logs"
  [e]
  (if-let [data (ex-data e)]
    (if (-> data :explain :value)
      (-> data
          (medley/dissoc-in [:explain :value])
          (update-in [:explain :schema] (fn [s] (m/properties s)))
          (update-in [:explain :errors] (fn [es]
                                          (map #(dissoc % :value) es))))

      e)

    e))

(comment
  (require '[malli.error :as me]
           '[malli.core :as m]
           '[malli.transform :as mt]
           '[malli.util :as mu]
           '[tick.core :as t])

  (def user1 {:user/id         1
              :user/username   "Joe"
              :user/active     true
              :user/email      "joe@example.com"
              :user/created_at (t/now)
              :user/updated_at (t/now)})
  (def invalid-user {:user/id         1
                     :user/username   "Joe"
                     :user/email      "foo@"
                     :user/created_at (t/now)
                     :user/updated_at (t/now)})
  (valid? :doc/user user1)
  (explain-human :doc/user user1)
  (valid? :doc/user invalid-user)
  (explain-human :doc/user invalid-user)
  (valid? ::instant (t/now))
  (explain-human ::email-address "foo@foo.com")
  (explain-human ::country-alpha3 "AT")
  (explain-human ::country-alpha3 "AUT")
;
  )
