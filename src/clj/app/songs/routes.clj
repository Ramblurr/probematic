(ns app.songs.routes
  (:require
   [app.layout :as layout]
   [app.songs.views :as view]
   [app.ui :as ui]
   [ctmx.core :as ctmx]))

(defn songs-sync []
  ["/songs-sync" {:app.route/name :app/songs-sync
                  :post (fn [req]
                          (view/songs-sync req))}])

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
                       (view/song-detail-page req false)))))

(defn songs-log-play-routes []
  (ctmx/make-routes
   "/songs/log-play/"
   (fn [req]
     (layout/app-shell req
                       [:div
                        (ui/page-header :title "Log Play")
                        (view/songs-log-play req)]))))

(defn routes []
  ["" {:app.route/name :app/songs}
   (songs-sync)
   (song-detail-routes)
   (songs-log-play-routes)
   (songs-list-routes)
   (songs-new-routes)])
