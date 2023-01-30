(ns app.urls
  (:import [java.net URLEncoder])
  (:require [app.config :as config]
            [reitit.core :as r]
            [app.debug :as debug]))

(defn link-helper
  ([prefix id-key maybe-id]
   (link-helper prefix id-key maybe-id "/"))
  ([prefix id-key maybe-id suffix]
   (let [maybe-id-id (if (map? maybe-id) (id-key maybe-id)
                         maybe-id)]
     (str prefix maybe-id-id suffix))))

(defn link-helper-new
  ([prefix id-key maybe-id]
   (link-helper prefix id-key maybe-id ""))
  ([prefix id-key maybe-id suffix]
   (let [maybe-id-id (if (map? maybe-id) (id-key maybe-id)
                         maybe-id)]
     (str prefix maybe-id-id suffix))))

(defn url-encode
  [string]
  (some-> string str (URLEncoder/encode "UTF-8") (.replace "+" "%20")))

(def link-member (partial link-helper "/member/" :member/gigo-key))

(def link-gig (partial link-helper "/gig/" :gig/gig-id))
(def link-gig-new (partial link-helper-new "/gig/" :gig/gig-id))
(def link-song (partial link-helper "/song/" :song/song-id))

(def link-policy (partial link-helper "/insurance/" :insurance.policy/policy-id))
(def link-instrument (partial link-helper "/instrument/" :instrument/instrument-id))

(defn link-gigs-home [] "/gigs/")
(defn link-songs-home [] "/songs/")
(defn link-probeplan-home [] "/probeplan")
(defn link-gig-create [] "/gigs/new/")

(defn link-file-download [path]
  (str "/nextcloud-fetch?path=" (url-encode path)))

(defn absolute-link-gig [env gig-id]
  (str (config/app-base-url env) "/gig/" gig-id))

(defn absolute-gig-answer-link-base [env]
  (str (config/app-base-url env) "/gig/answer-link"))

(defn absolute-gig-answer-link-undo [env]
  (str (config/app-base-url env) "/gig/answer-link-undo"))

(defn absolute-link-new-user-invite [env invite-code]
  (str (config/app-base-url env) "/invite-accept?code=" invite-code))

(defn link-logout [] "/logout")
(defn link-login [] "/login")

(defn throw-on-missing-match [match name]
  (if match
    match
    (throw (ex-info (format "No such route named %s" name) {:name name}))))

(defn endpoint-route
  ([router name]
   (r/match-by-name! router name))
  ([router name data]
   (r/match-by-name! router name data)))

(defn endpoint-path
  ([req name]
   (-> (-> req :system :routes :router)
       (r/match-by-name! name)
       (throw-on-missing-match name)
       (r/match->path)))
  ([req name data]
   (-> (-> req :system :routes :router)
       (r/match-by-name! name data)
       (throw-on-missing-match name)
       (r/match->path))))

(comment
  ;;
  )
