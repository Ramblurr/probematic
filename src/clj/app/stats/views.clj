(ns app.stats.views
  (:require
   [app.render :as render]
   [app.stats.controller :as controller]
   [app.ui :as ui]
   [app.util.http :as util.http]
   [ctmx.core :as ctmx]
   [hiccup.util :as hiccup.util]
   [jsonista.core :as j]
   [tick.core :as t]))

(defn stat-block [caption value]
  [:div {:class "flex items-baseline flex-wrap justify-between gap-y-2 gap-x-4 border-t border-gray-900/5 px-4 py-4 sm:px-6 lg:border-t-0 xl:px-8 lg:border-l first:lg:border-l-0"}
   [:dt {:class "text-sm font-medium leading-6 text-gray-500"} caption]
   #_[:dd {:class "text-xs font-medium text-gray-700"} "+4.75%"]
   [:dd {:class "w-full flex-none text-3xl font-medium leading-10 tracking-tight text-gray-900"}
    value]])

(def timespans
  {"last-three-months"  (t/inst (t/<< (t/zoned-date-time) (t/new-period 3 :months)))
   "last-six-months" (t/inst (t/<< (t/zoned-date-time) (t/new-period 6 :months)))
   "last-one-year" (t/inst (t/<< (t/zoned-date-time) (t/new-period 12 :months)))
   "now" (t/inst (t/<<  (t/zoned-date-time) (t/new-period 1 :days)))})

(defn fmt-double [p]
  (format "%.1f" (double p)))
(defn fmt-percent [p]
  (format "%.1f%%" (* 100 (double p))))

(def query-param-field-mapping
  {"name" :member/name
   "gigs-attended" :gigs-attended
   "gigs-percent" :gigs-percent
   "probes-attended" :probes-attended
   "probes-percent" :probes-percent
   "last-seen" :last-seen})

