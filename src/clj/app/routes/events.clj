(ns app.routes.events
  (:require
   [app.render :as render]
   [app.views.events :as view]
   [ctmx.core :as ctmx]))

(defn events-list-route []
  (ctmx/make-routes
   "/events"
   (fn [req]
     (render/html5-response
      (view/events-list-page req)))))

(defn event-detail-route []
  (ctmx/make-routes
   "/event/{gig/id}/"
   (fn [req]
     (render/html5-response nil))))

(defn event-log-play-route []
  (ctmx/make-routes
   "/event/{gig/id}/log-play"
   (fn [req]
     (render/html5-response
      (view/event-log-play req)))))

(defn events-routes []
  [""
   (events-list-route)
   (event-detail-route)
   (event-log-play-route)])
