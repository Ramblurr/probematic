(ns app.insurance.routes
  (:require
   [app.layout :as layout]
   [app.insurance.views :as view]
   [ctmx.core :as ctmx]
   [datomic.client.api :as d]
   [app.insurance.controller :as controller]))

(defn insurance-detail []
  (ctmx/make-routes
   "/insurance-policy/{policy-id}/"
   (fn [req]
     (layout/app-shell req
                       (view/insurance-detail-page req)))))

(defn insurance-coverage-detail []
  (ctmx/make-routes
   "/insurance-coverage/{coverage-id}/"
   (fn [req]
     (layout/app-shell req
                       (view/insurance-coverage-detail-page req)))))
(defn insurance-coverage-detail-edit []
  (ctmx/make-routes
   "/insurance-coverage-edit/{coverage-id}/"
   (fn [req]
     (layout/app-shell req
                       (view/insurance-coverage-detail-page-rw req)))))

(defn insurance-coverage-create []
  (ctmx/make-routes
   "/insurance-coverage-create/{policy-id}/"
   (fn [req]
     (layout/app-shell req
                       (view/insurance-coverage-create-page req)))))
(defn insurance-coverage-create2 []
  (ctmx/make-routes
   "/insurance-coverage-create2/{policy-id}/{instrument-id}"
   (fn [req]
     (layout/app-shell req
                       (view/image-upload req)))))
(defn insurance-coverage-create3 []
  (ctmx/make-routes
   "/insurance-coverage-create3/{policy-id}/{instrument-id}"
   (fn [req]
     (layout/app-shell req
                       (view/insurance-coverage-create-page3 req)))))

(defn instrument-detail []
  (ctmx/make-routes
   "/instrument/{instrument-id}/"
   (fn [req]
     (layout/app-shell req
                       (view/instrument-detail-page req false)))))

(defn insurance-create []
  (ctmx/make-routes
   "/insurance-new/"
   (fn [req]
     (layout/app-shell req
                       (view/insurance-create-page req)))))

(defn insurance-index []
  (ctmx/make-routes
   "/insurance"
   (fn [req]
     (layout/app-shell req
                       (view/insurance-index-page req)))))

(defn instrument-create []
  (ctmx/make-routes
   "/instrument-new/"
   (fn [req]
     (layout/app-shell req
                       (view/instrument-create-page req)))))

(def policy-interceptor {:name ::insurance-policy--interceptor
                         :enter (fn [ctx]
                                  (let [conn (-> ctx :request :datomic-conn)
                                        db (d/db conn)
                                        policy-id (-> ctx :request :path-params :policy-id)]
                                    (cond-> ctx
                                      policy-id (assoc-in  [:request :policy] (controller/retrieve-policy db (parse-uuid policy-id))))))})
(def coverage-interceptor {:name ::insurance-coverage--interceptor
                           :enter (fn [ctx]
                                    (let [coverage-id (-> ctx :request :path-params :coverage-id)]
                                      (if-let  [coverage (controller/retrieve-coverage (-> ctx :request :datomic-conn d/db) (parse-uuid coverage-id))]
                                        (assoc-in ctx  [:request :coverage] coverage)
                                        (throw (ex-info "Instrument Coverage not found" {:app/error-type :app.error.type/not-found
                                                                                         :instrument.coverage/coverage-id coverage-id})))))})

(def instrument-interceptor {:name ::instrument--interceptor
                             :enter (fn [ctx]
                                      (let [conn (-> ctx :request :datomic-conn)
                                            db (d/db conn)
                                            instrument-id (-> ctx :request :path-params :instrument-id)]
                                        (cond-> ctx
                                          instrument-id (assoc-in  [:request :instrument] (controller/retrieve-instrument db (parse-uuid instrument-id))))))})

(defn routes []
  [""
   ["" {:app.route/name :app/insurance1
        :interceptors [policy-interceptor]}
    (insurance-coverage-create)
    (insurance-coverage-create2)
    (insurance-coverage-create3)]
   ["" {:app.route/name :app/insurance2
        :interceptors [policy-interceptor instrument-interceptor]}
    ;; (ctmx/make-routes
    ;;  "/insurance-policy-duplicate/"
    ;;  (fn [req]
    ;;    (view/insurance-policy-duplicate req)))
    (insurance-index)
    ["" {:app.route/name :app/instrument.coverage
         :interceptors [coverage-interceptor]}
     (insurance-coverage-detail)
     (insurance-coverage-detail-edit)]
    (insurance-create)
    (insurance-detail)
    (instrument-detail)
    (instrument-create)]])
