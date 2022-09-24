(ns app.routes.helpers
  (:require
   [app.schemas :as schemas]
   [io.pedestal.http.secure-headers :as secure-headers]
   [luminus-transit.time :as time]
   [muuntaja.core :as m]
   [reitit.coercion.malli :as rcm]
   [reitit.http.coercion :as coercion]
   [reitit.http.interceptors.exception :as exception]
   [reitit.http.interceptors.multipart :as multipart]
   [reitit.http.interceptors.muuntaja :as muuntaja]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.swagger :as swagger]
   [clojure.string :as str])
  (:import
   (java.sql SQLException)))

(def ^:private relaxed-csp "img-src 'self' data:; object-src 'none'; default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; connect-src 'self'")

(derive ::error ::exception)
(derive ::failure ::exception)
(derive ::horror ::exception)

(defn handler [message exception request]
  (tap> exception)
  {:status 500
   :body   {:message   message
            :exception (str exception)
            :uri       (:uri request)}})

(def exception-interceptor
  (exception/exception-interceptor
   (merge
    exception/default-handlers
    {;; ex-data with :type ::error
     ::error             (partial handler "error")

     ;; ex-data with ::exception or ::failure
     ::exception         (partial handler "exception")

     ;; SQLException and all it's child classes
     SQLException        (partial handler "sql-exception")

     ;; override the default handler
     ::exception/default (partial handler "default")

     ;; print stack-traces for all exceptions
     ::exception/wrap    (fn [handler e request]
                           (.printStackTrace e)
                           (handler e request))})))

(def default-interceptors [swagger/swagger-feature
                           ;; query-params & form-params
                           (parameters/parameters-interceptor)
                           ;; content-negotiation
                           (muuntaja/format-negotiate-interceptor)
                           ;; encoding response body
                           (muuntaja/format-response-interceptor)
                           ;; exception handling
                           exception-interceptor
                           ;; decoding request body
                           (muuntaja/format-request-interceptor)
                           ;; coercing response bodys
                           (coercion/coerce-response-interceptor)
                           ;; coercing request parameters
                           (coercion/coerce-request-interceptor)
                           ;; multipart
                           (multipart/multipart-interceptor)])

(def default-coercion
  (-> rcm/default-options (assoc-in [:options :registry] schemas/registry) rcm/create))

(def formats-instance
  (m/create
   (-> m/default-options
       (update-in
        [:formats "application/transit+json" :decoder-opts]
        (partial merge time/time-deserialization-handlers))
       (update-in
        [:formats "application/transit+json" :encoder-opts]
        (partial merge time/time-serialization-handlers))
       (assoc-in [:formats "application/json" :encoder-opts]
                 {:encode-key-fn name})
       (assoc-in [:formats "application/json" :decoder-opts]
                 {:decode-key-fn keyword}))))

(def relaxed-csp-header-value (secure-headers/content-security-policy-header relaxed-csp))
