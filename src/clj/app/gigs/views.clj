(ns app.gigs.views
  (:refer-clojure :exclude [comment])
  (:require
   [app.views.shared :as ui]
   [app.gigs.controller :as controller]
   [app.util :as util]
   [app.songs.controller :as songs.controller]
   [ctmx.response :as response]
   [app.render :as render]
   [app.icons :as icon]
   [ctmx.core :as ctmx]
   [medley.core :as m]
   [ctmx.rt :as rt]
   [app.queries :as q]
   [clojure.string :as str]
   [app.humanize :as humanize]
   [clojure.set :as set]
   [app.controllers.common :as common]
   [app.debug :as debug]
   [app.i18n :as i18n]))

(def link-gig (partial util/link-helper "/event/" :gig/gig-id))

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
    [:a {:href (link-gig gig) :class "block hover:bg-gray-50"}
     [:div {:class "px-4 py-4 sm:px-6"}
      [:div {:class "flex items-center justify-between"}
       [:p {:class "truncate text-sm font-medium text-indigo-600"}
        (ui/gig-status-icon status)
        title]
       [:div {:class "ml-2 flex flex-shrink-0"}
        (render/button :tag :a :attr {:href (link-gig gig "/log-play/")}
                       :label "Log Plays" :priority :white-rounded :size :small)]]

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
          _ (tap> {:gigo-key gigo-key :gkv (value "gigo-key") :p (:params req)})
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
        :options (map (fn [m] {:label (tr [m]) :value (name m)}) controller/motivations))])))

(ctmx/defcomponent ^:endpoint gig-attendance-person [{:keys [db] :as req} idx attendance]
  (let [{:member/keys [gigo-key] :as member} (:attendance/member attendance)]
    [:div {:class "grid grid-cols grid-cols-5" :id id}
     [:div {:class "col-span-2 align-middle"}
      [:a {:href (str "/member/" gigo-key "/") :class "text-blue-500 hover:text-blue-600 align-middle"}
       (render/member-nick member)]]
     [:div {:class "col-span-1"} (gig-attendance-person-plan req gigo-key (:attendance/plan attendance))]
     [:div {:class "col-span-1"} (gig-attendance-person-motivation req gigo-key (:attendance/motivation attendance))]
     [:div {:class "col-span-1 "} (gig-attendance-person-comment req gigo-key (:attendance/comment attendance))]]))

(ctmx/defcomponent ^:endpoint gig-attendance [{:keys [db] :as req} idx section]
  [:div {:id id :class (render/cs "grid grid-cols-3 gap-x-0 gap-y-8 mb-2 px-4 py-0 sm:px-6"
                                  (when (= 0 (mod idx 2))
                                    "bg-gray-100"))}

   [:div {:class "col-span-1 font-bold"} (:section/name section)]
   [:div {:class "col-span-2"}
    (rt/map-indexed gig-attendance-person req (:members section))]])

(ctmx/defcomponent ^:endpoint gig-comment-li [{:keys [db] :as req} idx comment]
  (let [{:comment/keys [comment-id body author created-at]} comment]
    [:li
     [:div {:class "flex space-x-3"}
      [:div {:class "flex-shrink-0"}
       [:img {:class "h-10 w-10 rounded-full" :src "https://images.unsplash.com/photo-1494790108377-be9c29b29330?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=facearea&facepad=2&w=256&h=256&q=80"}]]
      [:div
       [:div {:class "text-sm"}
        [:a {:href "#", :class "font-medium text-gray-900"}
         (:member/name author)]]
       [:div {:class "mt-1 text-sm text-gray-700"}
        [:p body]]
       [:div {:class "mt-2 space-x-2 text-sm"}
        [:span {:class "font-medium text-gray-500"}
         (ui/humanize-dt created-at)]
        [:span {:class "font-medium text-gray-500"} "·"]
        [:button {:type "button", :class "font-medium text-gray-900"} "Reply"]]]]]))

