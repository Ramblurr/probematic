(ns app.songs.routes
  (:require
   [app.ui :as ui]
   [app.songs.views :as view]
   [ctmx.core :as ctmx]))

(defn songs-list-routes []
  (ctmx/make-routes
   "/songs"
   (fn [req]
     (ui/html5-response
      (view/songs-page req)))))

(defn songs-new-routes []
  (ctmx/make-routes
   "/songs/new"
   (fn [req]
     (ui/html5-response
      (view/song-new req "")))))

(defn song-detail-routes []
  (ctmx/make-routes
   "/song/{song/title}/"
   (fn [req]
     (ui/html5-response
      (view/song-detail req (-> req :path-params :song/title))))))

(defn songs-log-play-routes []
  (ctmx/make-routes
   "/songs/log-play/"
   (fn [req]
     (ui/html5-response
      [:div
       (ui/page-header :title "Log Play")
       (view/songs-log-play req)]))))

(defn songs-routes []
  [""
   (song-detail-routes)
   (songs-log-play-routes)
   (songs-list-routes)
   (songs-new-routes)])
