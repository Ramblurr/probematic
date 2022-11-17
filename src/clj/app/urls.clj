(ns app.urls)

(defn link-helper
  ([prefix id-key maybe-id]
   (link-helper prefix id-key maybe-id "/"))
  ([prefix id-key maybe-id suffix]
   (let [maybe-id-id (if (map? maybe-id) (id-key maybe-id)
                         maybe-id)]
     (str prefix maybe-id-id suffix))))

(def link-member (partial link-helper "/member/" :member/gigo-key))

(def link-gig (partial link-helper "/event/" :gig/gig-id))

(def link-policy (partial link-helper "/insurance/" :insurance.policy/policy-id))
(def link-instrument (partial link-helper "/instrument/" :instrument/instrument-id))

(defn link-gigs-home [] "/events")
