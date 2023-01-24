(ns app.gigs.domain
  (:require
   [medley.core :as m]
   [tick.core :as t]
   [app.schemas :as s]
   [malli.util :as mu]
   [app.debug :as debug]))

(def statuses [:gig.status/unconfirmed
               :gig.status/confirmed
               :gig.status/cancelled])
(def create-statuses [:gig.status/unconfirmed
                      :gig.status/confirmed])

(def plans [:plan/no-response
            :plan/definitely
            :plan/probably
            :plan/unknown
            :plan/probably-not
            :plan/definitely-not
            :plan/not-interested])

(def motivations [:motivation/none
                  :motivation/very-high
                  :motivation/high
                  :motivation/medium
                  :motivation/low
                  :motivation/very-low])
(def gig-types [:gig.type/probe :gig.type/extra-probe :gig.type/meeting :gig.type/gig])

(def setlist-versions [:setlist.version/v1])

(def SetlistV1Entity
  (s/schema
   [:map {:name :app.entity/setlist.v1}
    [:setlist/gig ::s/datomic-ref]
    [:setlist/version (s/enum-from setlist-versions)]
    [:setlist.v1/ordered-songs [:sequential [:tuple ::s/datomic-ref :int]]]]))

(def GigEntity
  (s/schema
   [:map {:name :app.entity/gig}
    [:gig/gig-id ::s/non-blank-string]
    [:gig/title {:max 4096} ::s/non-blank-string]
    [:gig/status (s/enum-from statuses)]
    [:gig/date ::s/instdate]
    [:gig/end-date {:optional true} ::s/instdate]
    [:gig/gig-type (s/enum-from gig-types)]
    [:gig/location {:optional true :max 4096} ::s/non-blank-string]
    [:gig/contact {:optional true} ::s/datomic-ref]
    [:gig/call-time {:optional true} ::s/minute-time]
    [:gig/set-time {:optional true} ::s/minute-time]
    [:gig/end-time {:optional true} ::s/time]
    [:gig/leader {:optional true :max 4096} :string]
    [:gig/pay-deal {:optional true :max 4096} :string]
    [:gig/outfit {:optional true :max 4096} :string]
    [:gig/more-details {:optional true :max 4096} :string]
    [:gig/setlist {:optional true :max 4096} :string]
    [:gig/description {:optional true :max 4096} :string]
    [:gig/post-gig-plans {:optional true :max 4096} :string]
    [:gig/gigo-plan-archive  {:optional true :max 4096} :string]]))

(clojure.core/comment
  (def _gig {:gig/gig-id "A" :gig/title "Title" :gig/status :gig.status/cancelled :gig/date (t/date) :gig/gig-type :gig.type/probe
             :gig/location "PR" :gig/call-time (t/time "03:00") :gig/set-time (t/time "14:23")
             :gig/contact [:member/gigo-key "Foobar"]})

  (s/valid? GigEntity _gig)
  (s/explain-human GigEntity _gig)
  (s/decode-datomic GigEntity
                    (s/encode-datomic GigEntity _gig))
  ;;
  )
(defn gig->db [gig]
  (when-not (s/valid? GigEntity gig)
    (throw
     (ex-info "Gig not valid" {:gig gig
                               :schema GigEntity
                               :error (s/explain GigEntity gig)
                               :human (s/explain-human GigEntity gig)})))
  (s/encode-datomic GigEntity gig))

(defn ->comment [comment]
  (-> comment
      (m/update-existing :comment/created-at t/date-time)))

(defn db->gig [gig]
  (-> (s/decode-datomic GigEntity gig)
      (m/update-existing :gig/comments #(->> %
                                             (map ->comment)
                                             (sort-by :comment/created-at)))))

(defn in-future? [{:gig/keys [date]}]
  (t/>= (t/date) date))

(defn gig-archived? [{:gig/keys [date]}]
  (when date
    (t/< date (t/<< (t/date) (t/new-period 14 :days)))))
