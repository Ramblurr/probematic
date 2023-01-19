(ns app.urls
  (:import [java.net URLEncoder])
  (:require [app.config :as config]))

(defn link-helper
  ([prefix id-key maybe-id]
   (link-helper prefix id-key maybe-id "/"))
  ([prefix id-key maybe-id suffix]
   (let [maybe-id-id (if (map? maybe-id) (id-key maybe-id)
                         maybe-id)]
     (str prefix maybe-id-id suffix))))

(defn url-encode
  [string]
  (some-> string str (URLEncoder/encode "UTF-8") (.replace "+" "%20")))

(def link-member (partial link-helper "/member/" :member/gigo-key))

(def link-gig (partial link-helper "/gig/" :gig/gig-id))
(def link-song (partial link-helper "/song/" :song/song-id))

(def link-policy (partial link-helper "/insurance/" :insurance.policy/policy-id))
(def link-instrument (partial link-helper "/instrument/" :instrument/instrument-id))

(defn link-gigs-home [] "/gigs/")
(defn link-probeplan-home [] "/probeplan")
(defn link-gig-create [] "/gigs/new/")

(defn link-file-download [path]
  (str "/nextcloud-fetch?path=" (url-encode path)))

(defn absolute-link-gig [env gig-id]
  (str (config/app-base-url env) "/gig/" gig-id))

(defn absolute-gig-answer-link-base [env]
  (str (config/app-base-url env) "/gig/answer-link"))

(comment
  ;;
  )
