(ns app.generators
  (:require [app.schemas.country :as country]
            [tick.core :as t])
  (:import
   [java.util.concurrent TimeUnit]
   [com.github.javafaker
    Faker]))

(def faker (Faker.))

(defn flip? []
  (-> faker (.random) (.nextBoolean)))

(defn maybe-prefix [prefix s]
  (if (flip?)
    (str prefix " " s)
    s))

(defn maybe-bookend [prefix s suffix]
  (if (flip?) ; prefix
    (if (flip?)
      (str prefix " " s " " suffix)
      (str prefix " " s))
    (if (flip?)
      (str s " " suffix)
      (str s))))

(defn last-name []
  (-> faker
      (.name)
      (.lastName)))

(defn first-name []
  (-> faker
      (.name)
      (.firstName)))

(defn phone []
  (-> faker (.phoneNumber) (.phoneNumber)))

(defn address [line1]
  (let [a (-> faker (.address))]

    {:address/line_1 (or line1 (-> a (.firstName)))
     :address/line_2 (-> a (.streetAddress))
     :address/line_3 (-> a (.secondaryAddress))
     :address/line_4 (-> a (.buildingNumber))
     :address/locality (-> a (.city))
     :address/region (-> a (.state))
     :address/country (country/iso2->iso3 (-> a (.countryCode)))
     :address/phone (phone)
     :address/valid_from (t/instant (-> faker (.date) (.past 360 TimeUnit/DAYS)))
     :address/postal_code (-> a (.zipCode))}))

(defn id-number []
  (-> faker (.idNumber) (.valid)))

(comment

  (first-name)
  (last-name)
  (address nil)
  ;
  )
