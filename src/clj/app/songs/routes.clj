(ns app.songs.routes
  (:require
   [app.layout :as layout]
   [app.songs.views :as view]
   [ctmx.core :as ctmx]
   [reitit.ring.malli :as reitit.ring.malli]))

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

(defn routes []
  ["" {:app.route/name :app/songs}
   (songs-sync)
   (song-detail-routes)
   (songs-list-routes)
   (songs-new-routes)
   ["/song-media/{song-id}"
    {:post {:summary "Upload media for an song"
            :parameters {:multipart [:map [:file reitit.ring.malli/temp-file-part]]
                         :path [:map [:song-id :uuid]]}
            :handler (fn [req] (view/image-upload-handler req))}}]])

(defn unauthenticated-routes []
  [""
   ["/song-media/{song-id}/{filename}"
    {:get {:summary "Get song media"
           :parameters {:path [:map
                               [:song-id :uuid]
                               [:filename :string]]}
           :handler (fn [req]
                      (view/image-fetch-handler req))}}]])
