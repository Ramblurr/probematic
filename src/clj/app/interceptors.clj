(ns app.interceptors
  (:require
   [app.auth :as auth]
   [app.config :as config]
   [app.i18n :as i18n]
   [app.queries :as q]
   [app.rand-human-id :as human-id]
   [app.routes.errors :as errors]
   [app.routes.pedestal-prone :as pedestal-prone]
   [app.schemas :as schemas]
   [clojure.data :as diff]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [datomic.client.api :as d]
   [io.pedestal.http :as http]
   [io.pedestal.http.ring-middlewares :as middlewares]
   [io.pedestal.interceptor :as pedestal.interceptor]
   [io.pedestal.interceptor.chain :as chain]
   [io.pedestal.interceptor.error :as error-int]
   [luminus-transit.time :as time]
   [muuntaja.core :as m]
   [reitit.coercion.malli :as rcm]
   [reitit.http.coercion :as coercion]
   [reitit.http.interceptors.multipart :as multipart]
   [reitit.http.interceptors.muuntaja :as muuntaja]
   [reitit.http.interceptors.parameters :as parameters]
   [ring.middleware.keyword-params :as keyword-params])
  (:import
   (org.eclipse.jetty.server HttpConfiguration)))

(defn current-user-interceptor
  "Fetches the current user from the request (see app.auth/auth-interceptor),
   looks up the member and attaches the member info to the request under :session :session/member.
  If there is no authed member, then does nothing."
  [system]
  {:name ::current-user-interceptor
   :enter (fn [ctx]
            (if-let [member-email (-> ctx :request :session :session/email)]
              (if-let [member (q/member-by-email (d/db (-> system :datomic :conn)) member-email)]
                (assoc-in ctx [:request :session :session/member] member)
                ctx)
              ctx))})

(def keyword-params-interceptor
  "Keywordizes request parameter keys. CTMX expects this."
  {:name ::keyword-params
   :enter (fn [ctx]
            (let [request (:request ctx)]
              (assoc ctx :request
                     (keyword-params/keyword-params-request request))))})

(defn datomic-interceptor
  "Attaches the datomic db and connection to the request map"
  [system]
  {:name ::datomic--interceptor
   :enter (fn [ctx]
            (let [conn (-> system :datomic :conn)]
              (-> ctx
                  (assoc-in  [:request :db] (d/db conn))
                  (assoc-in  [:request :datomic-conn] conn))))})

(def service-error-handler
  (error-int/error-dispatch [ctx ex]

                            [{:exception-type :java.lang.ArithmeticException :interceptor ::another-bad-one}]
                            (assoc ctx :response {:status 400 :body "Another bad one"})

                            [{:exception-type :java.lang.ArithmeticException}]
                            (assoc ctx :response {:status 400 :body "A bad one"})

                            [{:exception-type :clojure.lang.ExceptionInfo :cognitect.anomalies/category :cognitect.anomalies/incorrect}]
                            (assoc ctx :response (errors/not-found-error (:request ctx) ex))

                            [{:exception-type :clojure.lang.ExceptionInfo :app/error-type :app.error.type/validation}]
                            (assoc ctx :response (errors/validation-error (:request ctx) ex))

                            [{:exception-type :clojure.lang.ExceptionInfo :app/error-type :app.error.type/authentication-failure}]
                            (assoc ctx :response (errors/unauthorized-error (:request ctx) ex))

                            :else
                            (assoc ctx :response (errors/unknown-error (:request ctx) ex))))

(def htmx-interceptor
  "Sets :htmx? to true if the request originates from htmx"
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
  {:name ::system-interceptor
   :enter (fn [ctx]
            (assoc-in ctx [:request :system] system))})

(defn dev-mode-interceptor
  "Tell the request if we are in dev mode or not"
  [system]
  {:name ::dev-mode-interceptor
   :enter (fn [ctx]
            (assoc-in ctx [:request :dev?] (config/dev-mode? (-> system :env))))})

(def human-id-interceptor
  "Add a human readable id for the request"
  {:name ::human-id-interceptor
   :enter (fn [ctx]
            (assoc-in ctx [:request :human-id] (human-id/human-id)))})
(defn webdav-interceptor
  "Add webdav service to request map"
  [system]
  {:name ::webdav-interceptor
   :enter (fn [ctx]
            (assoc-in ctx [:request :webdav] (:webdav system)))})

(def tap-interceptor
  {:name :tap-interceptor
   :enter (fn [req]
            (tap> (-> req :request))
            (tap> (-> req :request :params))
            (tap> (-> req :request :body-params))
            (tap> (-> req :request :form-params))
            req)})
(def log-request-interceptor
  "Logs all http requests with response time."
  {:name ::log-request
   :enter (fn [ctx]
            (assoc-in ctx [:request :start-time] (System/currentTimeMillis)))
   :leave (fn [ctx]
            (let [{:keys [uri start-time request-method query-string human-id] :as req} (:request ctx)
                  user-email (auth/get-current-email req)
                  finish (System/currentTimeMillis)
                  total (- finish start-time)]
              (log/info :msg "request completed"
                        :method (str/upper-case (name request-method))
                        :uri uri
                        :human-id human-id
                        :user-email user-email
                        :query-string  query-string
                        :status (:status (:response ctx))
                        :response-time total)
              ctx))})

