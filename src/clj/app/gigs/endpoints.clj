(ns app.gigs.endpoints
  (:require
   [app.gigs.service :as service]
   [app.gigs.views2 :as view]
   [app.layout :as layout]
   [app.queries :as q]
   [app.render :as render]
   [app.urls :as url]
   [app.util :as util]
   [ctmx.core :as ctmx]
   [ctmx.response :as response]))

(defn maybe-partial [req partial]
  (if (:htmx? req)
    (render/snippet-response partial)
    (layout/app-shell req partial)))

(ctmx/defcomponent ^:endpoint gig-delete [{:keys [db] :as req}]
  (when
   (util/delete? req)
    (service/delete-gig! req)
    (response/hx-redirect (url/link-gigs-home))))

(ctmx/defcomponent ^:endpoint  gig-details-get [req]

  #_(gigs-detail-page-info-ro req (:gig req)))

(defn gig-detail-page [{:tempura/keys [tr] :keys [db] :as req}]
  (maybe-partial req (view/gig-detail-page req false)))

(defn gig-edit-form [req]
  (render/snippet-response
   (view/gig-edit-form req (q/retrieve-gig (:db req) (-> req :params :gig-id)))))

(defn log-plays [req]
  (maybe-partial req (view/log-plays req)))

(defn log-plays-post [req]
  (let [gig-id (-> req :path-params :gig/gig-id)]
    (service/log-play! req gig-id)
    (response/hx-redirect (url/link-gig gig-id))))

(defn setlist-select-songs-form [req]
  (render/snippet-response
   (view/setlist-choose-songs-form req)))

(defn setlist-select-songs-put [req]
  (tap> {:p (:params req)})
  (render/snippet-response
   (view/setlist-sort-form req)))

(defn setlist-order-songs-post [{:keys [db] :as req}]
  (let [song-ids (util/ensure-coll (-> req util/json-params :song-ids))
        song-ids (map util/ensure-uuid song-ids)
        ;; _ (tap> {:p (:params req) :un (-> req util/json-params) :song-ids song-ids})
        songs (util/index-sort-by song-ids :song/song-id (service/update-setlist! req song-ids))]

    (render/snippet-response
     (view/gig-detail-page-setlist req song-ids))))
