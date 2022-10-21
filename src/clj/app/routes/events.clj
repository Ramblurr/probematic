(ns app.routes.events
  (:require
   [app.render :as render]
   [app.views.events :as view]
   [ctmx.core :as ctmx]))

(defn events-list-routes []
  (ctmx/make-routes
   "/events"
   (fn [req]
     (render/html5-response
      (view/events-list-page req)))))

(defn events-routes []
  [""
   (events-list-routes)])