(defn query-string-lang [query-string]
  (when query-string
    (last (re-find #"lang=([^&]+)" query-string))))

(defn cookie-lang [cookies]
  (get-in cookies ["lang" :value]))

(defn i18n-interceptor [system]
  (let [lang-dicts (:i18n-langs system)]
    (assert lang-dicts "Translations not available")
    {:name ::i18n-interceptor
     :enter (fn [ctx]

              (let [lang-dicts (if (config/dev-mode? (:env system))
                                 (i18n/read-langs)
                                 lang-dicts)
                    request (:request ctx)
                    query-string (:query-string request)
                    lang-qr (query-string-lang query-string)
                    lang-cookie (cookie-lang (:cookies request))
                    lang-browser (i18n/browser-lang (:headers request))
                    all-accepted-langs (filterv some? (concat  [lang-qr lang-cookie] lang-browser [(name i18n/default-locale)]))
                    both-set (and lang-qr lang-cookie)
                    change-lang (or
                                 (and both-set (not (= lang-qr lang-cookie)))
                                 (and (not both-set) lang-qr))
                    tempura-accepted (if (:tempura/accept-langs request)
                                       (:tempura/accept-langs request)
                                       [])
                    accepted (if all-accepted-langs (into [] (concat all-accepted-langs tempura-accepted)) tempura-accepted)
                    current-locale (i18n/supported-lang lang-dicts accepted)
                    tr (i18n/tr-with lang-dicts accepted)
                    req (if tr
                          (assoc request
                                 :will-change-lang change-lang
                                 :tr tr
                                 :tempura/accept-langs accepted
                                 :current-locale (keyword current-locale))
                          (assoc request
                                 :will-change-lang change-lang
                                 :current-locale (keyword current-locale)))
                    ;;
                    ]
                (assoc ctx :request req)))
     :leave (fn [ctx]
              (if (get-in ctx [:request :will-change-lang])
                (-> ctx

                    (assoc-in [:response :cookies "lang" :value] (name (get-in ctx [:request :current-locale])))
                    (assoc-in [:response :cookies "lang" :path] "/")
                    (assoc-in [:response :cookies "lang" :max-age] (* 3600 30)))
                ctx))}))

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

(defn prone-exception-interceptor
  "Pretty prints exceptions in the browser"
  [service]
  (update-in service [:io.pedestal.http/interceptors] #(vec (cons (pedestal-prone/exceptions {:app-namespaces ["app"]}) %))))

(defn http-configuration
  [max-size]
  (doto (HttpConfiguration.)
    (.setRequestHeaderSize max-size)))

(defn log-diffs [previous current]
  (let [[deleted added] (diff/diff (dissoc previous ::chain/queue ::chain/stack ::previous-ctx)
                                   (dissoc current ::chain/queue ::chain/stack ::previous-ctx))]
    (when deleted (log/debug :deleted deleted))
    (when added (log/debug :added added))))

(defn handle-debug [ctx]
  (log-diffs (::previous-ctx ctx) ctx)
  (assoc ctx ::previous-ctx ctx))

(def debug-interceptor
  {:name :debug
   :enter handle-debug
   :leave handle-debug})

(def inject-debug-interceptor
  {:name :inject-debug
   :enter
   (fn [ctx]
     (assoc
      ctx
      ::previous-ctx
      ctx
      ::chain/queue
      (into clojure.lang.PersistentQueue/EMPTY
            (cons debug-interceptor
                  (-> ctx
                      ::chain/queue
                      seq
                      (interleave (repeat debug-interceptor)))))))})

(defn default-reitit-interceptors [system]
  (into [] (remove nil?
                   [;; inject-debug-interceptor
                    human-id-interceptor
                    (i18n-interceptor system)
                    log-request-interceptor
                    #_(cond (config/demo-mode? (:env system))
                            auth/demo-auth-interceptor
                            ;; (config/dev-mode? (:env system))
                            ;; (auth/dev-auth-interceptor (-> system :env :secrets :dev-session))
                            :else (auth/auth-interceptor (-> system :env :authorization :cert-filename)
                                                         (-> system :env :authorization :known-roles)))

                    ;; dev-mode-interceptor
                    ;; query-params & form-params
                    (parameters/parameters-interceptor)
                    ;; content-negotiation
                    (muuntaja/format-negotiate-interceptor)
                    ;; encoding response body
                    (muuntaja/format-response-interceptor)
                    ;; exception handling
                    ;; exception-interceptor
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
                    (multipart/multipart-interceptor)])))

(defn with-default-pedestal-interceptors [service system]
  (update-in service [::http/interceptors] conj
             service-error-handler
             middlewares/cookies
             (pedestal.interceptor/interceptor (system-interceptor system))
             (pedestal.interceptor/interceptor (datomic-interceptor system))
             (pedestal.interceptor/interceptor (current-user-interceptor system))
             (pedestal.interceptor/interceptor (auth/session-interceptor system))))

(defn with-interceptor [service interceptor]
  (update-in service [::http/interceptors] conj interceptor))
