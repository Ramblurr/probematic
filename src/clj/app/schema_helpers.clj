(ns app.schema-helpers
  (:require
   [app.schemas.country :as country]
   [clojure.string :as string]
   [malli.core :as m]
   [tick.core :as t])
  (:import
   [java.time Duration]))

(def EmailAddress (m/-simple-schema {:type            :email-address
                                     :pred            #(and (string? %)
                                                            (re-matches #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$" %))
                                     :type-properties {:error/fn           '(fn [{:keys [schema value]} _]
                                                                              (str value " does not match regex " #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$"))
                                                       :decode/string      string/lower-case
                                                       :encode/string      string/lower-case
                                                       :decode/json        string/lower-case
                                                       :encode/json        string/lower-case
                                                       :json-schema/type   "string"
                                                       :json-schema/format "string"}}))
(def DurationSchema (m/-simple-schema {:type            :duration
                                       :pred            t/duration?
                                       :type-properties {:error/message      "should be a valid duration"
                                                         :decode/string      #(Duration/parse %)
                                                         :encode/string      #(when % (str %))
                                                         :decode/json        #(Duration/parse %)
                                                         :encode/json        #(when % (str %))
                                                         :json-schema/type   "string"
                                                         :json-schema/format "duration"
                                                         :swagger/example    "PT6H"}}))
(def InstantSchema (m/-simple-schema {:type            :instant
                                      :pred            t/instant?
                                      :type-properties {:error/message      "should be a valid instant"
                                                        :decode/string      #(some-> % t/instant)
                                                        :encode/string      str
                                                        :decode/json        #(some-> % t/instant)
                                                        :encode/json        str
                                                        :json-schema/type   "string"
                                                        :json-schema/format "date-time"}}))

(def InstSchema (m/-simple-schema {:type            :inst
                                   :pred            inst?
                                   :type-properties {:error/message      "should be a valid inst"
                                                     :decode/string      #(some-> % t/inst)
                                                     :encode/string      str
                                                     :decode/json        #(some-> % t/inst)
                                                     :encode/json        str
                                                     :json-schema/type   "string"
                                                     :json-schema/format "date-time"}}))

(def DateSchema (m/-simple-schema {:type            :date
                                   :pred            t/date?
                                   :type-properties {:error/message      "should be a valid date"
                                                     :decode/string      #(some-> % t/date)
                                                     :encode/string      str
                                                     :decode/json        #(some-> % t/date)
                                                     :encode/json        str
                                                     :json-schema/type   "string"
                                                     :json-schema/format "date"}}))
(def NonBlankString
  (m/-simple-schema {:type :app.schemas/non-blank-string
                     :pred #(and (string? %) (not (string/blank? %)))
                     :type-properties {:error/message "should not be blank"
                                       :decode/string str
                                       :encode/string str
                                       :decode/json str
                                       :encode/json str
                                       :json-schema/type "string"
                                       :json-schema/format "string"}}))

(def common-registry
  {:app.schemas/email-address EmailAddress
   :app.schemas/duration      DurationSchema
   :app.schemas/instant       InstantSchema
   :app.schemas/inst          InstSchema
   :app.schemas/date          DateSchema
   :app.schemas/non-blank-string NonBlankString
   :app.schemas/country-alpha3 country/CountryAlpha3})

(def interval?
  "Represents an interval as a map with two properties,
   a beginning and an end. Converts into Interval record on decode.
   On encode just uses the default encoding strategy for a map with
   two instants."
  [:map
   {:registry common-registry :decode/json {:enter '#(with-meta % {:ol/type :interval})}}
   [:beginning :app.schemas/instant]
   [:end {:optional true} [:maybe :app.schemas/instant]]])

(comment
  (require
   '[clojure.test :refer [is]]
   '[malli.error :as me]
   '[malli.core :as m]
   '[malli.transform :as mt]
   '[malli.util :as mu])

  (meta (with-meta {:beginning (t/now)} {:type :interval}))

;; Invalid email returns nil
  (me/humanize (m/explain EmailAddress "hello@world@"))

  (t/date-time)
  (t/new-duration 3600000 :millis)
  (t/duration? (Duration/parse "PT1H"))

  (import [java.time Duration])
  (Duration/parse (str (t/new-duration 3600000 :millis)))

  (t/duration "PT1H")

  (str (t/new-duration 3600000 :millis))

  (str (t/new-duration 3600000 :millis)))
