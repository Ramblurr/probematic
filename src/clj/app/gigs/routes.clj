(ns app.gigs.routes
  (:require
   [app.gigs.views :as view]
   [app.layout :as layout]
   [app.queries :as q]
   [ctmx.core :as ctmx]
   [datomic.client.api :as d]))

(defn gigs-list-route []
  (ctmx/make-routes
   ""
   (fn [req]
     (layout/app-shell req
                       (view/gigs-list-page req)))))

(defn gig-detail-route []
  (ctmx/make-routes
   "/{gig/gig-id}/"
   (fn [req]
     (layout/app-shell req (view/gigs-detail-page req false)))))

(defn gig-create-route []
  (ctmx/make-routes
   "/new"
   (fn [req]
     (layout/app-shell req (view/gig-create-page req)))))

(defn gig-log-play-route []
  (ctmx/make-routes
   "/{gig/gig-id}/log-play"
   (fn [req]
     (layout/app-shell req
                       (view/gig-log-plays req)))))

(def gigs-interceptors {:name ::gigs--interceptor
                        :enter (fn [ctx]
                                 (let [conn (-> ctx :request :datomic-conn)
                                       db (d/db conn)
                                       gig-id (-> ctx :request :path-params :gig/gig-id)]
                                   (cond-> ctx
                                     gig-id (assoc-in [:request :gig] (q/retrieve-gig db gig-id)))))})

(defn events-routes []
  ["" {:app.route/name :app/gigs}
   ["/gigs"
    (gig-create-route)
    (gigs-list-route)]
   ["/gig" {:interceptors [gigs-interceptors]}
    (gig-detail-route)
    (gig-log-play-route)]])

(defn gigs-list-route []
  (ctmx/make-routes
   ""
   (fn [req]
     (layout/app-shell req
                       (view/gigs-list-page req)))))

(defn gig-detail-route []
  (ctmx/make-routes
   "/{gig/gig-id}/"
   (fn [req]
     (layout/app-shell req (view/gigs-detail-page req false)))))

(defn events-routes []
  ["" {:app.route/name :app/gigs}
   ["/gigs"
    (gig-create-route)
    (gigs-list-route)]
   ["/gig" {:interceptors [gigs-interceptors]}
    (gig-detail-route)
    (gig-log-play-route)]])
