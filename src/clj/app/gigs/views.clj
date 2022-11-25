(ns app.gigs.views
  (:refer-clojure :exclude [comment])
  (:require
   [app.auth :as auth]
   [app.gigs.controller :as controller]
   [app.gigs.domain :as domain]
   [app.i18n :as i18n]
   [app.icons :as icon]
   [app.probeplan.domain :as probeplan.domain]
   [app.queries :as q]
   [app.ui :as ui]
   [app.urls :as url]
   [app.util :as util]
   [clojure.set :as set]
   [clojure.string :as str]
   [ctmx.core :as ctmx]
   [ctmx.response :as response]
   [ctmx.rt :as rt]
   [medley.core :as m]
   [tick.core :as t]))

(defn radio-button  [idx {:keys [id name label value opt-id icon size class icon-class model disabled? required? data]
                          :or {size :normal
                               class ""
                               required? false
                               icon-class ""
                               disabled? false}}]
  (let [checked? (= model value)]
    [:label {:id id
             :class
             (ui/cs
              "cursor-pointer"
              class
              (if checked?
                "toggler--checked"
                "toggler--not-checked"))}

     [:input {:type "radio" :name name :value value :class "sr-only" :aria-labelledby id
              :required required?
              :disabled disabled?
              :checked checked?
              :data-toggler data
              :_ (format  "on change trigger radioChanged(value:[@value]) on <input[name='%s']/> end
                  on radioChanged(value) if I match <:checked/>
                    add .toggler--checked to the closest parent <label/>
                    add .{[@data-toggler]} to the closest parent <li.toggler/>
                    remove .toggler--not-checked from the closest parent <label/>
                  else
                    remove .toggler--checked from the closest parent <label/>
                    remove [@data-toggler] from the closest parent <li.toggler/>
                    remove .{[@data-toggler]} from the closest parent <li.toggler/>
                    add .toggler--not-checked to the closest parent <label/>
                  end" name)}]
     [:span
      (icon {:class (ui/cs "h-8 w-8" icon-class)})]]))

(defn radio-button-group [& {:keys [options id label class value required?]
                             :or {class ""
                                  required? false}}]
  [:fieldset
   [:legend {:class ""} label]
   [:div {:class (ui/cs "mt-1 flex items-center space-x-3" class)}
    (->> options
         (map #(merge {:name id :model value :required? required?} %))
         (map-indexed radio-button))]])

(defn log-play-legend [tr]
  (let [shared-classes "h-8 w-8 border-solid border-b-2 pb-1"]
    [:div {:class "ml-2"}
     [:h3 {:class "font-bold"}
      (tr [:play-log/diagram-legend])]

     [:div {:class "grid grid-cols-3 space-y-2 max-w-sm sm:max-w-none sm:grid-cols-7 sm:space-x-3"}
      [:div {:class "flex items-center justify-start sm:min-w-fit"}
       (icon/circle-xmark-outline {:class (ui/cs shared-classes "icon-not-played border-gray-500 toggler--checked")})
       (tr [:play-log/not-played])]
      [:div {:class "flex items-center justify-start"}
       (icon/smile {:class (ui/cs shared-classes "icon-smile border-green-500 toggler--checked")})
       (tr [:play-log/nice])]
      [:div {:class "flex items-center justify-start"}
       (icon/meh {:class (ui/cs shared-classes "icon-meh border-blue-500 toggler--checked")})
       (tr [:play-log/okay])]
      [:div {:class "flex items-center justify-start"}
       (icon/sad {:class (ui/cs shared-classes "icon-sad border-red-500 toggler--checked")})
       (tr [:play-log/bad])]
      [:div {:class "col-span-2 flex justify-start items-center"}
       (icon/fist-punch {:class (ui/cs shared-classes "icon-fist-punch intensiv--checked")})
       (tr [:play-log/intensive])]]]))

(ctmx/defcomponent gig-log-play [{:keys [db] :as req} idx play]
  (let [was-played? (some? (:played/play-id play))
        check-id (path "intensive")
        radio-id (path "feeling")
        song (:played/song play)
        song-id (:song/song-id song)
        feeling-value (util/kw->str (or (:played/rating play) :play-rating/not-played))
        intensive?  (= :play-emphasis/intensiv (:played/emphasis play))
        toggler-class (get {"play-rating/not-played" "toggler--not-played"
                            "play-rating/good" "toggler--feeling-good"
                            "play-rating/bad" "toggler--feeling-sad"
                            "play-rating/ok" "toggler--feeling-meh"} feeling-value)]
    [:li {:class (ui/cs "toggler" toggler-class)}
     [:input {:type "hidden" :value song-id :name (path "song-id")}]
     (when was-played?
       [:input {:type "hidden" :value (:played/play-id play) :name (path "play-id")}])
     [:div {:class "inline-block"}
      (:song/title song)]

     [:div {:class "flex"}
      [:div
       (radio-button-group  :id  radio-id :label ""
                            :required? true
                            :value feeling-value
                            :class "emotion-radio"
                            :options [{:id (path "feeling/not-played") :value "play-rating/not-played" :icon icon/circle-xmark-outline :size :small :class "icon-not-played" :data "toggler--not-played"}
                                      {:id (path "feeling/good") :label "Nice!" :value "play-rating/good" :icon icon/smile :size :large :class "icon-smile" :data "toggler--feeling-good"}
                                      {:id (path "feeling/ok")  :label "Okay" :value "play-rating/ok" :icon icon/meh :size :large :class "icon-meh"  :data "toggler--feeling-meh"}
                                      {:id  (path "feeling/bad")  :label "Uh-oh" :value "play-rating/bad" :icon icon/sad :size :large :class "icon-sad"  :data "toggler--feeling-sad"}])]
      [:div  {:class "border-l-4  border-gray-200 ml-2 pl-2 mt-1 flex items-center space-x-3"}
       [:label  {:for check-id :class (ui/cs "icon-fist-punch cursor-pointer" (when intensive? "intensiv--checked"))}
        [:input {:type "hidden" :name check-id :value "play-emphasis/durch"}]
        [:input {:type "checkbox" :class "sr-only" :name check-id :id check-id :value "play-emphasis/intensiv"
                 :_ "on change if I match <:checked/>
                                                add .intensiv--checked to the closest parent <label/>
                                                else
                                                remove .intensiv--checked from the closest parent <label/>
                                              end"
                 :checked intensive?}]
        (icon/fist-punch {:class "h-8 w-8"})]]]]))

(ctmx/defcomponent ^:endpoint gig-log-plays [{:keys [db] :as req}]
  (let [gig-id (-> req :path-params :gig/gig-id)
        post? (util/post? req)
        tr (i18n/tr-from-req req)
        gig (controller/retrieve-gig db gig-id)
        plays (cond post?
                    (:plays (controller/log-play! req gig-id))
                    :else
                    (controller/plays-by-gig db gig-id))
        songs-not-played (controller/songs-not-played plays (q/find-all-songs db))
        ;; our log-play component wants to have "plays" for every song
        ;; so we enrich the actual plays with stubs for the songs that were not played
        plays (sort-by #(-> % :played/song :song/title) (concat plays
                                                                (map (fn [s] {:played/song s}) songs-not-played)))]

    [:form {:id id :hx-post (path ".") :class "space-y-4"}
     (ui/page-header :title (list  (:gig/title gig) " (" (ui/datetime (:gig/date gig)) ")")
                     :subtitle (tr [:gig/play-log-subtitle]))
     (log-play-legend tr)
     [:ul {:class "toggler-container"}
      (rt/map-indexed gig-log-play req plays)]
     [:div
      [:div {:class "flex justify-end"}
       [:a {:href (url/link-gigs-home) :class "btn btn-sm btn-clear-normal"} (tr [:action/cancel])]
       [:button {:class "ml-3 btn btn-sm btn-indigo-high" :type "submit"} (tr [:action/save])]]]]))

(defn gig-row [{:gig/keys [status title location date gig-id] :as gig}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"]
    [:a {:href (url/link-gig gig) :class "block hover:bg-gray-50"}
     [:div {:class "px-4 py-4 sm:px-6"}
      [:div {:class "flex items-center justify-between"}
       [:div {:class "flex items-center space-x-2"}
        (ui/gig-status-icon status)
        [:p {:class "truncate text-sm font-medium text-indigo-600"} title]]]

      [:div {:class "mt-2 sm:flex sm:justify-between"}
       [:div {:class "flex items-center text-sm text-gray-500"}
        (icon/location-dot {:class style-icon})
        location]
       [:div {:class "mt-2 flex items-center text-sm text-gray-500 mt-0 ml-6 min-w-[8rem]"}
        (icon/calendar {:class style-icon})
        (ui/datetime date)]]]]))

(defn attendance-opts [tr size]
  (let [icon-class "mr-3 text-gray-400"
        size-class (if (= size :large) "w-5 h-5" "w-3 h-3")
        red-class "text-red-500 group-hover:text-red-500"
        green-class "text-green-500 group-hover:text-green-500"]
    {:opts [{:label (tr [:plan/definitely]) :value (name :plan/definitely)  :icon icon/circle :icon-class (ui/cs icon-class size-class green-class)}
            {:label (tr [:plan/probably]) :value (name :plan/probably) :icon icon/circle-outline  :icon-class (ui/cs icon-class size-class green-class)}
            {:label (tr [:plan/unknown]) :value (name :plan/unknown) :icon icon/question :icon-class (ui/cs icon-class  size-class "text-gray-500")}
            {:label (tr [:plan/probably-not]) :value (name :plan/probably-not) :icon icon/square-outline :icon-class (ui/cs icon-class size-class red-class)}
            {:label (tr [:plan/definitely-not]) :value (name :plan/definitely-not)    :icon icon/square :icon-class (ui/cs icon-class size-class red-class)}
            {:label (tr [:plan/not-interested]) :value (name :plan/not-interested)    :icon icon/xmark :icon-class (ui/cs icon-class  size-class "text-black")}]
     :default {:label (tr [:plan/no-response]) :value (name :plan/no-response) :icon icon/minus :icon-class (ui/cs icon-class  size-class "text-gray-500")}}))

(defn attendance-dropdown-opt [{:keys [label value icon icon-class]}]
  [:a {:href "#" :data-value value :class "hover:bg-gray-200 text-gray-700 group flex items-center px-4 py-2 text-sm", :role "menuitem", :tabindex "-1", :id "menu-item-0"}
   (icon {:class  icon-class}) label])

(defn attendance-dropdown [& {:keys [gigo-key gig-id value tr]
                              :or {value "no-response"}}]
  (let [{:keys [opts default]} (attendance-opts tr :large)
        current-opt (or (m/find-first #(= value (:value %)) (:opts (attendance-opts tr :small))) default)
        button-size-class-normal "px-4 py-2 text-sm "
        button-size-class-small "px-2 py-1 text-xs "]
    [:div {:class "dropdown relative inline-block text-left"}
     [:div
      [:button {:type "button" :class (ui/cs button-size-class-small "dropdown-button inline-flex w-full justify-center rounded-md border border-gray-300 bg-white font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 focus:ring-offset-gray-100 min-h-[25px]")  :aria-expanded "true", :aria-haspopup "true"}
       [:input {:type "hidden" :name "gigo-key" :value gigo-key}]
       [:input {:type "hidden" :name "gig-id" :value gig-id}]
       [:input {:type "hidden" :name "plan" :value (:value current-opt) :class "item-input"}]
       [:span {:class "item-icon"}
        ((:icon current-opt) {:class (ui/cs (:icon-class current-opt) "w-3 h-3")})]
       (icon/chevron-down {:class (ui/cs "-mr-1 ml-1 h-3 w-3")})]]
     [:div {:class "dropdown-menu-container hidden"}
      [:div {:class (ui/cs
                     "dropdown-choices"
                     (when false "absolute right-0 ")
                     "z-10 w-56 origin-top-right divide-y divide-gray-100 rounded-md bg-white shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none")
             :role "menu" :aria-orientation "vertical" :aria-labelledby "menu-button" :tabindex "-1"}
       [:div {:class "py-1" :role "none"}
        (map attendance-dropdown-opt  opts)]]]]))

(defn motivation-endpoint [req {:keys [path id hash value]} comp-name gig-id gigo-key motivation]
  (let [post?      (util/post? req)
        gigo-key   (or gigo-key (value "gigo-key"))
        gig-id     (or gig-id (value "gig-id"))
        tr         (i18n/tr-from-req req)
        motivation (if post?
                     (-> (controller/update-attendance-motivation! req gig-id) :attendance :attendance/motivation)
                     motivation)]
    [:form {:id id :hx-target (hash ".")}
     [:input {:type :hidden :name (path "gig-id") :value gig-id}]
     (ui/select
      :id (path "motivation")
      :value (when motivation (name motivation))
      :size :small
      :extra-attrs {:hx-trigger "change" :hx-post (comp-name) :hx-vals (ui/hx-vals {(path "gigo-key") gigo-key})}
      :options (map (fn [m] {:label (tr [m]) :value (name m)}) domain/motivations))]))

(defn comment-endpoint [req {:keys [path id hash value]} comp-name gig-id gigo-key comment edit?]
  (let [post? (util/post? req)
        gigo-key (or gigo-key (value "gigo-key"))
        gig-id (or gig-id (value "gig-id"))
        comment (if post?
                  (-> (controller/update-attendance-comment! req gig-id) :attendance :attendance/comment)
                  comment)]
    [:div {:id id :class ""}
     (if edit?
       (ui/text :name (path "comment") :value comment :required? false :size :small
                :extra-attrs {:hx-target (hash ".") :hx-post (comp-name) :hx-trigger "focusout, keydown[key=='Enter'] changed"  :autofocus true
                              :hx-vals {(path "gigo-key") gigo-key (path "gig-id") gig-id}
                              :_ "on focus or htmx:afterRequest or load
                                          set :initial_value to my value
                                        end
                                        on keyup[key=='Escape']
                                          set my value to the :initial_value then
                                          blur() me
                                        end"})

       (let [hx-attrs {:hx-target (hash ".") :hx-get (comp-name) :hx-vals {:gig-id gig-id :gigo-key gigo-key :comment comment :edit? true}}]
         (if comment
           [:span (merge hx-attrs {:class "text-xs sm:text-sm ml-2 link-blue"})
            comment]
           [:button  hx-attrs
            (icon/comment-outline {:class "ml-2 mb-2 w-5 h-5 cursor-pointer hover:text-blue-500 active:text-blue-300"})])))]))

(defn plan-endpoint [req {:keys [path id hash value]} comp-name gig-id gigo-key plan]
  (let [post? (util/post? req)
        tr (i18n/tr-from-req req)
        gigo-key   (or gigo-key (value "gigo-key"))
        gig-id     (or gig-id (value "gig-id"))
        plan-kw (or
                 (if post?
                   (-> (controller/update-attendance-plan! req gig-id) :attendance :attendance/plan)
                   plan)
                 :plan/no-response)]
    [:form {:hx-post (comp-name) :id id :hx-trigger "planChanged"} ;; this form is triggered by javascript
     (attendance-dropdown :tr tr :gig-id gig-id :gigo-key gigo-key :value (name plan-kw))]))

(ctmx/defcomponent ^:endpoint gig-attendance-person-plan [{:keys [db] :as req} gigo-key plan]
  (plan-endpoint req
                 {:path path :id id :hash hash :value value}
                 (util/comp-namer #'gig-attendance-person-plan)
                 (-> req :path-params :gig/gig-id)
                 gigo-key plan))

(ctmx/defcomponent ^:endpoint gig-attendance-person-comment [{:keys [db] :as req} gigo-key comment  ^:boolean edit?]
  (comment-endpoint req
                    {:path path :id id :hash hash :value value}
                    (util/comp-namer #'gig-attendance-person-comment)
                    (-> req :path-params :gig/gig-id)
                    gigo-key comment edit?))

(ctmx/defcomponent ^:endpoint gig-attendance-person-motivation [{:keys [db] :as req} gigo-key motivation]
  (motivation-endpoint req
                       {:path path :id id :hash hash :value value}
                       (util/comp-namer #'gig-attendance-person-motivation)
                       (-> req :path-params :gig/gig-id)
                       gigo-key motivation))

(ctmx/defcomponent ^:endpoint gig-attendance-person [{:keys [db] :as req} idx attendance]
  (let [{:member/keys [gigo-key] :as member} (:attendance/member attendance)
        has-comment? (:attendance/comment attendance)]
    [:div {:id id}
     ;; for mobile < sm
     [:div {:class (ui/cs "sm:hidden grid grid-rows-auto  grid-cols-3")}
      [:div {:class (ui/cs "grid gap-x-2 grid-cols-6 col-span-3")}
       [:div {:class "col-span-2 align-middle"} [:a {:href (url/link-member gigo-key) :class "link-blue align-middle"} (ui/member-nick member)]]
       [:div {:class "col-span-1"} (gig-attendance-person-plan req gigo-key (:attendance/plan attendance))]
       [:div {:class "col-span-2"} (gig-attendance-person-motivation req gigo-key (:attendance/motivation attendance))]
       (when-not has-comment? [:div {:class "col-span-1 break-words"} (gig-attendance-person-comment req gigo-key (:attendance/comment attendance) false)])]
      (when has-comment?
        [:div {:class "col-start-2 col-span-2 break-words"} (gig-attendance-person-comment req gigo-key (:attendance/comment attendance) false)])]
     ;; for > sm
     [:div {:class "hidden sm:grid sm:grid-cols sm:grid-cols-6 sm:gap-x-2"}
      [:div {:class "col-span-2 align-middle"}
       [:a {:href (url/link-member gigo-key) :class "text-blue-500 hover:text-blue-600 align-middle"}
        (ui/member-nick member)]]
      [:div {:class "col-span-1"} (gig-attendance-person-plan req gigo-key (:attendance/plan attendance))]
      [:div {:class "col-span-1"} (gig-attendance-person-motivation req gigo-key (:attendance/motivation attendance))]
      [:div {:class "col-span-2 break-words"} (gig-attendance-person-comment req gigo-key (:attendance/comment attendance) false)]]]))

(defn gig-attendance-person-archived [{:keys [db] :as req} idx attendance]
  (let [{:member/keys [gigo-key] :as member} (:attendance/member attendance)]
    [:div {:class "grid grid-cols grid-cols-5"}
     [:div {:class "col-span-2 align-middle"}
      [:a {:href (url/link-member gigo-key) :class "text-blue-500 hover:text-blue-600 align-middle"}
       (ui/member-nick member)]]
     [:div {:class "col-span-1"} (:attendance/plan attendance)]
     [:div {:class "col-span-1"} (:attendance/motivation attendance)]
     [:div {:class "col-span-1 "} (:attendance/comment attendance)]]))

(defn section-name-wrappable [section]
  (interpose [:span "/" [:wbr]]
             (str/split (:section/name section) #"/")))

(ctmx/defcomponent ^:endpoint gig-attendance [{:keys [db] :as req} idx section]
  [:div {:class "gap-x-0 gap-y-8 mt-2 mb-2 px-4 py-0 sm:px-6 border-b border-gray-200"}
   [:div {:class "font-bold break-words"} (section-name-wrappable section)]
   [:div {:id id :class ""}
    (rt/map-indexed gig-attendance-person req (:members section))]])
(ctmx/defcomponent ^:endpoint gig-attendance-archived [{:keys [db] :as req} idx section]
  [:div {:id id :class (ui/cs "grid grid-cols-3 gap-x-0 gap-y-8 mb-2 px-4 py-0 sm:px-6"
                              (when (= 0 (mod idx 2))
                                "bg-gray-100"))}

   [:div {:class "col-span-1 font-bold break-words"} (section-name-wrappable section)]
   [:div {:class "col-span-2"}
    (rt/map-indexed gig-attendance-person-archived req (:members section))]])

(defn gig-comment-li [comment]
  (let [{:comment/keys [body author created-at]} comment]
    [:li
     [:div {:class "flex space-x-3"}
      [:div {:class "flex-shrink-0"}
       [:a {:href (url/link-member author)}
        (ui/avatar-img author :class "h-10 w-10 rounded-full")]]
      [:div
       [:div {:class "text-sm"}
        [:a {:href (url/link-member author) :class "font-medium text-gray-900"}
         (ui/member-nick author)]]
       [:div {:class "mt-1 text-sm text-gray-700"}
        [:p body]]
       [:div {:class "mt-2 space-x-2 text-sm"}
        [:span {:class "font-medium text-gray-500"}
         (ui/humanize-dt created-at)]]]]]))

(defn comment-list [tr comments]
  [:div {:class "px-4 py-6 sm:px-6"}
   (if (empty? comments)
     (tr [:gig/no-comments])
     [:ul {:role "list", :class "space-y-8"}
      (map gig-comment-li comments)])])

(defn comment-header [tr]
  [:div {:class "px-4 py-5 sm:px-6"}
   [:h2 {:id "notes-title", :class "text-lg font-medium text-gray-900"}
    "Comments"]])

(defn comment-input [member target endpoint tr]
  [:div {:class "bg-gray-50 px-4 py-6 sm:px-6"}
   [:div {:class "flex space-x-3"}
    [:div {:class "flex-shrink-0"}
     (ui/avatar-img member :class "h-10 w-10 rounded-full")]
    [:div {:class "min-w-0 flex-1"}
     [:form {:hx-target target :hx-post endpoint}
      [:div
       [:label {:for "comment-body", :class "sr-only"} "Comment"]
       [:textarea {:id "comment-body", :name "body", :rows "3", :class "block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                   :placeholder (tr [:gig/comment-placeholder])}]]
      [:div {:class "mt-3 flex items-center justify-between"}
       [:button {:type "submit", :class "inline-flex items-center justify-center rounded-md border border-transparent bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"}
        (tr [:action/comment])]]]]]])

(defn comment-section [member archived? id endpoint tr comments]
  [:div {:id id :class "divide-y divide-gray-200"}
   (comment-header tr)
   (comment-list tr comments)
   (when (and member (not archived?))
     (comment-input member (str "#" id) endpoint tr))])

(ctmx/defcomponent ^:endpoint gigs-detail-page-comment [{:keys [db] :as req} gig]
  (let [comp-name              (util/comp-namer #'gigs-detail-page-comment)
        archived?              (domain/gig-archived? gig)
        current-user           (auth/get-current-member req)
        tr                     (i18n/tr-from-req req)
        {:gig/keys [comments]} (cond (util/post? req) (controller/post-comment! req)
                                     :else            gig)]
    (comment-section current-user archived? id (comp-name) tr comments)))

;;;; Setlist

(defn setlist-song-checkbox [selected-songs idx song]
  (let [this-song-id (str (:song/song-id song))
        {:keys [song-order]} (m/find-first #(= (:song-id %) this-song-id) selected-songs)]
    [:li
     [:label {:for this-song-id :class "rounded border-4 mx-0 my-1 p-2  basis-1/2 grid grid-flow-col grid-cols-auto justify-between"}
      [:span {:class "truncate"} (:song/title song)]
      [:input {:type :hidden :name (str idx "_songs_song-order")  :value (or song-order "9999")}]
      [:input {:type :checkbox :id this-song-id  :name (str idx "_songs_song-id") :value this-song-id
               :checked (some? song-order)}]]]))

(declare gig-setlist-sort)
(declare gigs-detail-page-setlist)

(ctmx/defcomponent ^:endpoint gig-setlist-choose-songs [{:keys [db] :as req}]
  (let [all-songs (q/find-all-songs db)
        comp-name (util/comp-namer #'gig-setlist-choose-songs)
        tr (i18n/tr-from-req req)]
    (if (util/put? req)
      (gig-setlist-sort req (util/unwrap-params req))
      (let [selected-songs (util/unwrap-params req)]
        (ui/panel {:title (tr [:gig/setlist]) :id "setlist-container"
                   :buttons (ui/button :class "pulse-delay" :label (tr [:action/save]) :priority :primary :form id)}
                  [:form {:hx-target "#setlist-container" :hx-put (comp-name) :id id}
                   [:ul {:class "p-0 m-0 grid grid-cols-2 md:grid-cols-2 lg:grid-cols-3 gap-y-0 md:gap-x-2"}
                    (map-indexed (partial setlist-song-checkbox selected-songs) all-songs)]])))))

(ctmx/defcomponent ^:endpoint  gig-setlist-sort [{:keys [db] :as req} ^:array selected-songs]
  (let [tr (i18n/tr-from-req req)
        song-ids (->>  (or selected-songs (-> req util/json-params :songs))
                       (filter :song-id)
                       (sort-by :song-order)
                       (map :song-id)
                       (map util/ensure-uuid))
        songs (util/index-sort-by song-ids :song/song-id (q/find-songs db song-ids))]
    (ui/panel {:title (tr [:gig/setlist]) :id "setlist-container"
               :buttons (ui/button :class "pulse-delay" :label (tr [:action/done]) :priority :primary :form id)}
              [:form {:class "w-full" :hx-post (util/comp-name #'gigs-detail-page-setlist) :hx-target "#setlist-container" :id id}
               [:p "Drag the songs into setlist order."]
               [:div {:class "htmx-indicator pulsate"} (tr [:updating])]
               [:ul {:class "sortable p-0 m-0 grid grid-cols-1 gap-y-0 md:gap-x-2 max-w-sm"}
                (map (fn [{:song/keys [song-id title]}]
                       [:li {:class "rounded border-4 mx-0 my-1 p-2  basis-1/2 grid grid-flow-col grid-cols-auto justify-between"}
                        [:div {:class "drag-handle cursor-pointer"} (icon/bars {:class "h-5 w-5"})]
                        [:input {:type :hidden :name "song-ids" :value song-id}]
                        title]) songs)]])))

(defn gigs-detail-page-setlist-list-ro [selected-songs]
  [:ul {:class "list-disc" :hx-boost true}
   (map (fn [{:song/keys [title] :as song}]
          [:li {:class "ml-4"}
           [:a {:href (url/link-song song) :class "link-blue"}
            title]]) selected-songs)])

;;;; Setlist editing Flow:
;; setlist --[Edit]-----> choose songs --[Save]--> order --[Done]--> setlist (repeat)
;;        \--[Reorder]---------------------------/
;;

(defn serialize-selected-songs [selected-songs]
  (map-indexed (fn [idx {:keys [song-order song-id]}]
                 (list
                  [:input {:type :hidden :name (str idx "_songs_song-order")  :value (or song-order idx)}]
                  [:input {:type :hidden :name (str idx "_songs_song-id") :value song-id}]))
               selected-songs))

(ctmx/defcomponent ^:endpoint  gigs-detail-page-setlist [{:keys [db] :as req} ^:array song-ids]
  gig-setlist-choose-songs
  gig-setlist-sort
  (let [comp-name (util/comp-namer #'gigs-detail-page-setlist)
        tr (i18n/tr-from-req req)
        song-ids (map util/ensure-uuid song-ids)
        songs (util/index-sort-by song-ids :song/song-id (if (util/post? req)
                                                           (controller/update-setlist! req song-ids)
                                                           (q/find-songs db song-ids)))
        selected-songs (map-indexed (fn [idx song] {:song-order idx :song-id (-> song :song/song-id str)}) songs)]
    (ui/panel {:title (tr [:gig/setlist]) :id "setlist-container"
               :buttons (when (seq songs)
                          (list
                           [:form {:hx-post (util/comp-name #'gig-setlist-choose-songs)  :hx-target "#setlist-container"}
                            (serialize-selected-songs selected-songs)
                            (ui/button :label (tr [:action/edit]) :priority :white)]
                           (list
                            [:form {:hx-post (util/comp-name #'gig-setlist-sort)  :hx-target "#setlist-container"}
                             (serialize-selected-songs selected-songs)
                             (ui/button :label (tr [:action/reorder]) :priority :white)])))}
              [:div {:id "setlist-container" :class ""}
               (if (empty? songs)
                 [:div {:class "flex flex-col items-center justify-center"}
                  [:div
                   (ui/button :label (tr [:gig/create-setlist]) :priority :primary :icon icon/plus
                              :attr {:hx-get (util/comp-name #'gig-setlist-choose-songs)
                                     :hx-vals {:target (hash ".") :post (comp-name)} :hx-target "#setlist-container"})]]
                 (gigs-detail-page-setlist-list-ro songs))])))
;;;; Probeplan
;;; The flow is the same as the setlist, with the addition that in the drop phase we set the emphasis
(defn probeplan-song-checkbox [selected-songs idx song]
  (let [this-song-id (str (:song/song-id song))
        {:keys [position emphasis] :as d} (m/find-first #(= (:song-id %) this-song-id) selected-songs)]
    [:li
     [:label {:for this-song-id :class "rounded border-4 mx-0 my-1 p-2  basis-1/2 grid grid-flow-col grid-cols-auto justify-between"}
      [:span {:class "truncate"} (:song/title song)]
      [:input {:type :hidden :name (str idx "_songs_position")  :value (or position "9999")}]
      [:input {:type :hidden :name (str idx "_songs_emphasis")  :value emphasis}]
      [:input {:type :checkbox :id this-song-id  :name (str idx "_songs_song-id") :value this-song-id
               :data-maximum 5 :data-checkbox-limit true
               :_ "on click
                     if checkboxLimitReached()
                       halt the event
                       then add .shake-horizontal to <p.probeplan-instruction/>
                       then settle then remove .shake-horizontal from <p.probeplan-instruction/>"
               :checked (some? position)}]]]))

(declare gig-probeplan-sort)
(declare gigs-detail-page-probeplan)

(ctmx/defcomponent ^:endpoint gig-probeplan-choose-songs [{:keys [db] :as req}]
  (let [all-songs (q/find-all-songs db)
        comp-name (util/comp-namer #'gig-probeplan-choose-songs)
        tr (i18n/tr-from-req req)]
    (if (util/put? req)
      (gig-probeplan-sort req (util/unwrap-params req))
      (let [selected-songs (util/unwrap-params req)]
        (ui/panel {:title (tr [:gig/probeplan]) :id "probeplan-container"
                   :buttons (ui/button :class "pulse-delay" :label (tr [:action/save]) :priority :primary :form id)}
                  [:form {:hx-target "#probeplan-container" :hx-put (comp-name) :id id}
                   [:p {:class "probeplan-instruction mb-2 text-xl"} (tr [:gig/probeplan-choose])]
                   [:ul {:class "p-0 m-0 grid grid-cols-2 md:grid-cols-2 lg:grid-cols-3 gap-y-0 md:gap-x-2"}
                    (map-indexed (partial probeplan-song-checkbox selected-songs) all-songs)]])))))

(ctmx/defcomponent  gig-probeplan-sort-item [req idx {:song/keys [song-id title] :keys [position emphasis] :as arg}]
  (let [checked? (= :probeplan.emphasis/intensive (probeplan.domain/str->play-emphasis emphasis))]
    [:li {:class "rounded border-4 mx-0 my-1 p-2  basis-1/2 grid grid-flow-col grid-cols-auto justify-between items-center"}
     [:div {:class "drag-handle cursor-pointer"} (icon/bars {:class "h-5 w-5"})]
     [:input {:type :hidden :name (path "song-id") :value song-id}]
     [:input {:type :hidden :name (path "position") :value idx :data-sort-order true}]
     title
     [:div  {:class "border-l-4  border-gray-200 ml-2 pl-2 mt-1 flex items-center space-x-3"}
      [:label  {:for id :class (ui/cs "icon-fist-punch cursor-pointer" (when checked? "intensiv--checked"))}
       [:input {:type "checkbox" :class "sr-only" :name (path "emphasis") :id id :value (or emphasis probeplan.domain/probeplan-classic-default-emphasis)
                :_ "on change if I match <:checked/>
                                                add .intensiv--checked to the closest parent <label/>
                                                else
                                                remove .intensiv--checked from the closest parent <label/>
                                              end"
                :checked checked?}]
       (icon/fist-punch {:class "h-8 w-8"})]]]))

(ctmx/defcomponent ^:endpoint  gig-probeplan-sort [{:keys [db] :as req} ^:array selected-songs]
  (let [tr (i18n/tr-from-req req)
        selected-songs (->>  (or selected-songs (-> req util/json-params :songs))
                             (filter :song-id)
                             (map #(update % :position (fn [v] (Integer/parseInt v))))
                             (map #(update % :song-id util/ensure-uuid)))
        selected-songs (->> (clojure.set/join selected-songs (q/find-songs db (map :song-id selected-songs))  {:song-id :song/song-id})
                            (into [])
                            (sort-by :position))]
    (ui/panel {:title (tr [:gig/probeplan]) :id "probeplan-container"
               :buttons (ui/button :class "pulse-delay" :label (tr [:action/done]) :priority :primary :form id)}
              [:form {:class "w-full" :hx-post (util/comp-name #'gigs-detail-page-probeplan) :hx-target "#probeplan-container" :id id}
               [:p (tr [:gig/probeplan-sort])]
               [:div {:class "htmx-indicator pulsate"} (tr [:updating])]
               [:ul {:class "sortable p-0 m-0 grid grid-cols-1 gap-y-0 md:gap-x-2 max-w-sm"}
                (rt/map-indexed gig-probeplan-sort-item req selected-songs)]])))

(defn gigs-detail-page-probeplan-list-ro [selected-songs]
  [:div {:class "mt-1 grid grid-cols-1 gap-4 sm:grid-cols-2" :hx-boost true}
   (map (fn [{:song/keys [title] :keys [emphasis] :as song}]
          (let [intensive? (= emphasis :probeplan.emphasis/intensive)]
            [:div {:class
                   (ui/cs
                    "relative flex items-center space-x-3 rounded-lg px-3 py-2 shadow-sm focus-within:ring-2 focus-within:ring-pink-500 focus-within:ring-offset-2 hover:border-gray-400"
                    (if intensive? "bg-purple-200 border border-purple-500" "bg-white border border-gray-300 "))}
             (when intensive?
               [:div {:class "flex-shrink-0"}
                (icon/fist-punch {:class "h-10 w-10 text-purple-600"})])
             [:div {:class "min-w-0 flex-1"}
              [:a {:href (url/link-song song) :class "focus:outline-none"  :hx-boost true}
               [:span {:class "absolute inset-0" :aria-hidden "true"}]
               [:p {:class "text-sm font-medium text-gray-900"} title]
               [:p {:class "truncate text-sm text-gray-500"} "Last Played: never"]]]]))
        selected-songs)])

(defn serialize-probeplan-selected-songs [songs]
  (map-indexed (fn [idx {:song/keys [song-id] :keys [position emphasis]}]
                 (list
                  [:input {:type :hidden :name (str idx "_songs_position")  :value (or position idx)}]
                  [:input {:type :hidden :name (str idx "_songs_emphasis")  :value (or emphasis probeplan.domain/probeplan-classic-default-emphasis)}]
                  [:input {:type :hidden :name (str idx "_songs_song-id") :value song-id}]))
               songs))

(ctmx/defcomponent ^:endpoint  gigs-detail-page-probeplan [{:keys [db] :as req}]
  gig-probeplan-choose-songs
  gig-probeplan-sort
  (let [comp-name (util/comp-namer #'gigs-detail-page-probeplan)
        tr (i18n/tr-from-req req)
        ;; archived? (domain/gig-archived? gig)
        songs (if (util/post? req)
                (controller/update-probeplan! req (util/unwrap-params req))
                (q/probeplan-songs-for-gig db (-> req :path-params :gig/gig-id)))
        ;; selected-songs (map-indexed (fn [idx song] {:song-order idx :song-id (-> song :song/song-id str)}) songs)
        ]
    (ui/panel {:title (tr [:gig/probeplan]) :id "probeplan-container"
               :buttons (when (seq songs)
                          (list
                           [:form {:hx-post (util/comp-name #'gig-probeplan-choose-songs)  :hx-target "#probeplan-container"}
                            (serialize-probeplan-selected-songs songs)
                            (ui/button :label (tr [:action/edit]) :priority :white)]
                           (list
                            [:form {:hx-post (util/comp-name #'gig-probeplan-sort)  :hx-target "#probeplan-container"}
                             (serialize-probeplan-selected-songs songs)
                             (ui/button :label (tr [:action/reorder]) :priority :white)])))}
              [:div {:id "probeplan-container" :class ""}
               (if (empty? songs)
                 [:div {:class "flex flex-col items-center justify-center"}
                  [:div
                   (ui/button :label (tr [:gig/create-probeplan]) :priority :primary :icon icon/plus
                              :attr {:hx-get (util/comp-name #'gig-probeplan-choose-songs)
                                     :hx-vals {:target (hash ".") :post (comp-name)} :hx-target "#probeplan-container"})]]
                 (gigs-detail-page-probeplan-list-ro songs))])
     ;;;
    #_(ui/panel {:title (tr [:gig/probeplan])
                 :buttons (ui/button :tag :a :label "Edit" :priority :white :attr {:href (url/link-probeplan-home) :hx-boost true})}
                [:div {:class "max-w-lg"}])))

(defn gig-form [{:gig/keys [title date end-date status gig-type
                            contact pay-deal call-time set-time end-time
                            outfit description location setlist leader post-gig-plans
                            more-details] :as gig} archived? comp-name path tr
                member-select-vals]
  [:div
   (ui/page-header-full :title
                        (ui/text :label "Title" :name (path "title") :value title)
                        :subtitle
                        (list
                         (ui/select
                          :label (tr [:gig/status])
                          :id (path "status")
                          :value (when status (name status))
                          :size :small
                          :required? true
                          :options (map (fn [m] {:label (tr [m]) :value (name m)}) domain/statuses))
                         (ui/select
                          :id (path "gig-type")
                          :label (tr [:gig/gig-type])
                          :value (when gig-type (name gig-type))
                          :size :small
                          :required? true
                          :options
                          (if (nil? gig)
                            (concat [{:label "-" :value ""}]
                                    (map (fn [m] {:label (tr [m]) :value (name m)}) domain/gig-types))
                            (map (fn [m] {:label (tr [m]) :value (name m)}) domain/gig-types)))
                         (ui/checkbox :label (tr [:gig/email-about-change?]) :id (path "notify?")))

                        :buttons (when-not archived?
                                   (list
                                    (ui/button :label (tr [:action/cancel])
                                               :priority :white
                                               :centered? true
                                               :class "items-center justify-center"
                                               :attr {:hx-get (comp-name) :hx-target (hash ".") :hx-vals {"edit?" false}})
                                    (ui/button :label (tr [:action/save])
                                               :priority :primary
                                               :centered? true
                                               :class "items-center justify-center"
                                               :attr {:hx-target (hash ".")}))))
   [:div {:class "mx-auto mt-8 grid max-w-3xl grid-cols-1 gap-6 sm:px-6 lg:max-w-7xl lg:grid-flow-col-dense lg:grid-cols-3"}
    [:div {:class "space-y-6 lg:col-span-3 lg:col-start-1"}
     [:section
      [:div {:class "bg-white shadow sm:rounded-lg"}
       [:div {:class "px-4 py-5 sm:px-6"}
        [:h2 {:class "text-lg font-medium leading-6 text-gray-900"}
         (tr [:gig/gig-info])]]
       [:div {:class "border-t border-gray-200 px-4 py-5 sm:px-6"}
        [:dl {:class "grid grid-cols-1 gap-x-4 gap-y-8 sm:grid-cols-3"}
         (list
          (ui/dl-item (tr [:gig/date])
                      (if (nil? gig)
                        (ui/date :value date :name (path "date") :required? true :min (str (t/date)))
                        (ui/date :value date :name (path "date") :required? true)))
          (ui/dl-item (tr [:gig/end-date])
                      (if (nil? gig)
                        (ui/date :value date :name (path "end-date") :required? false :min (str (t/tomorrow)))
                        (ui/date :value date :name (path "end-date") :required? false)))
          (ui/dl-item (tr [:gig/contact]) (ui/member-select :value (:member/gigo-key contact) :label "" :id (path "contact") :members member-select-vals))
          (ui/dl-item (tr [:gig/call-time]) (ui/input-time :value call-time :name (path "call-time") :required? true))
          (ui/dl-item (tr [:gig/set-time]) (ui/input-time :value set-time :name (path "set-time") :required? false))
          (ui/dl-item (tr [:gig/end-time]) (ui/input-time :value end-time :name (path "end-time") :required? false))
          (ui/dl-item (tr [:gig/location]) (ui/text :value location :name (path "location")))
          (ui/dl-item (tr [:gig/outfit]) (ui/text :value outfit :name (path "outfit") :required? false))
          (ui/dl-item (tr [:gig/pay-deal]) (ui/text :value pay-deal :name (path "pay-deal") :required? false))
          (ui/dl-item (tr [:gig/leader]) (ui/text :label "" :value leader :name (path "leader") :required? false))
          (ui/dl-item (tr [:gig/post-gig-plans]) (ui/text :value post-gig-plans :name (path "post-gig-plans") :required? false) "sm:col-span-2")
          (ui/dl-item (tr [:gig/more-details]) (ui/textarea :value more-details :name (path "more-details") :required? false :placeholder (tr [:gig/more-details-placeholder])) "sm:col-span-3")
          (ui/dl-item (tr [:gig/setlist]) (ui/textarea :value setlist :name (path "setlist") :required? false) "sm:col-span-3")
          (ui/dl-item (tr [:gig/description]) (ui/textarea :value description :name (path "description") :required? false) "sm:col-span-3")
          ;;
          )]]]]]]])
(ctmx/defcomponent ^:endpoint  gigs-detail-page-info [{:keys [db] :as req} gig ^:boolean edit?]
  (let [comp-name (util/comp-namer #'gigs-detail-page-info)
        tr (i18n/tr-from-req req)
        archived? (domain/gig-archived? gig)
        gig (cond (and (not archived?) (util/post? req)) (:gig (controller/update-gig! req))
                  :else (:gig req))]

    (if edit?
      [:form {:id id :hx-post (comp-name)}
       (gig-form gig archived? comp-name path tr (q/members-for-select-active db))]
      (let [{:gig/keys [title date end-date status gig-type
                        contact pay-deal call-time set-time end-time
                        outfit description location setlist leader post-gig-plans
                        more-details]} gig]
        [:div {:id id}
         (ui/page-header :title (list  title " " (ui/gig-status-icon status))
                         :subtitle (tr [gig-type])
                         :buttons (when-not archived?
                                    (list
                                     (ui/button :label (tr [:action/edit])
                                                :priority :white
                                                :centered? true
                                                :class "items-center justify-center"
                                                :attr {:hx-get (comp-name) :hx-target (hash ".") :hx-vals {"edit?" true}})
                                     (ui/button :label "Log Plays"
                                                :tag :a
                                                :priority :primary
                                                :centered? true
                                                :class "items-center justify-center"
                                                :attr {:href (url/link-gig gig "/log-play/")}))))
         [:div {:class "mx-auto mt-8 grid max-w-3xl grid-cols-1 gap-6 sm:px-6 lg:max-w-7xl lg:grid-flow-col-dense lg:grid-cols-3"}
          [:div {:class "space-y-6 lg:col-span-3 lg:col-start-1"}
           [:section
            [:div {:class "bg-white shadow sm:rounded-lg"}
             [:div {:class "px-4 py-5 sm:px-6"}
              [:h2 {:class "text-lg font-medium leading-6 text-gray-900"}
               (tr [:gig/gig-info])]]
             [:div {:class "border-t border-gray-200 px-4 py-5 sm:px-6"}
              [:dl {:class "grid grid-cols-1 gap-x-4 gap-y-8 sm:grid-cols-3"}
               (list
                (ui/dl-item (tr [:gig/date])
                            (ui/datetime date)
                            (when end-date
                              [:span " " (ui/datetime end-date)]))
                (ui/dl-item (tr [:gig/location]) location)
                (ui/dl-item (tr [:gig/contact]) (ui/member-nick contact))
                (ui/dl-item (tr [:gig/call-time]) (ui/time call-time))
                (ui/dl-item (tr [:gig/set-time]) (ui/time set-time))
                (ui/dl-item (tr [:gig/end-time]) (ui/time end-time))
                (when-not (str/blank? leader)
                  (ui/dl-item (tr [:gig/leader]) leader))
                (when-not (str/blank? pay-deal)
                  (ui/dl-item (tr [:gig/pay-deal]) pay-deal))
                (when-not (str/blank? outfit)
                  (ui/dl-item (tr [:gig/outfit]) outfit))
                (when-not (str/blank? more-details)
                  (ui/dl-item (tr [:gig/more-details]) more-details "sm:col-span-3"))
                (when-not (str/blank? setlist)
                  (ui/dl-item (tr [:gig/setlist]) (interpose [:br] (str/split-lines setlist)) "sm:col-span-3"))
                (when-not (str/blank? post-gig-plans)
                  (ui/dl-item (tr [:gig/post-gig-plans]) post-gig-plans "sm:col-span-3")))
               ;;
               ]]]]]]]))))
(ctmx/defcomponent ^:endpoint gigs-detail-page [{:keys [db] :as req} ^:boolean show-committed?]
  (let [{:gig/keys [gig-id gig-type] :as gig} (:gig req)
        comp-name (util/comp-namer #'gigs-detail-page)
        tr (i18n/tr-from-req req)
        archived? (domain/gig-archived? gig)
        probe? (#{:gig.type/probe :gig.type/extra-probe} gig-type)
        gig? (#{:gig.type/gig} gig-type)
        scheduled-songs (when gig?
                          (q/setlist-song-ids-for-gig db gig-id))
        attendances-by-section (if archived?
                                 (q/attendance-plans-by-section-for-gig db gig-id (q/attendances-for-gig db gig-id) false)
                                 (q/attendance-plans-by-section-for-gig db gig-id (q/attendance-for-gig-with-all-active-members db gig-id) show-committed?))]

    [:div {:id id}
     (gigs-detail-page-info req gig false)
     (cond probe?
           (gigs-detail-page-probeplan req)
           gig?
           (gigs-detail-page-setlist req scheduled-songs)
           :else nil)
     [:div {:class "mx-auto mt-8 grid max-w-3xl grid-cols-1 gap-6 sm:px-6 lg:max-w-7xl lg:grid-flow-col-dense lg:grid-cols-3"}
      [:div {:class "space-y-6 lg:col-span-3 lg:col-start-1"}
;;;; Attendance Section
       [:section
        [:div {:class "bg-white shadow sm:rounded-lg"}
         [:div {:class "px-4 py-5 sm:px-6 flex flex-row flex-items-center justify-between"}
          [:h2 {:class "text-lg font-medium leading-6 text-gray-900"}
           (tr [:gig/attendance])]
          [:div
           (ui/button :label (if show-committed?
                               (tr [:gig/show-all])
                               (tr [:gig/show-committed]))
                      :size :xsmall
                      :priority :primary
                      :centered? true
                      :attr  {:hx-get (comp-name) :hx-target (hash ".") :hx-vals {:show-committed? (not show-committed?)}})]]

         [:div {:class "border-t border-gray-200"}
          (if archived?
            (rt/map-indexed gig-attendance-archived req attendances-by-section)
            (rt/map-indexed gig-attendance req attendances-by-section))]]]
;;;; Comments Section
       [:section {:aria-labelledby "notes-title"}
        [:div {:class "bg-white shadow sm:overflow-hidden sm:rounded-lg"}
         (gigs-detail-page-comment req gig)]]]]]))

;;;; Create Gig
(ctmx/defcomponent ^:endpoint gig-create-page [{:keys [db] :as req}]
  (tap> {:params (:params req)})
  (let [tr (i18n/tr-from-req req)
        comp-name (util/comp-namer #'gig-create-page)]
    (if (util/post? req)
      (let [new-gig (:gig (controller/create-gig! req))]
        (tap> {:new-gig new-gig})
        (response/hx-redirect (url/link-gig new-gig)))

      [:form {:hx-post (comp-name) :class "space-y-8 divide-y divide-gray-200"}
       (gig-form nil false comp-name path tr (q/members-for-select-active db))])))

(ctmx/defcomponent ^:endpoint gigs-list-page [{:keys [db] :as req}]
  (let [future-gigs (controller/gigs-future db)
        offset 0
        limit 10
        past-gigs (controller/gigs-past-page db offset limit)
        ;; past-gigs (controller/gigs-past-two-weeks db)
        tr (i18n/tr-from-req req)]
    [:div
     (ui/page-header :title (tr [:gigs/title])
                     :buttons  (list
                                (ui/button :tag :a :label (tr [:action/create])
                                           :priority :primary
                                           :centered? true
                                           :attr {:hx-boost true :href (url/link-gig-create)} :icon icon/plus)))

     [:div {:class "mt-6 px-4 sm:px-6 md:px-8 md:flex md:flex-row md:space-x-4"}
      [:div {:class "max-w-lg"}
       (ui/divider-left (tr [:gigs/upcoming]))
       [:div {:class "overflow-hidden bg-white shadow sm:rounded-md mb-8"}
        (if (empty? future-gigs)
          (tr [:gigs/no-future])
          [:ul {:role "list" :class "divide-y divide-gray-200"}
           (map (fn [gig]
                  [:li
                   (gig-row gig)]) future-gigs)])]]
      [:div {:class "max-w-lg"}
       (ui/divider-left (tr [:gigs/past]))
       [:div {:class "overflow-hidden bg-white shadow sm:rounded-md"}
        (if (empty? past-gigs)
          (tr [:gigs/no-past])
          [:ul {:role "list", :class "divide-y divide-gray-200"}
           (map (fn [gig]
                  [:li
                   (gig-row gig)]) past-gigs)])]]]]))
