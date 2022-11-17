(ns app.dashboard.views
  (:require [app.ui :as ui]
            [app.gigs.views :as gig.view]
            [app.gigs.controller :as gig.controller]
            [app.i18n :as i18n]
            [app.icons :as icon]
            [ctmx.core :as ctmx]
            [ctmx.rt :as rt]
            [app.auth :as auth]
            [app.util :as util]
            [app.urls :as url]
            [tick.core :as t]))

(ctmx/defcomponent ^:endpoint gig-attendance-person-motivation [req gig-id gigo-key motivation]
  (gig.view/motivation-endpoint req
                                {:path path :id id :hash hash :value value}
                                (util/comp-namer #'gig-attendance-person-motivation)
                                gig-id
                                gigo-key
                                motivation))
(ctmx/defcomponent ^:endpoint gig-attendance-person-plan [req gig-id gigo-key plan]
  (gig.view/plan-endpoint req
                          {:path path :id id :hash hash :value value}
                          (util/comp-namer #'gig-attendance-person-plan)
                          gig-id
                          gigo-key
                          plan))
(ctmx/defcomponent ^:endpoint gig-attendance-person-comment [req gig-id gigo-key comment ^:boolean edit?]
  (gig.view/comment-endpoint req
                             {:path path :id id :hash hash :value value}
                             (util/comp-namer #'gig-attendance-person-comment)
                             gig-id gigo-key comment edit?))

(ctmx/defcomponent ^:endpoint gig-attendance [{:keys [db] :as req} idx gig]
  (let [attendance (:attendance gig)
        gig-id (:gig/gig-id gig)
        {:member/keys [gigo-key]} (:attendance/member attendance)]
    [:div {:id id :class (ui/cs "flex flex-col md:grid md:grid-cols-4 gap-x-0 md:gap-y-8 px-4 py-2 sm:px-6 last:rounded-b-md border-b border-gray-200"
                                (when (= 0 idx) "sm:rounded-t-md")
                                (if (= 0 (mod idx 2))
                                  "bg-white"
                                  "bg-white"))}
     [:div {:class "order-2 md:order-none md:col-span-1 text-sm whitespace-nowrap"}
      [:div {:class "flex gap-x-2 md:grid md:grid-flow-col md:auto-cols-min"}
       [:div {:class "hidden md:block"} (ui/gig-status-icon (:gig/status gig))]
       [:div {:class "md:order-none md:min-w-[5rem]"} (ui/gig-date gig)]
       [:div {:class "md:order-none "} (ui/gig-time gig)]]]
     [:div {:class "order-1 md:order-none font-bold md:font-normal"}
      [:a {:href (url/link-gig gig) :class "link-blue"} (:gig/title gig)]]
     [:div {:class "order-last md:order-none md:col-span-2 flex gap-x-2"}
      [:div {:class "block md:hidden"} (ui/gig-status-icon (:gig/status gig))]
      [:div (gig-attendance-person-plan req gig-id gigo-key (:attendance/plan attendance))]
      [:div (gig-attendance-person-motivation req gig-id gigo-key (:attendance/motivation attendance))]
      [:div (gig-attendance-person-comment req gig-id gigo-key (:attendance/comment attendance) false)]]]))

(ctmx/defcomponent ^:endpoint dashboard-page [{:keys [db] :as req}]
  (let [member (auth/get-current-member req)
        gigs-planned (gig.controller/gigs-planned-for db member)
        need-answer-gigs (gig.controller/gigs-needing-plan db member)
        offset 0
        limit 10
        ;; past-gigs (controller/gigs-past-two-weeks db)
        tr (i18n/tr-from-req req)]
    [:div
     (ui/page-header :title (tr [(keyword "dashboard" (name (util/time-window (util/local-time-austria!))))] [(ui/member-nick member)])
                     :buttons  (list
                                (when false ;; TODO
                                  (ui/button :label (tr [:action/create])
                                             :priority :primary
                                             :centered? true
                                             :attr {:href "/events/new"} :icon icon/plus))))

     (when (seq need-answer-gigs)
       [:div {:class "mt-6 sm:px-6 lg:px-8"}
        (ui/divider-left (tr [:dashboard/unanswered]))
        (rt/map-indexed gig-attendance req need-answer-gigs)])

     (when (seq gigs-planned)
       [:div {:class "mt-6 sm:px-6 lg:px-8"}
        (ui/divider-left (tr [:dashboard/upcoming]))
        (rt/map-indexed gig-attendance req gigs-planned)])
     (when-not (or (seq gigs-planned) (seq need-answer-gigs))
       [:div {:class "mt-6 sm:px-6 lg:px-8"}
        "You have no upcoming gigs or probes. Why don't you create one?"])]))
