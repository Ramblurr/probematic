(ns app.gigo
  (:require [app.gigo.core :as gigo]))

(defn members-from-attendance
  "Given an attendance response from get-gig-attendance!, returns a map of member_names -> member_key"
  [attendance]
  (gigo/members-from-attendance attendance))

(defn get-next-gig!
  "Gets the next gig, with attendance info."
  [config]
  (gigo/get-next-gig! config))

(defn update-cache!
  "Update the gigo cache (fetches all members and gigs)"
  [config]
  (gigo/update-cache! config))

(defn wednesday-probe? [g]
  (gigo/wednesday-probe? g))

(defn non-wednesday-probe? [g]
  (gigo/non-wednesday-probe? g))

(defn probe? [g]
  (gigo/probe? g))

(defn meeting? [g]
  (gigo/meeting? g))

(def gigs-cache gigo/gigs-cache)
