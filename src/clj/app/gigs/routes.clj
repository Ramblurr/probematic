(ns app.gigs.routes
  (:require
   [app.gigs.views :as view]
   [app.layout :as layout]
   [app.queries :as q]
   [app.util.http :as http.util]
   [ctmx.core :as ctmx]
   [datomic.client.api :as d]))

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
                                       _ (assert conn "datomic connection not available")
                                       db (d/db conn)
                                       is-answer-link-workaround? (= "answer-link" (http.util/path-param (:request ctx) :gig/gig-id))]
                                   (if is-answer-link-workaround?
                                     ;;  tmp workaround, will remove later
                                     ctx
                                     (let [gig-id (http.util/path-param-uuid! (:request ctx) :gig/gig-id)
                                           gig (q/retrieve-gig db gig-id)]
                                       (if gig
                                         (assoc-in ctx [:request :gig] gig)
                                         (throw (ex-info "Gig not found" {:app/error-type :app.error.type/not-found
                                                                          :gig/gig-id gig-id})))))))})

(defn gigs-list-route []
  (ctmx/make-routes
   ""
   (fn [req]
     (layout/app-shell req
                       (view/gigs-list-page req)))))
(defn gigs-archive-route []
  (ctmx/make-routes
   "/archive"
   (fn [req]
     (layout/app-shell req
                       (view/gigs-archive-page req)))))

(defn gig-detail-route []
  (ctmx/make-routes
   "/{gig/gig-id}/"
   (fn [req]
     (if (= "answer-link" (http.util/path-param req :gig/gig-id))
       (view/gig-answer-link req)
       (layout/app-shell req (view/gig-detail-page req false))))))

(defn routes []
  ["" {:app.route/name :app/gigs}
   ["/gigs"
    (gig-create-route)
    (gigs-list-route)
    (gigs-archive-route)]
   ["/gig" {:interceptors [gigs-interceptors]}
    (gig-detail-route)
    (gig-log-play-route)]])

(defn unauthenticated-routes []
  [""
   ["/answer-link" {:app.route/name :app/gig-answer-link
                    :handler  (fn [req]
                                (view/gig-answer-link req))}]
   ["/answer-link/" {:app.route/name :app/gig-answer-link2
                     :handler  (fn [req]
                                 (view/gig-answer-link req))}]])
