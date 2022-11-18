(ns app.probeplan.views
  (:require

   [app.probeplan :as pp]
   [app.util :as util]
   [app.urls :as url]
   [app.ui :as ui]
   [app.probeplan.controller :as controller]
   [ctmx.response :as response]
   [app.icons :as icon]
   [ctmx.core :as ctmx]
   [tick.core :as t]
   [tick.alpha.interval :as t.i]
   [ctmx.rt :as rt]
   [medley.core :as m]
   [app.queries :as q]
   [app.i18n :as i18n]))

(defn probe-row [songs])

(ctmx/defcomponent ^:endpoint probeplan-probe-song-col-ro [{:keys [db] :as req} idx  {:song/keys [title song-id] :as song}]
  (let [comp-name (util/comp-namer #'probeplan-probe-song-col-ro)
        all-songs (q/find-all-songs db)]
    [:td {:class (ui/cs
                  "px-2 py-1"
                  ;; "max-w-[12rem] truncate"
                  (if (or (= idx 0) (= idx 1)) "bg-orange-100" "bg-blue-100"))
          :_ "on click toggle .hidden on the first <div.rw/> in me end
              on click toggle .hidden on the first <div.ro/> in me end"
          :hx-include (str (hash ".") " select,", (hash ".") " input")}

     [:div  ;; {:class "min-h-[29px]"}
      [:div {:class "hidden rw"}
       (ui/song-select :id (path "song-id") :label "" :value song-id :songs all-songs :size :small :extra-attrs {:hx-post "probeplan-index-page" :hx-target "../"})]
      [:div {:class "ro"}
       title]]]))

(ctmx/defcomponent ^:endpoint probeplan-probe-song-col-rw [{:keys [db] :as req} idx {:song/keys [title song-id] :keys [fixed?]}]
  (let [all-songs (q/find-all-songs db)]
    [:td {:class (ui/cs
                  "px-2 py-1"
                  ;; "max-w-[12rem] truncate"
                  (if (or (= idx 0) (= idx 1)) "bg-orange-100" "bg-blue-100"))}
     (if fixed?
       (ui/song-select :id (path "song-id") :label "" :size :small :value song-id :songs all-songs)
       title)]))

(defn probeplan-probe-row [edit? {:keys [db] :as req} idx {:keys [date songs last-fixed? fixed? num-gigs]}]
  [:tr {:class (ui/cs "border border-x-black border-y-gray-300 border-l border-r border-b border-t-0"
                      (cond
                        last-fixed? "border-solid border-b-1 border-b-black"
                        fixed? "border-solid"
                        :else "border-dashed"))}

   [:td {:class "px-2"} (inc idx)]
   [:td {:class "font-mono px-2 py-1 bg-green-100"}
    (let [date-str (t/format (t/formatter "dd.MM") date)]
      (if fixed?
        [:a {:href "#" :class "link-blue underline"} date-str]
        date-str))]

   [:td {:class ""} num-gigs]
   (if edit?
     (rt/map-indexed probeplan-probe-song-col-rw req (map #(merge {:fixed? fixed?} %) songs))
     (rt/map-indexed probeplan-probe-song-col-ro req songs))
   (if (and edit? fixed?)
     [:td {}
      (ui/button :label "Gig Probe" :size :xsmall)]
     [:td])])

(defn howitworks [tr]
  (ui/panel {:title "How This Works"}
            [:div {:class "ml-4 leading-relaxed"}
             [:p {:class "text-l"}
              "The probeplan generator works like this:"
              [:ul {:class "list-disc max-w-lg"}
               [:li {:class "ml-8"} "The probematic generates an infinite amount of future probeplans, based on various parameters. " [:br] "(number of plays, days since last play, etc)"]
               [:li {:class "ml-8"} "The next 4 probes are assigned probeplans and these are not changed automatically by the system they are \"fixed\"."]
               [:li {:class "ml-8"} "A human (you!) can edit the plans for the next 4 probes."]
               [:li {:class "ml-8"} "The system will update all probeplans after the next 4 based on the parameters mentioned before"]
               [:li {:class "ml-8"} "The Gigs column shows the number of gigs taking place after the Probe, but before the next one."]
               [:li {:class "ml-8"} "Use the \"Gig Probe\" button to change a probe from a Probeplan to a Setlist to practice a setlist for a gig."]
               ;;
               ]]]))
(ctmx/defcomponent ^:endpoint probeplan-index-page [{:keys [db] :as req} ^:boolean edit?]
  probeplan-probe-song-col-ro
  probeplan-probe-song-col-rw
  (let [tr (i18n/tr-from-req req)
        comp-name (util/comp-namer #'probeplan-index-page)
        song-cycle (pp/make-song-cycle db)
        num-probes 20
        num-songs-p-probe 5
        num-songs-needed (* 20 5)
        num-fixed 4
        num-floating (- num-probes 4)
        probe-dates (take num-probes (pp/wednesday-sequence (t/date)))
        songs (partition 5 (take num-songs-needed song-cycle))
        probes (map (fn [idx songs date] {:songs songs
                                          :date date
                                          :idx idx
                                          :last-fixed? (= (inc idx)  num-fixed)
                                          :num-gigs (rand-int 3)
                                          :fixed? (<= (inc idx) num-fixed)})
                    (range) songs probe-dates)]
    (tap> probes)
    [:div {:id id}
     (ui/page-header :title (tr [:nav/probeplan]))
     (howitworks tr)
     [:div {:class "mt-8 mr-2 ml-2"}
      [:div {:class ""}
       [:div {:class "flex items-center justify-end"}
        [:div {:class "mt-4 sm:mt-0 sm:ml-16 flex sm:flex-row space-x-4"}
         (ui/toggle :label "Edit" :active? edit? :id (path "toggle") :hx-target (hash ".") :hx-get (comp-name) :hx-vals {:edit? (not edit?)})]]
       [:div {:class "mt-4"}

        [:table {:class "min-w-full text-sm table-auto fade-out relative"}
         (ui/table-row-head  [{:label "Nr"}
                              {:label "Date"}
                              {:label "Gigs"}
                              {:label "Intensive 1"}
                              {:label "Intensive 2"}
                              {:label "Durchspielen 1"}
                              {:label "Durchspielen 2"}
                              {:label "Durchspielen 3"}
                              {:label ""}])

         (map-indexed (partial probeplan-probe-row edit? req) probes)
         [:tr
          [:td]
          [:td]
          [:td]
          [:td]
          [:td ""]
          [:td ""]
          [:td ""]
          [:td ""]
          [:td]]]]]]]))