(ctmx/defcomponent ^:endpoint gigs-detail-page [{:keys [db] :as req}]
  (let [{:gig/keys [gig-id title date end-date status
                    contact pay-deal call-time set-time end-time
                    outfit description location setlist leader post-gig-plans
                    more-details comments] :as gig} (:gig req)
        attendances-by-section (q/attendance-plans-by-section-for-gig db gig-id)]

    [:div
     (render/page-header :title (list  title " " (ui/gig-status-icon status))

                         :subtitle (ui/datetime date)
                         :buttons (list  (render/button :label "Comment"
                                                        :priority :white
                                                        :centered? true
                                                        :attr {:href "/songs/new"})
                                         (render/button :label "Log Plays"
                                                        :tag :a
                                                        :priority :primary
                                                        :centered? true
                                                        :class "items-center justify-center "
                                                        :attr {:href (link-gig gig "/log-play/")})))
     [:div {:class "mx-auto mt-8 grid max-w-3xl grid-cols-1 gap-6 sm:px-6 lg:max-w-7xl lg:grid-flow-col-dense lg:grid-cols-3"}
      [:div {:class "space-y-6 lg:col-span-2 lg:col-start-1"}

;;;; Gig Info Section
       [:section
        [:div {:class "bg-white shadow sm:rounded-lg"}
         [:div {:class "px-4 py-5 sm:px-6"}
          [:h2 {:class "text-lg font-medium leading-6 text-gray-900"}
           "Info"]]
         [:div {:class "border-t border-gray-200 px-4 py-5 sm:px-6"}
          [:dl {:class "grid grid-cols-1 gap-x-4 gap-y-8 sm:grid-cols-3"}
           (render/dl-item "Date"
                           (ui/datetime date)
                           (when end-date
                             [:span " " (ui/datetime end-date)]))
           (render/dl-item "Location" location)
           (render/dl-item "Contact" (:member/name contact))
           (render/dl-item "Call Time" (ui/time call-time))
           (render/dl-item "Set Time" (ui/time set-time))
           (render/dl-item "End Time" (ui/time end-time))
           (when leader
             (render/dl-item "Leader" leader))
           (when pay-deal
             (render/dl-item "Pay Deal" pay-deal))
           (when outfit
             (render/dl-item "What to wear" outfit))
           (when more-details
             (render/dl-item "Details" more-details "sm:col-span-3"))
           (when setlist
             (render/dl-item "Set List" (interpose [:br] (str/split-lines setlist)) "sm:col-span-3"))
           (when post-gig-plans
             (render/dl-item "Post Gig Plans" post-gig-plans "sm:col-span-3"))
           ;;
           ]]]]

;;;; Attendance Section
       [:section
        [:div {:class "bg-white shadow sm:rounded-lg"}
         [:div {:class "px-4 py-5 sm:px-6"}
          [:h2 {:class "text-lg font-medium leading-6 text-gray-900"}
           "Attendance"]]

         [:div {:class "border-t border-gray-200"}
          (rt/map-indexed gig-attendance req attendances-by-section)]]]
;;;; Comments Section
       [:section {:aria-labelledby "notes-title"}
        [:div {:class "bg-white shadow sm:overflow-hidden sm:rounded-lg"}
         [:div {:class "divide-y divide-gray-200"}
          [:div {:class "px-4 py-5 sm:px-6"}
           [:h2 {:id "notes-title", :class "text-lg font-medium text-gray-900"}
            "Comments"]]
          [:div {:class "px-4 py-6 sm:px-6"}
           [:ul {:role "list", :class "space-y-8"}
            (rt/map-indexed gig-comment-li req comments)

            [:li
             [:div {:class "flex space-x-3"}
              [:div {:class "flex-shrink-0"}
               [:img {:class "h-10 w-10 rounded-full", :src "https://images.unsplash.com/photo-1519244703995-f4e0f30006d5?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=facearea&facepad=2&w=256&h=256&q=80"}]]
              [:div
               [:div {:class "text-sm"}
                [:a {:href "#", :class "font-medium text-gray-900"} "Michael Foster"]]
               [:div {:class "mt-1 text-sm text-gray-700"}
                [:p "Et ut autem. Voluptatem eum dolores sint necessitatibus quos. Quis eum qui dolorem accusantium voluptas voluptatem ipsum. Quo facere iusto quia accusamus veniam id explicabo et aut."]]
               [:div {:class "mt-2 space-x-2 text-sm"}
                [:span {:class "font-medium text-gray-500"} "4d ago"]
                [:span {:class "font-medium text-gray-500"} "·"]
                [:button {:type "button", :class "font-medium text-gray-900"} "Reply"]]]]]
            [:li
             [:div {:class "flex space-x-3"}
              [:div {:class "flex-shrink-0"}
               [:img {:class "h-10 w-10 rounded-full", :src "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=facearea&facepad=2&w=256&h=256&q=80"}]]
              [:div
               [:div {:class "text-sm"}
                [:a {:href "#", :class "font-medium text-gray-900"} "Dries Vincent"]]
               [:div {:class "mt-1 text-sm text-gray-700"}
                [:p "Expedita consequatur sit ea voluptas quo ipsam recusandae. Ab sint et voluptatem repudiandae voluptatem et eveniet. Nihil quas consequatur autem. Perferendis rerum et."]]
               [:div {:class "mt-2 space-x-2 text-sm"}
                [:span {:class "font-medium text-gray-500"} "4d ago"]]]]]]]]
         [:div {:class "bg-gray-50 px-4 py-6 sm:px-6"}
          [:div {:class "flex space-x-3"}
           [:div {:class "flex-shrink-0"}
            [:img {:class "h-10 w-10 rounded-full", :src "https://images.unsplash.com/photo-1517365830460-955ce3ccd263?ixlib=rb-=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=facearea&facepad=8&w=256&h=256&q=80"}]]
           [:div {:class "min-w-0 flex-1"}
            [:form {:action "#"}
             [:div
              [:label {:for "comment", :class "sr-only"} "About"]
              [:textarea {:id "comment", :name "comment", :rows "3", :class "block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm", :placeholder "Add a note"}]]
             [:div {:class "mt-3 flex items-center justify-between"}

              [:button {:type "submit", :class "inline-flex items-center justify-center rounded-md border border-transparent bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"} "Comment"]]]]]]]]]]]))
(ctmx/defcomponent ^:endpoint gigs-list-page [{:keys [db] :as req}]
  (let [events (controller/gigs-future db)]
    [:div
     (render/page-header :title "Gigs/Probes"
                         :buttons  (list (render/button :label "Share2"
                                                        :priority :white
                                                        :centered? true
                                                        :attr {:href "/events/new"})
                                         (render/button :label "Gig/Probe"
                                                        :priority :primary
                                                        :centered? true
                                                        :attr {:href "/events/new"} :icon icon/plus)))

     [:div {:class "mt-6 px-4 sm:px-6 lg:px-8"}
      (ui/divider-left "Upcoming")
      [:div {:class "overflow-hidden bg-white shadow sm:rounded-md mb-8"
             :id "songs-list"}
       (if (empty? events)
         "No events"
         [:ul {:role "list", :class "divide-y divide-gray-200"}
          (map (fn [event]
                 [:li
                  (gig-row event)]) events)])]
      (ui/divider-left "Past")
      [:div {:class "overflow-hidden bg-white shadow sm:rounded-md"
             :id "songs-list"}]]]))
