(ns app.insurance.routes
  (:require
   [app.ui :as ui]
   [app.insurance.views :as view]
   [ctmx.core :as ctmx]
   [datomic.client.api :as d]
   [app.insurance.controller :as controller]))

(defn insurance-detail []
  (ctmx/make-routes
   "/insurance/{policy-id}/"
   (fn [req]
     (ui/app-shell req
                   (view/insurance-detail-page req)))))

(defn instrument-detail []
  (ctmx/make-routes
   "/instrument/{instrument-id}/"
   (fn [req]
     (ui/app-shell req
                   (view/instrument-detail-page req)))))

(defn insurance-create []
  (ctmx/make-routes
   "/insurance-new/"
   (fn [req]
     (ui/app-shell req
                   (view/insurance-create-page req)))))

(defn insurance-index []
  (ctmx/make-routes
   "/insurance"
   (fn [req]
     (ui/app-shell req
                   (view/insurance-index-page req)))))

(defn instrument-create []
  (ctmx/make-routes
   "/instrument-new/"
   (fn [req]
     (ui/app-shell req
                   (view/instrument-create-page req)))))

(defn insurance-routes []
  [""
   (ctmx/make-routes
    "/insurance-policy-duplicate/"
    (fn [req]
      (view/insurance-policy-duplicate req)))
   (insurance-index)
   (insurance-create)
   (insurance-detail)
   (instrument-detail)
   (instrument-create)])

(def insurance-interceptors [{:name ::insurance-policy--interceptor
                              :enter (fn [ctx]
                                       (let [conn (-> ctx :request :datomic-conn)
                                             db (d/db conn)
                                             policy-id (-> ctx :request :path-params :policy-id)
                                             instrument-id (-> ctx :request :path-params :instrument-id)]
                                         (cond-> ctx
                                           policy-id (assoc-in  [:request :policy] (controller/retrieve-policy db (parse-uuid policy-id)))
                                           instrument-id (assoc-in  [:request :instrument] (controller/retrieve-instrument db (parse-uuid instrument-id))))))}])
