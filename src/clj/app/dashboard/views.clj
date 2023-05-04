(ns app.dashboard.views
  (:require
   [app.auth :as auth]
   [app.gigs.service :as gig.service]
   [app.gigs.views :as gig.view]
   [app.icons :as icon]
   [app.ui :as ui]
   [app.urls :as url]
   [app.util :as util]
   [ctmx.core :as ctmx]
   [ctmx.rt :as rt]
   [app.gigs.domain :as domain]))

(ctmx/defcomponent ^:endpoint gig-attendance-person-motivation [req gig-id member-id motivation]
  (gig.view/motivation-endpoint req
                                {:path path :id id :hash hash :value value}
                                (util/comp-namer #'gig-attendance-person-motivation)
                                (util/ensure-uuid! gig-id)
                                (util/ensure-uuid! member-id)
                                motivation))
(ctmx/defcomponent ^:endpoint gig-attendance-person-plan [req gig-id member-id plan]
  (gig.view/plan-endpoint req
                          {:path path :id id :hash hash :value value}
                          (util/comp-namer #'gig-attendance-person-plan)
                          (util/ensure-uuid! gig-id)
                          (util/ensure-uuid! member-id)
                          plan))
(ctmx/defcomponent ^:endpoint gig-attendance-person-comment [req gig-id member-id comment ^:boolean edit?]
  (gig.view/comment-endpoint req
                             {:path path :id id :hash hash :value value}
                             (util/comp-namer #'gig-attendance-person-comment)
                             (util/ensure-uuid! gig-id)
                             (util/ensure-uuid! member-id)
                             comment edit?))

(ctmx/defcomponent ^:endpoint gig-attendance-snooze [req gig-id member-id]
  (gig.view/snooze-endpoint req
                            {:path path :id id :hash hash :value value}
                            (util/comp-namer #'gig-attendance-snooze)
                            (util/ensure-uuid! gig-id)
                            (util/ensure-uuid! member-id)))

(defn gig-attendance-endpoint [req id idx gig]
  (let [attendance (:attendance gig)
        plan (:attendance/plan attendance)
        gig-id (:gig/gig-id gig)
        tr (:tr req)
        {:member/keys [member-id]} (:attendance/member attendance)
        {:gig/keys [date end-date status title]} gig]

    [:div {:id id :class (ui/cs "flex flex-col md:grid md:grid-cols-4 gap-x-0 md:gap-y-8 px-4 py-2 sm:px-6 last:rounded-b-md border-b border-gray-200"
                                (when (= 0 idx) "sm:rounded-t-md")
                                (if (= 0 (mod idx 2))
                                  "bg-white"
                                  "bg-white"))}
     [:div {:class "md:order-none md:col-span-1 text-sm whitespace-nowrap font-medium"}
      [:div {:class "flex gap-x-2 md:grid md:grid-flow-col md:auto-cols-min"}
       [:div {:class "hidden md:block"} (ui/gig-status-icon status)]
       [:div {:class "md:order-none md:min-w-[5rem]"}
        (if end-date
          (ui/daterange date end-date)
          (ui/datetime date))]
       [:div {:class "md:order-none "} (ui/gig-time gig)]]]
     [:div {:class "md:order-none md:font-normal"}
      [:a {:href (url/link-gig gig) :class "link-blue"} title]]
     [:div {:class "order-last md:order-none md:col-span-2 flex gap-x-2 flex-wrap"}
      [:div {:class "block md:hidden"} (ui/gig-status-icon status)]
      [:div (gig-attendance-person-plan req gig-id member-id (:attendance/plan attendance))]
      [:div (gig-attendance-person-motivation req gig-id member-id (:attendance/motivation attendance))]

      (when (domain/no-response? plan)
        [:div (gig-attendance-snooze req gig-id member-id)])

      [:div (gig-attendance-person-comment req gig-id member-id (:attendance/comment attendance) false)]]]))

(ctmx/defcomponent ^:endpoint gig-attendance-upcoming [req idx gig]
  gig-attendance-person-comment
  gig-attendance-person-motivation
  gig-attendance-person-plan
  gig-attendance-snooze
  (gig-attendance-endpoint req id idx gig))

(ctmx/defcomponent ^:endpoint gig-attendance-unanswered [req idx gig]
  gig-attendance-person-comment
  gig-attendance-person-motivation
  gig-attendance-person-plan
  (gig-attendance-endpoint req id idx gig))

(ctmx/defcomponent ^:endpoint dashboard-page [{:keys [db tr] :as req}]
  (let [member (auth/get-current-member req)
        gigs-planned (gig.service/gigs-planned-for db member)
        need-answer-gigs (gig.service/gigs-needing-plan db member)]
    [:div
     (ui/page-header :title (tr [(keyword "dashboard" (name (util/time-window (util/local-time-austria!))))] [(ui/member-nick member)])
                     :buttons  (list
                                (ui/button :tag :a :label (tr [:action/create-gig])
                                           :priority :primary
                                           :centered? true
                                           :attr {:hx-boost "true" :href (url/link-gig-create)} :icon icon/plus)))

     (when (seq need-answer-gigs)
       [:div {:class "mt-6 sm:px-6 lg:px-8" :hx-boost "true"}
        (ui/divider-left (tr [:dashboard/unanswered]))
        (rt/map-indexed gig-attendance-unanswered req need-answer-gigs)])

     (when (seq gigs-planned)
       [:div {:class "mt-6 sm:px-6 lg:px-8" :hx-boost "true"}
        (ui/divider-left (tr [:dashboard/upcoming]))
        (rt/map-indexed gig-attendance-upcoming req gigs-planned)])
     (when-not (or (seq gigs-planned) (seq need-answer-gigs))
       [:div {:class "mt-6 sm:px-6 lg:px-8"}
        "You have no upcoming gigs or probes. Why don't you create one?"])]))
