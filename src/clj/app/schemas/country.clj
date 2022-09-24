(ns app.schemas.country
  (:require [clj-iso3166.country :as c]
            [malli.core :as m]))

(def alpha3-set
  (set (map :alpha3 c/countries)))

(def alpha3-map
  (->> c/countries
       (map #(vector (:alpha3 %) %))
       (into {})))

(def alpha2-map
  (->> c/countries
       (map #(vector (:alpha2 %) %))
       (into {})))

(defn valid-alpha3? [a]
  (and
   (string? a)
   (re-matches #"[A-Z]{3}" a)
   (contains? alpha3-set a)))

(defn iso3->iso2 [iso3]
  (->
   (alpha3-map iso3)
   :alpha2))

(defn iso2->iso3 [iso2]
  (->
   (alpha2-map iso2)
   :alpha3))

(def CountryAlpha3
  (m/-simple-schema {:type :app.schema/country-alpha3
                     :pred valid-alpha3?
                     :type-properties {:error/message "is not a valid 3 character country code"
                                       :decode/string str
                                       :encode/string str
                                       :decode/json str
                                       :encode/json str
                                       :json-schema/type "string"
                                       :json-schema/format "string"}}))

(def currencies
  {"AUT" "EUR"
   "BEL" "EUR"
   "BGR" "EUR"
   "HRV" "EUR"
   "CYP" "EUR"
   "CZE" "EUR"
   "DNK" "EUR"
   "EST" "EUR"
   "FIN" "EUR"
   "FRA" "EUR"
   "DEU" "EUR"
   "GRC" "EUR"
   "HUN" "EUR"
   "IRL" "EUR"
   "ITA" "EUR"
   "LVA" "EUR"
   "LTU" "EUR"
   "LUX" "EUR"
   "MLT" "EUR"
   "NLD" "EUR"
   "POL" "EUR"
   "PRT" "EUR"
   "ROU" "EUR"
   "SVK" "EUR"
   "SVN" "EUR"
   "ESP" "EUR"
   "SWE" "EUR"
   "USA" "USD"
   "CDN" "USD"})

(defn billing-currency-for
  "Return the currency code for the country to be used for billing purposes"
  [iso3]
  (or (currencies iso3) "USD"))

(comment
  (m/validate CountryAlpha3 "AUT")
  (m/validate CountryAlpha3 "AT")

  (alpha3-map "AUT")

  ;
  )
