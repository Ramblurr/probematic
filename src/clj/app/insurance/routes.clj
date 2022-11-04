(ns app.insurance.routes
  (:require
   [app.render :as render]
   [app.insurance.views :as view]
   [ctmx.core :as ctmx]
   [datomic.client.api :as d]
   [app.insurance.controller :as controller]))

(defn insurance-detail []
  (ctmx/make-routes
   "/insurance/{policy-id}/"
   (fn [req]
     (render/html5-response
      (view/insurance-detail-page req)))))

(defn instrument-detail []
  (ctmx/make-routes
   "/instrument/{instrument-id}/"
   (fn [req]
     (render/html5-response
      (view/instrument-detail-page req)))))

(defn insurance-create []
  (ctmx/make-routes
   "/insurance-new/"
   (fn [req]
     (render/html5-response
      (view/insurance-create-page req)))))

(defn insurance-index []
  (ctmx/make-routes
   "/insurance"
   (fn [req]
     (render/html5-response
      (view/insurance-index-page req)))))

(defn instrument-create []
  (ctmx/make-routes
   "/instrument-new/"
   (fn [req]
     (render/html5-response
      (view/instrument-create-page req)))))

(defn insurance-routes []
  [""
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