(defn member-stats-head [{:keys [tr] :as req}]
  (let [sort-spec (util.http/sort-param req query-param-field-mapping)
        sort-param-maker (fn [k]
                           {:hx-boost "true"
                            :href
                            (str
                              (util.http/serialize-sort-param query-param-field-mapping (util.http/sort-param-by-field sort-spec k))
                              "&timespan="(get-in req [:query-params "timespan"] "last-three-months")
                              )
                            :class "link-blue"})]

    (ui/table-row-head
     [{:label
       [:a (sort-param-maker :member/name) (tr [:member/name])] :priority :important :key :name}
      {:label [:a (sort-param-maker :gigs-attended) (tr [:stats/gigs-attended])] :priority :medium :key :value}
      {:label [:a (sort-param-maker :gigs-percent) (tr [:stats/gigs-percent])] :priority :medium :key :value}
      {:label [:a (sort-param-maker :probes-attended) (tr [:stats/probes-attended])] :priority :medium :key :value}
      {:label [:a (sort-param-maker :probes-percent) (tr [:stats/probes-percent])] :priority :medium :key :value}
      {:label [:a (sort-param-maker :last-seen) (tr [:stats/last-seen])] :priority :low :key :value}
      {:label "Gigs" :priority :sm-only :key :value}
      {:label "Probes" :priority :sm-only :key :value}
   ;;
      ]))

  #_[:thead {:class "border-b border-gray-900/5 text-sm leading-6 "}
     [:tr
      [:th {:scope "col", :class "py-2 pl-4 pr-8 font-semibold sm:pl-6 lg:pl-8"}
       "Member"]
      [:th {:scope "col", :class "hidden py-2 pl-0 pr-8 font-semibold sm:table-cell"}
       "Gigs Attended" #_"Number of rehearsals and gigs attended by each member."]
      [:th {:scope "col", :class "hidden py-2 pl-0 pr-8 font-semibold sm:table-cell"}
       "Gigs %" #_"Number of rehearsals and gigs attended by each member."]
      [:th {:scope "col", :class "py-2 pl-0 pr-4 text-right font-semibold sm:pr-8 sm:text-left lg:pr-20"}
       "Probes Attended"
       #_"Attendance Percentage" #_"The percentage of attended versus missed rehearsals and gigs."]
      [:th {:scope "col", :class "py-2 pl-0 pr-4 text-right font-semibold sm:pr-8 sm:text-left lg:pr-20"}
       "Probes %"]
      [:th {:scope "col", :class "hidden py-2 pl-0 pr-8 font-semibold md:table-cell lg:pr-20"}
       "Last Seen" #_"The date of the last rehearsal and gig attended."]]])
(defn member-stat-row [tr {:keys [member  gigs-attended probes-attended last-seen gig-rate probe-rate]}]
  (let [td-class "px-3 py-4"]
    [:tr
     [:td {:class (ui/cs "w-full max-w-0 py-4 pl-4 pr-3 sm:w-auto sm:max-w-none sm:pl-6" (ui/table-row-priorities :important))}
      [:div {:class "flex items-center gap-x-4"}
       (ui/avatar-img member :class "h-8 w-8 rounded-full bg-gray-800 w-8 rounded-full hidden sm:block")
       [:div {:class "truncate text-sm font-medium leading-6 "} (:member/name member)]]
      [:dl {:class "font-normal xl:hidden"}
       [:dt {:class "sr-only sm:hidden"} (tr [:stats/last-seen])]
       [:dd {:class "mt-1 truncate text-gray-500"} last-seen]
       (comment
         [:dt {:class "sm:hidden"}]
         [:dd {:class "mt-1 truncate text-gray-500 sm:hidden"} (tr [:stats/gigs-attended]) ": " gigs-attended]
         [:dd {:class "mt-1 truncate text-gray-500 sm:hidden"} probes-attended]
         [:dt {:class "sr-only sm:hidden"} "foo"]
         [:dd {:class "mt-1 truncate text-gray-500"}])]]
     [:td {:class (ui/cs td-class (ui/table-row-priorities :medium))}
      gigs-attended]
     [:td {:class (ui/cs td-class (ui/table-row-priorities :medium))}
      (fmt-percent gig-rate)]
     [:td {:class (ui/cs td-class (ui/table-row-priorities :medium))}
      probes-attended]
     [:td {:class (ui/cs td-class (ui/table-row-priorities :medium))}
      (fmt-percent probe-rate)]
     [:td {:class (ui/cs td-class (ui/table-row-priorities :low "text-gray-400"))}
      last-seen]
     [:td {:class (ui/cs td-class "table-cell sm:hidden")}
      [:div
       (fmt-percent gig-rate) " (" gigs-attended ")"]]
     [:td {:class (ui/cs td-class "table-cell sm:hidden")}
      [:div
       (fmt-percent probe-rate) " (" probes-attended ")"]]]))

(ctmx/defcomponent ^:endpoint stats-index-page [{:keys [db tr query-params] :as req}]
  (let [timespan (get query-params "timespan" "last-three-months")
        then (get timespans timespan)
        now (get timespans "now")
        _ (assert then)
        _ (assert now)
        {:keys [attendance-rate-gigs attendance-rate-probes  mean-attendance-gig mean-attendance-probe mean-active-members
                probe-count gig-count total-plays per-member-stats probe-histogram gig-histogram]
         :as stats} (controller/stats-for db then now (util.http/sort-param req query-param-field-mapping))]

    [:main {:class "bg-white"}
     (render/chart-stat-scripts)
     [:div {:class "relative isolate overflow-hidden"}
      [:header {:class "pb-4 pt-6 sm:pb-6"}
       [:div {:class "mx-auto flex max-w-7xl flex-wrap items-center gap-6 px-4 sm:flex-nowrap sm:px-6 lg:px-8"}
        [:h1 {:class "text-base font-semibold leading-7 text-gray-900"} "SNO Stats"]
        [:div {:class "order-last flex w-full gap-x-8 text-sm font-semibold leading-6 sm:order-none sm:w-auto sm:border-l sm:border-gray-200 sm:pl-6 sm:leading-7"}
         [:a {:href "?timespan=last-three-months" :class (ui/cs (if (= timespan "last-three-months") "text-sno-orange-600" "text-gray-700"))} (tr [:stats/last-three-months])]
         [:a {:href "?timespan=last-six-months" :class (ui/cs (if (= timespan "last-six-months") "text-sno-orange-600" "text-gray-700"))} (tr [:stats/last-six-months])]
         [:a {:href "?timespan=last-one-year" :class (ui/cs (if (= timespan "last-one-year") "text-sno-orange-600" "text-gray-700"))} (tr [:stats/last-year])]
         #_[:a {:href "?timespan=last-three-years" :class (ui/cs (if (= timespan "last-three-years") "text-sno-orange-600" "text-gray-700"))} "Last 3 years"]]
        #_[:a {:href "#", :class "ml-auto flex items-center gap-x-1 rounded-md bg-sno-orange-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-sno-orange-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sno-orange-600"}
           [:svg {:class "-ml-1.5 h-5 w-5", :viewbox "0 0 20 20", :fill "currentColor", :aria-hidden "true"}
            [:path {:d "M10.75 6.75a.75.75 0 00-1.5 0v2.5h-2.5a.75.75 0 000 1.5h2.5v2.5a.75.75 0 001.5 0v-2.5h2.5a.75.75 0 000-1.5h-2.5v-2.5z"}]] "New invoice"]]]
      [:div {:class "border-b border-b-gray-900/10 lg:border-t lg:border-t-gray-900/5"}
       [:dl {:class "mx-auto grid max-w-7xl grid-cols-2 sm:grid-cols-2 lg:grid-cols-5 lg:px-2 xl:px-0"}
        (stat-block (tr [:stats/gig-attendance-rate])
                    ;;  Percentage of the active band attending recent rehearsals and gigs.
                    [:span (fmt-percent attendance-rate-gigs)])
        (stat-block (tr [:stats/probe-attendance-rate])
                    [:span (fmt-percent attendance-rate-probes)])
        (stat-block (tr [:stats/mean-attendance-gig]) (fmt-double mean-attendance-gig))
        (stat-block (tr [:stats/mean-attendance-probe]) (fmt-double mean-attendance-probe))
        (stat-block (tr [:stats/mean-active-members]) (fmt-double  mean-active-members))

        (stat-block (tr [:stats/total-gigs]) [:span gig-count])
        (stat-block (tr [:stats/total-probes]) [:span probe-count])
        (stat-block (tr [:stats/total-plays]) [:span total-plays])]]

      [:script {:type "application/json" :id "gig-histogram-data"}
       (hiccup.util/raw-string (j/write-value-as-string
                                {:values gig-histogram
                                 :xAxisLabel (tr [:stats/attendance-rate])
                                 :yAxisLabel (tr [:stats/num-members])
                                 :title (tr [:stats/gig-attendance])
                                 :color "#f97316"}))]
      [:script {:type "application/json" :id "probe-histogram-data"}
       (hiccup.util/raw-string (j/write-value-as-string
                                {:values probe-histogram
                                 :xAxisLabel (tr [:stats/attendance-rate])
                                 :yAxisLabel (tr [:stats/num-members])
                                 :title (tr [:stats/probe-attendance])
                                 :color "#22c55e"}))]
      [:div {:class "lg:flex gap-x-4 px-2"}
       [:div  {:class "histogram-chart-container w-full max-w-5xl" #_#_:style (str "height: " 400 "px;")}
        [:canvas {:class "histogram-chart" :data-values "#gig-histogram-data" :id "gig-histogram"}]]
       [:div  {:class "histogram-chart-container w-full max-w-5xl" #_#_:style (str "height: " 400 "px;")}
        [:canvas {:class "histogram-chart" :data-values "#probe-histogram-data" :id "probe-histogram"}]]]

      [:div {:class "border-t border-gray-900/5 pt-11"}
       [:h2 {:class "px-4 text-base font-semibold leading-7  sm:px-6 lg:px-8"} ""]
       [:table {:class "mt-6 w-full whitespace-nowrap text-left"}
        [:colgroup
         [:col {:class "w-full sm:w-4/12"}]
         [:col {:class "lg:w-4/12"}]
         [:col {:class "lg:w-2/12"}]
         [:col {:class "lg:w-1/12"}]
         [:col {:class "lg:w-1/12"}]]

        (member-stats-head req)
        [:tbody {:class "divide-y divide-white/5"}
         (map (partial member-stat-row tr) per-member-stats)]]]]]))
