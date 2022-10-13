(ns app.routes.helpers
  (:require
   [app.schemas :as schemas]
   [io.pedestal.http.body-params :as body-params]
   [io.pedestal.http.secure-headers :as secure-headers]
   [luminus-transit.time :as time]
   [muuntaja.core :as m]
   [reitit.coercion.malli :as rcm]
   [reitit.http.coercion :as coercion]
   [reitit.http.interceptors.exception :as exception]
   [reitit.http.interceptors.multipart :as multipart]
   [reitit.http.interceptors.muuntaja :as muuntaja]
   [reitit.http.interceptors.parameters :as parameters]
   [ring.middleware.keyword-params :as keyword-params]
   [reitit.swagger :as swagger]))

(def ^:private relaxed-csp "img-src 'self' data:; object-src 'none'; default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline' https://rsms.me; connect-src 'self'")

(derive ::error ::exception)
(derive ::failure ::exception)
(derive ::horror ::exception)

(defn handler [message exception request]
  (tap> exception)
  {:status 500
   :body   {:message   message
            :exception (str exception)
            :uri       (:uri request)}})

(def keyword-params-interceptor
  {:name ::keyword-params
   :enter (fn [ctx]
            (let [request (:request ctx)]
              (assoc ctx :request
                     (keyword-params/keyword-params-request request))))})

(def htmx-interceptor
  {:name ::htmx
   :enter (fn [ctx]
            (let [request (:request ctx)
                  headers (:headers request)]
              (if (some? (get headers "hx-request"))
                (-> ctx
                    (assoc-in [:request :htmx]
                              (->> headers
                                   (filter (fn [[key _]] (.startsWith key "hx-")))
                                   (map (fn [[key val]] [(keyword key) val]))
                                   (into {})))
                    (assoc-in [:request :htmx?] true))
                (assoc-in ctx [:request :htmx?] false))))})

(defn system-interceptor
  "Install the integrant system map into the request under the :system key"
  [system]
  {:name ::system
   :enter (fn [ctx]
            (assoc-in ctx [:request :system] system))})

(def exception-interceptor
  (exception/exception-interceptor
   (merge
    exception/default-handlers
    {;; ex-data with :type ::error
     ::error             (partial handler "error")

     ;; ex-data with ::exception or ::failure
     ::exception         (partial handler "exception")

     ;; override the default handler
     ::exception/default (partial handler "default")

     ;; print stack-traces for all exceptions
     ::exception/wrap    (fn [handler e request]
                           (.printStackTrace e)
                           (handler e request))})))

(def tap-interceptor
  {:name :tap-interceptr
   :enter (fn [req]
            (tap> (-> req :request))
            (tap> (-> req :request :params))
            (tap> (-> req :request :body-params))
            (tap> (-> req :request :form-params))
            req)})

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
                           htmx-interceptor
                           ;; htmx reequires all params (query, form etc) to be keywordized
                           keyword-params-interceptor
                           ;; multipart
                           (multipart/multipart-interceptor)])

(def default-coercion
  (-> rcm/default-options
      (assoc-in [:options :registry] schemas/registry)
      rcm/create))

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
