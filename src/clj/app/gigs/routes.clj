(ns app.gigs.routes
  (:require
   [app.ui :as ui]
   [app.gigs.views :as view]
   [datomic.client.api :as d]
   [ctmx.core :as ctmx]
   [app.gigs.controller :as controller]))

(defn gigs-list-route []
  (ctmx/make-routes
   "/events"
   (fn [req]
     (ui/html5-response
      (view/gigs-list-page req)))))

(defn gig-detail-route []
  (ctmx/make-routes
   "/event/{gig/gig-id}/"
   (fn [req]
     (ui/html5-response (view/gigs-detail-page req)))))

(defn gig-log-play-route []
  (ctmx/make-routes
   "/event/{gig/gig-id}/log-play"
   (fn [req]
     (ui/html5-response
      (view/gig-log-plays req)))))

(def gigs-interceptors [{:name ::gigs--interceptor
                         :enter (fn [ctx]
                                  (let [conn (-> ctx :request :datomic-conn)
                                        db (d/db conn)
                                        gig-id (-> ctx :request :path-params :gig/gig-id)]
                                    (cond-> ctx
                                      gig-id (assoc-in [:request :gig] (controller/retrieve-gig db gig-id)))))}])

(defn events-routes []
  ["" {:interceptors gigs-interceptors}
   (gigs-list-route)
   (gig-detail-route)
   (gig-log-play-route)])
