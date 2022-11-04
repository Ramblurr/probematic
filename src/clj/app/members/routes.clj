(ns app.members.routes
  (:require
   [app.render :as render]
   [app.members.views :as view]
   [ctmx.core :as ctmx]))

(defn members-index []
  (ctmx/make-routes
   "/members"
   (fn [req]
     (render/html5-response
      (view/members-index-page req)))))

(defn members-routes []
  [""
   (members-index)])
