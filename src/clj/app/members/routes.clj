(ns app.members.routes
  (:require
   [app.render :as render]
   [app.members.views :as view]
   [app.members.controller :as controller]
   [datomic.client.api :as d]
   [ctmx.core :as ctmx]))

(defn members-index []
  (ctmx/make-routes
   "/members"
   (fn [req]
     (render/html5-response
      (view/members-index-page req)))))

(def members-interceptors [{:name ::members--interceptor
                            :enter (fn [ctx]
                                     (let [conn (-> ctx :request :datomic-conn)
                                           db (d/db conn)]
                                       (assoc-in ctx [:request :members] (controller/members db))))}])

(defn members-routes []
  ["" {:interceptors  members-interceptors}
   (members-index)])
