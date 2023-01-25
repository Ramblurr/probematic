(ns app.members.routes
  (:require
   [app.queries :as q]
   [app.layout :as layout]
   [app.members.views :as view]
   [app.members.controller :as controller]
   [datomic.client.api :as d]
   [ctmx.core :as ctmx]))

(defn members-detail []
  (ctmx/make-routes
   "/member/{gigo-key}"
   (fn [req]
     (layout/app-shell req
                       (view/members-detail-page req)))))

(defn members-index []
  (ctmx/make-routes
   "/members"
   (fn [req]
     (layout/app-shell req
                       (view/members-index-page req false)))))

(def members-interceptors [{:name ::members--interceptor
                            :enter (fn [ctx]
                                     (let [conn (-> ctx :request :datomic-conn)
                                           db (d/db conn)
                                           member-gigo-key (-> ctx :request :path-params :gigo-key)]
                                       (cond-> ctx
                                         member-gigo-key (assoc-in [:request :member] (q/retrieve-member db member-gigo-key))
                                         true (assoc-in [:request :members] (controller/members db)))))}])

(defn members-routes []
  ["" {:interceptors  members-interceptors
       :app.route/name :app/members}
   (members-detail)
   (members-index)])
