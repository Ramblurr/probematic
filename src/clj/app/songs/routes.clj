(ns app.songs.routes
  (:require
   [app.layout :as layout]
   [app.ui :as ui]
   [app.songs.views :as view]
   [ctmx.core :as ctmx]))

(defn songs-list-routes []
  (ctmx/make-routes
   "/songs"
   (fn [req]
     (layout/app-shell req
                       (view/songs-page req)))))

(defn songs-new-routes []
  (ctmx/make-routes
   "/songs/new"
   (fn [req]
     (layout/app-shell req
                       (view/song-new req)))))

(defn song-detail-routes []
  (ctmx/make-routes
   "/song/{song-id}/"
   (fn [req]
     (layout/app-shell req
                       (view/song-detail req  (parse-uuid (-> req :path-params :song-id)))))))

(defn songs-log-play-routes []
  (ctmx/make-routes
   "/songs/log-play/"
   (fn [req]
     (layout/app-shell req
                       [:div
                        (ui/page-header :title "Log Play")
                        (view/songs-log-play req)]))))

(defn songs-routes []
  ["" {:app.route/name :app/songs}
   (song-detail-routes)
   (songs-log-play-routes)
   (songs-list-routes)
   (songs-new-routes)])
