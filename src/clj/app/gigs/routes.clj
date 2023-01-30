(ns app.gigs.routes
  (:require
   [app.gigs.views :as view]
   [app.gigs.endpoints :as endpoint]
   [app.layout :as layout]
   [app.queries :as q]
   [ctmx.core :as ctmx]
   [datomic.client.api :as d]
   [app.urls :as url]
   [app.debug :as debug]))

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
     (layout/app-shell req (view/gig-detail-page req false)))))

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

(defn gigs-list-route []
  (ctmx/make-routes
   ""
   (fn [req]
     (layout/app-shell req
                       (view/gigs-list-page req)))))
(def gigs-interceptors {:name ::gigs--interceptor
                        :enter (fn [ctx]
                                 (let [conn (-> ctx :request :datomic-conn)
                                       db (d/db conn)
                                       gig-id (-> ctx :request :path-params :gig/gig-id)]
                                   (cond-> ctx
                                     gig-id (assoc-in [:request :gig] (q/retrieve-gig db gig-id)))))})

(defn gig-detail-route []
  (ctmx/make-routes
   "/{gig/gig-id}/"
   (fn [req]
     (layout/app-shell req (view/gig-detail-page req false)))))

(defn gigs-routes []
  ["" {:app.route/name :app/gigs}
   ["/gigs"
    (gig-create-route)
    (gigs-list-route)]
   ["/gig" {:interceptors [gigs-interceptors]}
    (gig-detail-route)
    (gig-log-play-route)]])

(defn gigs-routes2 []
  ["" {:app.route/name :app/gigs}
   ["/new/gigs"
    (gig-create-route)
    (gigs-list-route)]
   ["/new/gig" {:interceptors [gigs-interceptors]}
    ["/{gig/gig-id}"
     ["" {:get (fn [req]
                 (endpoint/gig-detail-page req))
          :name ::details-page}]
     ["/log-plays"
      {:name ::log-plays
       :get (fn [req]
              (endpoint/log-plays req))
       :post (fn [req]
               (endpoint/log-plays-post req))}]
     ["/setlist-choose-songs" {:name ::setlist-select-songs
                               :get (fn [req]
                                      (endpoint/setlist-select-songs-form req))
                               :post (fn [req]
                                       (endpoint/setlist-select-songs-form req))
                               :put (fn [req]
                                      (endpoint/setlist-select-songs-put req))}]
     ["/setlist-order-songs" {:name ::setlist-order-songs
                              :post (fn [req] (endpoint/setlist-order-songs-post req))}]]]

   ["/new/gig-edit-form" {:get (fn [req]
                                 (endpoint/gig-edit-form req))
                          :name ::details-edit-form}]])

(defn gig-answer-link-route []
  ["/answer-link" {:app.route/name :app/gig-answer-link
                   :handler  (fn [req]

                               #_(view/gig-answer-link req))}])
