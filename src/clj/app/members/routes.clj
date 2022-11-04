(ns app.members.routes
  (:require
   [app.render :as render]
   [app.members.views :as view]
   [app.members.controller :as controller]
   [datomic.client.api :as d]
   [ctmx.core :as ctmx]))

(defn members-detail []
  (ctmx/make-routes
   "/member/{gigo-key}"
   (fn [req]
     (render/html5-response
      (view/members-detail-page req)))))

(defn members-index []
  (ctmx/make-routes
   "/members"
   (fn [req]
     (render/html5-response
      (view/members-index-page req)))))

(def members-interceptors [{:name ::members--interceptor
                            :enter (fn [ctx]
                                     (let [conn (-> ctx :request :datomic-conn)
                                           db (d/db conn)
                                           member-gigo-key (-> ctx :request :path-params :gigo-key)]
                                       (cond-> ctx
                                         member-gigo-key (assoc-in [:request :member] (controller/retrieve-member db member-gigo-key))
                                         true (assoc-in [:request :members] (controller/members db)))))}])

(defn members-routes []
  ["" {:interceptors  members-interceptors}
   (members-detail)
   (members-index)])
