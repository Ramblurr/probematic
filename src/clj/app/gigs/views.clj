(ns app.gigs.views
  (:refer-clojure :exclude [comment])
  (:require
   [app.debug :as debug]
   [app.gigs.controller :as controller]
   [app.gigs.domain :as domain]
   [app.i18n :as i18n]
   [app.icons :as icon]
   [app.queries :as q]
   [app.render :as render]
   [app.urls :as url]
   [app.util :as util]
   [app.views.shared :as ui]
   [clojure.string :as str]
   [ctmx.core :as ctmx]
   [ctmx.rt :as rt]
   [medley.core :as m]
   [tick.core :as t]
   [app.auth :as auth]))

(defn radio-button  [idx {:keys [id name label value opt-id icon size class icon-class model disabled? required? data]
                          :or {size :normal
                               class ""
                               required? false
                               icon-class ""
                               disabled? false}}]
  (let [checked? (= model value)]
    [:label {:id id
             :class
             (render/cs
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
      (icon {:class (render/cs "h-8 w-8" icon-class)})]]))

(defn radio-button-group [& {:keys [options id label class value required?]
                             :or {class ""
                                  required? false}}]
  [:fieldset
   [:legend {:class ""} label]
   [:div {:class (render/cs "mt-1 flex items-center space-x-3" class)}
    (->> options
         (map #(merge {:name id :model value :required? required?} %))
         (map-indexed radio-button))]])

(defn log-play-legend []
  (let [shared-classes "h-8 w-8 border-solid border-b-2 pb-1"]
    [:div {:class ""}
     [:h3 {:class "font-bold"}
      "Legend"]

     [:div {:class "flex flex-col sm:flex-row space-y-4 sm:space-y-0 sm:space-x-4 "}
      [:div {:class "flex items-center justify-start"}
       (icon/circle-xmark-outline {:class (render/cs shared-classes "icon-not-played border-gray-500")})
       "Not Played"]
      [:div {:class "flex items-center justify-start"}
       (icon/smile {:class (render/cs shared-classes "icon-smile border-green-500")})
       "Nice!"]
      [:div {:class "flex items-center justify-start"}
       (icon/meh {:class (render/cs shared-classes "icon-meh border-blue-500")})
       "Okay"]
      [:div {:class "flex items-center justify-start"}
       (icon/sad {:class (render/cs shared-classes "icon-sad border-red-500")})
       "Uh-oh"]
      [:div {:class "flex justify-start items-center"}
       (icon/fist-punch {:class (render/cs shared-classes "icon-fist-punch border-purple-500")})
       "Intensiv geprobt"]]]))

(ctmx/defcomponent gig-log-play [{:keys [db] :as req} idx play]
  (let [was-played? (some? (:played/play-id play))
        check-id (path "intensive")
        radio-id (path "feeling")
        _ (value "foo")
        song (:played/song play)
        song-id (:song/song-id song)
        feeling-value (util/kw->str (or (:played/rating play) :play-rating/not-played))
        intensive?  (= :play-emphasis/intensiv (:played/emphasis play))
        toggler-class (get {"play-rating/not-played" "toggler--not-played"
                            "play-rating/good" "toggler--feeling-good"
                            "play-rating/bad" "toggler--feeling-sad"
                            "play-rating/ok" "toggler--feeling-meh"} feeling-value)]
    [:li {:class (render/cs "toggler" toggler-class)}
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
       [:label  {:for check-id :class (render/cs "icon-fist-punch cursor-pointer" (when intensive? "intensiv--checked"))}
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
  (ctmx/with-req req
    (let [gig-id (-> req :path-params :gig/gig-id)
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

      [:form {:id id :hx-post (path ".")
              :class "space-y-4"}

       (render/page-header :title (list  (:gig/title gig) " (" (ui/datetime (:gig/date gig)) ")") :subtitle "Here you can record what was played at this gig/probe.")

       (log-play-legend)
       [:ul {:class "toggler-container"}
        (rt/map-indexed gig-log-play req plays)]
       [:div
        [:div {:class "flex justify-end"}
         [:a {:href "/events", :class "btn btn-sm btn-clear-normal"} "Cancel"]
         [:button {:class "ml-3 btn btn-sm btn-indigo-high"
                   :type "submit"} "Save"]]]])))

(defn gig-row [{:gig/keys [status title location date gig-id] :as gig}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"]
    [:a {:href (url/link-gig gig) :class "block hover:bg-gray-50"}
     [:div {:class "px-4 py-4 sm:px-6"}
      [:div {:class "flex items-center justify-between"}
       [:p {:class "truncate text-sm font-medium text-indigo-600"}
        (ui/gig-status-icon status)
        title]
       [:div {:class "ml-2 flex flex-shrink-0"}
        (when (domain/in-future? gig)
          (render/button :tag :a :attr {:href (url/link-gig gig "/log-play/")}
                         :label "Log Plays" :priority :white-rounded :size :small))]]

      [:div {:class "mt-2 sm:flex sm:justify-between"}
       [:div {:class "flex"}
        [:p {:class "flex items-center text-sm text-gray-500"}
         (icon/location-dot {:class style-icon})
         location]
        [:p {:class "mt-2 flex items-center text-sm text-gray-500 mt-0 ml-6"}

         (icon/calendar {:class style-icon})
         (ui/datetime date)]]

       [:div {:class "mt-2 flex items-center text-sm text-gray-500 sm:mt-0"}
                                        ;(icon/calendar {:class style-icon})
                                        ;[:p "Last Played "]
        ]]]]))

(defn attendance-opts [tr]
  (let [icon-class "mr-3 text-gray-400 w-5 h-5"
        red-class "text-red-500 group-hover:text-red-500"
        green-class "text-green-500 group-hover:text-green-500"]
    [{:label (tr [:plan/definitely]) :value (name :plan/definitely)  :icon icon/circle :icon-class (render/cs icon-class green-class)}
     {:label (tr [:plan/probably]) :value (name :plan/probably) :icon icon/circle-outline  :icon-class (render/cs icon-class green-class)}
     {:label (tr [:plan/unknown]) :value (name :plan/unknown) :icon icon/question :icon-class (render/cs icon-class "text-gray-500")}
     {:label (tr [:plan/probably-not]) :value (name :plan/probably-not) :icon icon/square-outline :icon-class (render/cs icon-class red-class)}
     {:label (tr [:plan/definitely-not]) :value (name :plan/definitely-not)    :icon icon/square :icon-class (render/cs icon-class red-class)}
     {:label (tr [:plan/not-interested]) :value (name :plan/not-interested)    :icon icon/xmark :icon-class (render/cs icon-class "text-black")}]))

(defn attendance-dropdown-opt [{:keys [label value icon icon-class]}]
  [:a {:href "#" :data-value value :class "hover:bg-gray-200 text-gray-700 group flex items-center px-4 py-2 text-sm", :role "menuitem", :tabindex "-1", :id "menu-item-0"}
   (icon {:class  icon-class}) label])

(defn attendance-dropdown [& {:keys [gigo-key value tr]
                              :or {value "unknown"}}]
  (let [opts (attendance-opts tr)
        current-opt (m/find-first #(= value (:value %)) opts)
        button-size-class-normal "px-4 py-2 text-sm "
        button-size-class-small "px-2 py-1 text-xs "]
    [:div {:class "dropdown relative inline-block text-left"}
     [:div
      [:button {:type "button" :class (render/cs button-size-class-small "dropdown-button inline-flex w-full justify-center rounded-md border border-gray-300 bg-white font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 focus:ring-offset-gray-100")  :aria-expanded "true", :aria-haspopup "true"}
       [:input {:type "hidden" :name "gigo-key" :value gigo-key}]
       [:input {:type "hidden" :name "plan" :value (:value current-opt) :class "item-input"}]
       [:span {:class "item-icon"}
        ((:icon current-opt) {:class (render/cs (:icon-class current-opt) "w-3 h-3")})]
       (icon/chevron-down {:class (render/cs "-mr-1 ml-1 h-3 w-3")})]]
     [:div {:class "dropdown-menu-container hidden"}
      [:div {:class (render/cs
                     "dropdown-choices"
                     (when false "absolute right-0 ")
                     "z-10 w-56 origin-top-right divide-y divide-gray-100 rounded-md bg-white shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none")
             :role "menu" :aria-orientation "vertical" :aria-labelledby "menu-button" :tabindex "-1"}
       [:div {:class "py-1" :role "none"}
        (map attendance-dropdown-opt  opts)]]]]))

(ctmx/defcomponent ^:endpoint gig-attendance-person-plan [{:keys [db] :as req} gigo-key plan]
  (ctmx/with-req req
    (let [comp-name (util/comp-namer #'gig-attendance-person-plan)
          tr (i18n/tr-from-req req)
          plan-kw (or
                   (if post?
                     (-> (controller/update-attendance-plan! req) :attendance :attendance/plan)
                     plan)
                   :plan/unknown)
          body-result [:form {:hx-post (comp-name) :id id :hx-trigger "planChanged"} ;; this form is triggered by javascript
                       (attendance-dropdown :tr tr :gigo-key gigo-key :value (name plan-kw))]]
      (if post?
        (render/trigger-response "newDropdown" body-result
                                 {:trigger-type :hx-trigger-after-settle
                                  :data (str (hash ".") " .dropdown")})
        body-result))))

;; TODO WTF is up with (value ..) here and in the motivation component, why do i have to reach into unwrap params?
(ctmx/defcomponent ^:endpoint gig-attendance-person-comment [{:keys [db] :as req} gigo-key comment]
  (ctmx/with-req req
    (let [comp-name (util/comp-namer #'gig-attendance-person-comment)
          edit? (util/qp-bool req :edit)
          gigo-key (or gigo-key (value "gigo-key"))
          comment (if post?
                    (-> (controller/update-attendance-comment! req) :attendance :attendance/comment)
                    comment)]
      [:div {:id id :class ""}
       (if edit?
         (render/text :name (path "comment") :value comment :required? false :size :small
                      :extra-attrs {:hx-target (hash ".") :hx-post (comp-name) :hx-trigger "focusout, keydown[key=='Enter'] changed"  :autofocus true
                                    :hx-vals (render/hx-vals {(path "gigo-key") gigo-key})
                                    :_ "on focus or htmx:afterRequest or load
                                          set :initial_value to my value
                                        end
                                        on keyup[key=='Escape']
                                          set my value to the :initial_value then
                                          blur() me
                                        end"})

         (let [hx-attrs {:hx-target (hash ".") :hx-get (comp-name "?edit=true") :hx-vals (render/hx-vals {:gigo-key gigo-key :comment comment})}]
           (if comment
             [:span (merge hx-attrs {:class "ml-2 cursor-pointer text-blue-500 hover:text-blue-600"})
              comment]
             [:button  hx-attrs
              (icon/comment-outline {:class "ml-2 mb-2 w-5 h-5 cursor-pointer hover:text-blue-500 active:text-blue-300"})])))])))

(ctmx/defcomponent ^:endpoint gig-attendance-person-motivation [{:keys [db] :as req} gigo-key motivation]
  (ctmx/with-req req
    (let [comp-name (util/comp-namer #'gig-attendance-person-motivation)
          gigo-key (or gigo-key (value "gigo-key"))
          tr (i18n/tr-from-req req)
          motivation (if post?
                       (-> (controller/update-attendance-motivation! req) :attendance :attendance/motivation)
                       motivation)]
      [:form {:id id :hx-target (hash ".")}
       (render/select
        :id (path "motivation")
        :value (when motivation (name motivation))
        :size :small
        :extra-attrs {:hx-trigger "change"  :hx-post (comp-name) :hx-vals (render/hx-vals {(path "gigo-key") gigo-key})}
        :options (map (fn [m] {:label (tr [m]) :value (name m)}) domain/motivations))])))

(ctmx/defcomponent ^:endpoint gig-attendance-person [{:keys [db] :as req} idx attendance]
  (let [{:member/keys [gigo-key] :as member} (:attendance/member attendance)]
    [:div {:class "grid grid-cols grid-cols-5" :id id}
     [:div {:class "col-span-2 align-middle"}
      [:a {:href (url/link-member gigo-key) :class "text-blue-500 hover:text-blue-600 align-middle"}
       (render/member-nick member)]]
     [:div {:class "col-span-1"} (gig-attendance-person-plan req gigo-key (:attendance/plan attendance))]
     [:div {:class "col-span-1"} (gig-attendance-person-motivation req gigo-key (:attendance/motivation attendance))]
     [:div {:class "col-span-1 "} (gig-attendance-person-comment req gigo-key (:attendance/comment attendance))]]))

(defn gig-attendance-person-archived [{:keys [db] :as req} idx attendance]
  (let [{:member/keys [gigo-key] :as member} (:attendance/member attendance)]
    [:div {:class "grid grid-cols grid-cols-5"}
     [:div {:class "col-span-2 align-middle"}
      [:a {:href (url/link-member gigo-key) :class "text-blue-500 hover:text-blue-600 align-middle"}
       (render/member-nick member)]]
     [:div {:class "col-span-1"} (:attendance/plan attendance)]
     [:div {:class "col-span-1"} (:attendance/motivation attendance)]
     [:div {:class "col-span-1 "} (:attendance/comment attendance)]]))

(ctmx/defcomponent ^:endpoint gig-attendance [{:keys [db] :as req} idx section]
  [:div {:id id :class (render/cs "grid grid-cols-3 gap-x-0 gap-y-8 mb-2 px-4 py-0 sm:px-6"
                                  (when (= 0 (mod idx 2))
                                    "bg-gray-100"))}

   [:div {:class "col-span-1 font-bold"} (:section/name section)]
   [:div {:class "col-span-2"}
    (rt/map-indexed gig-attendance-person req (:members section))]])

(ctmx/defcomponent ^:endpoint gig-attendance-archived [{:keys [db] :as req} idx section]
  [:div {:id id :class (render/cs "grid grid-cols-3 gap-x-0 gap-y-8 mb-2 px-4 py-0 sm:px-6"
                                  (when (= 0 (mod idx 2))
                                    "bg-gray-100"))}

   [:div {:class "col-span-1 font-bold"} (:section/name section)]
   [:div {:class "col-span-2"}
    (rt/map-indexed gig-attendance-person-archived req (:members section))]])

(defn gig-comment-li [comment]
  (let [{:comment/keys [body author created-at]} comment]
    [:li
     [:div {:class "flex space-x-3"}
      [:div {:class "flex-shrink-0"}
       [:a {:href (url/link-member author)}
        (render/avatar-img author :class "h-10 w-10 rounded-full")]]
      [:div
       [:div {:class "text-sm"}
        [:a {:href (url/link-member author) :class "font-medium text-gray-900"}
         (render/member-nick author)]]
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
     (render/avatar-img member :class "h-10 w-10 rounded-full")]
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

(ctmx/defcomponent ^:endpoint  gigs-detail-page-info [{:keys [db] :as req} gig ^:boolean edit?]
  (let [comp-name (util/comp-namer #'gigs-detail-page-info)
        tr (i18n/tr-from-req req)
        archived? (domain/gig-archived? gig)
        gig (cond (and (not archived?) (util/post? req)) (:gig (controller/update-gig! req))
                  :else (:gig req))
        {:gig/keys [gig-id title date end-date status gig-type
                    contact pay-deal call-time set-time end-time
                    outfit description location setlist leader post-gig-plans
                    more-details]} gig]

    [:form  {:id id :hx-post (comp-name)}
     (if edit?
       (render/page-header-full :title
                                (render/text :label "Title" :name (path "title") :value title)
                                :subtitle
                                (list
                                 (render/select
                                  :label (tr [:gig/status])
                                  :id (path "status")
                                  :value (when status (name status))
                                  :size :small
                                  :options (map (fn [m] {:label (tr [m]) :value (name m)}) domain/statuses))
                                 (render/select
                                  :id (path "gig-type")
                                  :label (tr [:gig/gig-type])
                                  :value (when gig-type (name gig-type))
                                  :size :small
                                  :options (map (fn [m] {:label (tr [m]) :value (name m)}) domain/gig-types))
                                 (render/checkbox :label (tr [:gig/email-about-change?]) :id (path "notify?")))

                                :buttons (when-not archived?
                                           (list
                                            (render/button :label (tr [:action/cancel])
                                                           :priority :white
                                                           :centered? true
                                                           :class "items-center justify-center"
                                                           :attr {:hx-get (comp-name) :hx-target (hash ".") :hx-vals {"edit?" false}})
                                            (render/button :label (tr [:action/save])
                                                           :priority :primary
                                                           :centered? true
                                                           :class "items-center justify-center"
                                                           :attr {:hx-target (hash ".")}))))
       (render/page-header :title (list  title " " (ui/gig-status-icon status))
                           :subtitle (tr [gig-type])
                           :buttons (when-not archived?
                                      (list
                                       (render/button :label (tr [:action/edit])
                                                      :priority :white
                                                      :centered? true
                                                      :class "items-center justify-center"
                                                      :attr {:hx-get (comp-name) :hx-target (hash ".") :hx-vals {"edit?" true}})
                                       (render/button :label "Log Plays"
                                                      :tag :a
                                                      :priority :primary
                                                      :centered? true
                                                      :class "items-center justify-center"
                                                      :attr {:href (url/link-gig gig "/log-play/")})))))
     [:div {:class "mx-auto mt-8 grid max-w-3xl grid-cols-1 gap-6 sm:px-6 lg:max-w-7xl lg:grid-flow-col-dense lg:grid-cols-3"}
      [:div {:class "space-y-6 lg:col-span-2 lg:col-start-1"}
       [:section
        [:div {:class "bg-white shadow sm:rounded-lg"}
         [:div {:class "px-4 py-5 sm:px-6"}
          [:h2 {:class "text-lg font-medium leading-6 text-gray-900"}
           (tr [:gig/gig-info])]]
         [:div {:class "border-t border-gray-200 px-4 py-5 sm:px-6"}
          [:dl {:class "grid grid-cols-1 gap-x-4 gap-y-8 sm:grid-cols-3"}
           (if (and (not archived?) edit?)
             (list
              (render/dl-item (tr [:gig/date]) (render/date :value date :name (path "date")))
              (render/dl-item (tr [:gig/end-date]) (render/date :value date :name (path "end-date") :required? false))
              (render/dl-item (tr [:gig/contact]) (render/member-select :value (:member/gigo-key contact) :label "" :id (path "contact") :members (q/members-for-select-active db)))
              (render/dl-item (tr [:gig/call-time]) (render/time :value call-time :name (path "call-time")))
              (render/dl-item (tr [:gig/set-time]) (render/time :value set-time :name (path "set-time") :required? false))
              (render/dl-item (tr [:gig/end-time]) (render/time :value end-time :name (path "end-time") :required? false))
              (render/dl-item (tr [:gig/location]) (render/text :value location :name (path "location")))
              (render/dl-item (tr [:gig/outfit]) (render/text :value outfit :name (path "outfit") :required? false))
              (render/dl-item (tr [:gig/pay-deal]) (render/text :value pay-deal :name (path "pay-deal") :required? false))
              (render/dl-item (tr [:gig/leader]) (render/text :label "" :value leader :name (path "leader") :required? false))
              (render/dl-item (tr [:gig/post-gig-plans]) (render/text :value post-gig-plans :name (path "post-gig-plans") :required? false) "sm:col-span-2")
              (render/dl-item (tr [:gig/more-details]) (render/textarea :value more-details :name (path "more-details") :required? false :placeholder (tr [:gig/more-details-placeholder])) "sm:col-span-3")
              (render/dl-item (tr [:gig/setlist]) (render/textarea :value setlist :name (path "setlist") :required? false) "sm:col-span-3")
              (render/dl-item (tr [:gig/description]) (render/textarea :value description :name (path "description") :required? false) "sm:col-span-3")
              ;;
              )
             (list

              (render/dl-item (tr [:gig/date])
                              (ui/datetime date)
                              (when end-date
                                [:span " " (ui/datetime end-date)]))
              (render/dl-item (tr [:gig/location]) location)
              (render/dl-item (tr [:gig/contact]) (render/member-nick contact))
              (render/dl-item (tr [:gig/call-time]) (ui/time call-time))
              (render/dl-item (tr [:gig/set-time]) (ui/time set-time))
              (render/dl-item (tr [:gig/end-time]) (ui/time end-time))
              (when leader
                (render/dl-item (tr [:gig/leader]) leader))
              (when pay-deal
                (render/dl-item (tr [:gig/pay-deal]) pay-deal))
              (when outfit
                (render/dl-item (tr [:gig/outfit]) outfit))
              (when more-details
                (render/dl-item (tr [:gig/more-details]) more-details "sm:col-span-3"))
              (when setlist
                (render/dl-item (tr [:gig/setlist]) (interpose [:br] (str/split-lines setlist)) "sm:col-span-3"))
              (when post-gig-plans
                (render/dl-item (tr [:gig/post-gig-plans]) post-gig-plans "sm:col-span-3"))))
           ;;
           ]]]]]]]))
(ctmx/defcomponent ^:endpoint gigs-detail-page [{:keys [db] :as req}]
  (let [{:gig/keys [gig-id title date end-date status
                    contact pay-deal call-time set-time end-time
                    outfit description location setlist leader post-gig-plans
                    more-details] :as gig} (:gig req)

        archived? (domain/gig-archived? gig)
        attendances-by-section (if archived?
                                 (q/attendance-plans-by-section-for-gig db gig-id (q/attendance-for-gig db gig-id))
                                 (q/attendance-plans-by-section-for-gig db gig-id (q/attendance-for-gig-with-all-active-members db gig-id)))]
    [:div {:id id}
     (gigs-detail-page-info req gig false)
     [:div {:class "mx-auto mt-8 grid max-w-3xl grid-cols-1 gap-6 sm:px-6 lg:max-w-7xl lg:grid-flow-col-dense lg:grid-cols-3"}
      [:div {:class "space-y-6 lg:col-span-2 lg:col-start-1"}
;;;; Attendance Section
       [:section
        [:div {:class "bg-white shadow sm:rounded-lg"}
         [:div {:class "px-4 py-5 sm:px-6"}
          [:h2 {:class "text-lg font-medium leading-6 text-gray-900"}
           "Attendance"]]
         [:div {:class "border-t border-gray-200"}
          (if archived?
            (rt/map-indexed gig-attendance-archived req attendances-by-section)
            (rt/map-indexed gig-attendance req attendances-by-section))]]]
;;;; Comments Section
       [:section {:aria-labelledby "notes-title"}
        [:div {:class "bg-white shadow sm:overflow-hidden sm:rounded-lg"}
         (gigs-detail-page-comment req gig)]]]]]))

(ctmx/defcomponent ^:endpoint gigs-list-page [{:keys [db] :as req}]
  (let [future-gigs (controller/gigs-future db)
        offset 0
        limit 10
        past-gigs (controller/gigs-past-page db offset limit)
        ;; past-gigs (controller/gigs-past-two-weeks db)
        tr (i18n/tr-from-req req)]
    [:div
     (render/page-header :title (tr [:gigs/title])
                         :buttons  (list
                                    (render/button :label (tr [:action/create])
                                                   :priority :primary
                                                   :centered? true
                                                   :attr {:href "/events/new"} :icon icon/plus)))

     [:div {:class "mt-6 px-4 sm:px-6 lg:px-8"}
      (ui/divider-left (tr [:gigs/upcoming]))
      [:div {:class "overflow-hidden bg-white shadow sm:rounded-md mb-8"
             :id "songs-list"}
       (if (empty? future-gigs)
         (tr [:gigs/no-future])
         [:ul {:role "list", :class "divide-y divide-gray-200"}
          (map (fn [gig]
                 [:li
                  (gig-row gig)]) future-gigs)])]
      (ui/divider-left (tr [:gigs/past]))
      [:div {:class "overflow-hidden bg-white shadow sm:rounded-md"
             :id "songs-list"}
       (if (empty? past-gigs)
         (tr [:gigs/no-past])
         [:ul {:role "list", :class "divide-y divide-gray-200"}
          (map (fn [gig]
                 [:li
                  (gig-row gig)]) past-gigs)])]]]))