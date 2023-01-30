(ns app.gigs.views2
  (:refer-clojure :exclude [hash])
  (:require
   [app.gigs.domain :as domain]
   [app.icons :as icon]
   [app.queries :as q]
   [app.ui :as ui]
   [app.urls :as url]
   [app.util :as util]
   [clojure.string :as str]
   [medley.core :as m]
   [tick.core :as t]
   [app.debug :as debug]))

(defn gig-edit-form [{:tempura/keys [tr]  :as req}
                     {:gig/keys [title date end-date status gig-type gig-id
                                 contact pay-deal call-time set-time end-time
                                 outfit description location setlist leader post-gig-plans
                                 more-details] :as gig}]
  (let [archived? (domain/gig-archived? gig)
        member-select-vals (q/members-for-select-active (:db req))
        hx-target (util/hash :comp/gig-detail-page)
        gig-detail-page-endpoint (url/endpoint-path req :app.gigs.routes/details-page {:gig/gig-id gig-id})
        gig-delete-endpoint nil #_(util/endpoint endpoints/gig-delete)
        form-buttons (list
                      (ui/button :label (tr [:action/save])
                                 :priority :primary
                                 :centered? true
                                 :attr {:hx-target hx-target})
                      (ui/button :label (tr [:action/cancel])
                                 :priority :white
                                 :centered? true
                                 :tabindex -1
                                 :attr {:hx-get gig-detail-page-endpoint
                                        :hx-target hx-target :hx-vals {"edit?" false}})
                      (ui/button :label (tr [:action/delete]) :priority :white-destructive :centered? true
                                 :tabindex -1
                                 :hx-delete gig-delete-endpoint :hx-target hx-target
                                 :hx-confirm (tr [:action/confirm-delete-gig] [title])))]

    [:form {:id (util/id :comp/gig-detail-page) :hx-post (url/link-gig-new gig)}
     [:div
       ;; page-header-full with buttons hidden on smallest screens
      [:div {:class "px-4 py-4 sm:flex sm:items-center sm:justify-between sm:px-6 lg:px-8 bg-white"}
       [:div {:class "flex items-center space-x-5 w-full sm:w-1/2"}
        [:div {:class "w-full"}
         [:h1 {:class "text-2xl font-bold text-gray-900 w-full"}
          (ui/text :label "Title" :name "title" :value title)]
         [:p {:class "text-sm font-medium text-gray-500 w-full"}
          (list
           (ui/select
            :label (tr [:gig/status])
            :id "status"
            :value (when status (name status))
            :size :small
            :required? true
            :options (map (fn [m] {:label (tr [m]) :value (name m)})
                          (if gig
                            domain/statuses
                            domain/create-statuses)))
           (ui/select
            :id "gig-type"
            :label (tr [:gig/gig-type])
            :value (when gig-type (name gig-type))
            :size :small
            :required? true
            :options
            (if (nil? gig)
              (concat [{:label "-" :value ""}]
                      (map (fn [m] {:label (tr [m]) :value (name m)}) domain/gig-types))
              (map (fn [m] {:label (tr [m]) :value (name m)}) domain/gig-types))))]]]
       [:dil {:class
              "hidden sm:flex justify-stretch mt-6 flex flex-col-reverse space-y-4 space-y-reverse sm:flex-row-reverse sm:justify-end sm:space-y-0 sm:space-x-3 sm:space-x-reverse md:mt-0 md:flex-row-reverse md:space-x-reverse"}

        (when-not archived?
          form-buttons)]]

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
                           (ui/date :value date :name "date" :required? true :min (str (t/date)))
                           (ui/date :value date :name "date" :required? true)))
             (ui/dl-item (tr [:gig/end-date])
                         (if (nil? gig)
                           (ui/date :value end-date :name "end-date" :required? false :min (str (t/tomorrow)))
                           (ui/date :value end-date :name "end-date" :required? false)))
             (ui/dl-item (tr [:gig/contact]) (ui/member-select :value (:member/gigo-key contact) :label "" :id  "contact" :members member-select-vals :with-empty-opt? true))
             (ui/dl-item (tr [:gig/call-time]) (ui/input-time :value call-time :name  "call-time" :required? true))
             (ui/dl-item (tr [:gig/set-time]) (ui/input-time :value set-time :name  "set-time" :required? false))
             (ui/dl-item (tr [:gig/end-time]) (ui/input-time :value end-time :name  "end-time" :required? false))
             (ui/dl-item (tr [:gig/location]) (ui/text :value location :name  "location"))
             (ui/dl-item (tr [:gig/outfit]) (ui/text :value (or outfit (tr [:orange-and-green])) :name  "outfit" :required? false))
             (ui/dl-item (tr [:gig/pay-deal]) (ui/text :value pay-deal :name  "pay-deal" :required? false))
             (ui/dl-item (tr [:gig/leader]) (ui/text :label "" :value leader :name  "leader" :required? false))
             (ui/dl-item (tr [:gig/post-gig-plans]) (ui/text :value post-gig-plans :name  "post-gig-plans" :required? false) "sm:col-span-2")
             (ui/dl-item (tr [:gig/more-details]) (ui/textarea :value more-details :name  "more-details" :required? false :placeholder (tr [:gig/more-details-placeholder])) "sm:col-span-3")
             (when false
               (ui/dl-item (tr [:gig/setlist]) (ui/textarea :value setlist :name  "setlist" :required? false) "sm:col-span-3"))
             (ui/dl-item (tr [:gig/description]) (ui/textarea :value description :name  "description" :required? false) "sm:col-span-3")
             (ui/dl-item nil (ui/checkbox :label (tr [:gig/email-about-change?]) :id  "notify?"))
              ;;
             )]]
          [:div {:class "px-4 py-5 sm:px-6"}
           [:div {:class "justify-stretch mt-6 flex flex-col-reverse space-y-4 space-y-reverse sm:hidden"}
            form-buttons]]]]]]]]))

(defn gigs-detail-page-info-ro [{:tempura/keys [tr] :as req}
                                {:gig/keys [title date end-date status gig-type gig-id
                                            contact pay-deal call-time set-time end-time
                                            outfit location setlist leader post-gig-plans
                                            more-details] :as gig}]
  (let [archived? (domain/gig-archived? gig)]
    [:div {:id (util/id :comp/gig-detail-page-info)}
     (ui/page-header :title (list  title " " (ui/gig-status-icon status))
                     :subtitle (tr [gig-type])
                     :buttons (list
                               (ui/button :label "Log Plays"
                                          :tag :a
                                          :priority :primary
                                          :centered? true
                                          :class "items-center justify-center"
                                          :attr {:href
                                                 (url/endpoint-path req :app.gigs.routes/log-plays {:gig/gig-id gig-id})})
                               (when-not archived?
                                 (ui/button :label (tr [:action/edit])
                                            :priority :white
                                            :centered? true
                                            :class "items-center justify-center"
                                            :attr {:hx-get (url/endpoint-path req :app.gigs.routes/details-edit-form)
                                                   :hx-target (util/hash :comp/gig-detail-page-info)
                                                   :hx-vals {:gig-id gig-id}}
                                            #_{:hx-get (util/endpoint gig-details-edit-form)
                                               :hx-target (util/hash :comp/gig-detail-page-info)}))))
     [:div {:class "mx-auto mt-8 grid max-w-3xl grid-cols-1 gap-6 sm:px-6 lg:max-w-7xl lg:grid-flow-col-dense lg:grid-cols-3"}
      [:div {:class "space-y-6 lg:col-span-3 lg:col-start-1"}
       [:section
        [:div {:class "bg-white shadow sm:rounded-lg"}
         [:div {:class "px-4 py-5 sm:px-6"}
          [:h2 {:class "text-lg font-medium leading-6 text-gray-900"}
           (tr [:gig/gig-info])]]
         [:div {:class "border-t border-gray-200 px-4 py-5 sm:px-6"}
          [:dl {:class "grid grid-cols-1 gap-x-4 gap-y-8 grid-cols-2 sm:grid-cols-3"}
           (list
            (ui/dl-item (tr [:gig/date])
                        (if end-date
                          (ui/daterange date end-date)
                          (ui/datetime date)))

            (ui/dl-item (tr [:gig/location]) location "break-words")
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
           ]]]]]]]))
;;;; Setlist editing Flow:
;; setlist --[Edit]-----> choose songs --[Save]--> order --[Done]--> setlist (repeat)
;;        \--[Reorder]---------------------------/
;;

(defn gigs-detail-page-setlist-list-ro [selected-songs]
  [:ul {:class "list-disc" :hx-boost true}
   (map (fn [{:song/keys [title solo-count] :as song}]
          [:li {:class "ml-4"}
           [:a {:href (url/link-song song) :class "link-blue"}
            title]
           (when (and solo-count (> solo-count 0))
             (list " (" solo-count " solos)"))]) selected-songs)])

(defn serialize-selected-songs [selected-songs]
  (map-indexed (fn [idx {:keys [song-order song-id]}]
                 (list
                  [:input {:type :hidden :name (str idx "_songs_song-order")  :value (or song-order idx)}]
                  [:input {:type :hidden :name (str idx "_songs_song-id") :value song-id}]))
               selected-songs))

(defn gig-detail-page-setlist [{:tempura/keys [tr] :keys [db] :as req} song-ids]
  (let [song-ids (map util/ensure-uuid song-ids)
        songs (util/index-sort-by song-ids :song/song-id (q/find-songs db song-ids))
        selected-songs (map-indexed (fn [idx song] {:song-order idx :song-id (-> song :song/song-id str)}) songs)]
    (ui/panel {:title (tr [:gig/setlist]) :id (util/id :comp/setlist-container)
               :buttons (when (seq songs)
                          (list
                           [:form {:hx-post (url/endpoint-path req :app.gigs.routes/setlist-select-songs (:gig req))
                                   :hx-target (util/hash :comp/setlist-container)}
                            (serialize-selected-songs selected-songs)
                            (ui/button :label (tr [:action/edit]) :priority :white)]
                           #_(list
                              [:form {:hx-post (url/endpoint-path req :app.gigs.routes/gig-setlist-sort (:gig req))
                                      :hx-target (util/hash :comp/setlist-container)}
                               (serialize-selected-songs selected-songs)
                               (ui/button :label (tr [:action/reorder]) :priority :white)])))}
              [:div {#_:id #_"setlist-container" :class ""}
               (if (empty? songs)
                 [:div {:class "flex flex-col items-center justify-center"}
                  [:div
                   (ui/button :label (tr [:gig/create-setlist]) :priority :primary :icon icon/plus
                              :attr {:hx-get (url/endpoint-path req :app.gigs.routes/setlist-select-songs (:gig req))
                                     :hx-target (util/hash :comp/setlist-container)})]]
                 (gigs-detail-page-setlist-list-ro songs))])))

(defn setlist-song-checkbox [selected-songs idx song]
  (let [this-song-id (str (:song/song-id song))
        path (fn [name] (str idx "_songs_" name))
        {:keys [song-order]} (m/find-first #(= (:song-id %) this-song-id) selected-songs)]
    [:li
     [:label {:for this-song-id :class "rounded border-4 mx-0 my-1 p-2  basis-1/2 grid grid-flow-col grid-cols-auto justify-between"}
      [:span {:class "truncate"} (:song/title song)]
      [:input {:type :hidden :name (path "song-order")  :value (or song-order "9999")}]
      [:input {:type :checkbox :id this-song-id  :name (path "song-id") :value this-song-id
               :checked (some? song-order)}]]]))

(defn setlist-choose-songs-form [{:tempura/keys [tr] :keys [db] :as req}]
  (let [selected-songs (util/unwrap-params req)
        all-songs (q/find-all-songs db)
        self-id (util/id :comp/setlist-choose-songs-form)]
    (ui/panel {:title (tr [:gig/setlist])
               :id (util/id :comp/setlist-container)
               :buttons (ui/button :class "pulse-delay" :label (tr [:action/continue])
                                   :priority :primary :form self-id)}
              [:form {:hx-target (util/hash :comp/setlist-container)
                      :hx-put (url/endpoint-path req :app.gigs.routes/setlist-select-songs (:gig req))
                      :id self-id}
               [:ul {:class "p-0 m-0 grid grid-cols-2 md:grid-cols-2 lg:grid-cols-3 gap-y-0 md:gap-x-2"}
                (map-indexed (partial setlist-song-checkbox selected-songs) all-songs)]])))

(defn setlist-sort-form [{:tempura/keys [tr] :keys [db] :as req}]
  (let [song-ids (->>  (-> req util/json-params :songs)
                       (filter :song-id)
                       (sort-by :song-order)
                       (map :song-id)
                       (map util/ensure-uuid))
        songs (util/index-sort-by song-ids :song/song-id (q/find-songs db song-ids))
        self-id (util/id :comp/setlist-sort-form)]
    (ui/panel {:title (tr [:gig/setlist]) :id "setlist-container"
               :buttons (ui/button :class "pulse-delay" :label (tr [:action/save]) :priority :primary :form self-id)}
              [:form {:class "w-full"
                      :hx-post (url/endpoint-path req :app.gigs.routes/setlist-order-songs (:gig req))
                      :hx-target (util/hash :comp/setlist-container)
                      :id self-id}
               [:p "Drag the songs into setlist order."]
               [:div {:class "htmx-indicator pulsate"} (tr [:updating])]
               [:ul {:class "sortable p-0 m-0 grid grid-cols-1 gap-y-0 md:gap-x-2 max-w-sm"}
                (map (fn [{:song/keys [song-id title]}]
                       [:li {:class "rounded border-4 mx-0 my-1 p-2  basis-1/2 grid grid-flow-col grid-cols-auto justify-between"}
                        [:div {:class "drag-handle cursor-pointer"} (icon/bars {:class "h-5 w-5"})]
                        [:input {:type :hidden :name "song-ids" :value song-id}]
                        title]) songs)]])))

(defn gig-detail-page [{:tempura/keys [tr] :keys [db] :as req} ^:boolean show-committed?]
  ;; gig-details-edit-form #_endpoints/gig-delete gig-details-edit-post gig-details-get
  (let [{:gig/keys [gig-id gig-type] :as gig} (:gig req)
        _ (assert gig)
        _ (assert gig-id)
        archived? (domain/gig-archived? gig)
        probe? (#{:gig.type/probe :gig.type/extra-probe} gig-type)
        gig? (#{:gig.type/gig} gig-type)
        scheduled-songs (q/setlist-song-ids-for-gig db gig-id)
        attendances-by-section (if archived?
                                 (q/attendance-plans-by-section-for-gig db gig-id (q/attendances-for-gig db gig-id) false)
                                 (q/attendance-plans-by-section-for-gig db gig-id (q/attendance-for-gig-with-all-active-members db gig-id) show-committed?))]

    (tap> {:scheduled scheduled-songs})
    [:div {:id (util/id :comp/gig-detail-page)}
     (gigs-detail-page-info-ro req (:gig req))
     (cond probe?
           #_(gigs-detail-page-probeplan req)
           (gig-detail-page-setlist req scheduled-songs)
           gig?
           (gig-detail-page-setlist req scheduled-songs)
           :else nil)
     #_[:div {:class "mx-auto mt-8 grid max-w-3xl grid-cols-1 gap-6 sm:px-6 lg:max-w-7xl lg:grid-flow-col-dense lg:grid-cols-3"}
        [:div {:class "space-y-6 lg:col-span-3 lg:col-start-1"}
;;;; Attendance Section
         [:section
          [:div {:class "bg-white shadow sm:rounded-lg"}
           [:div {:class "px-4 py-5 sm:px-6 flex flex-row flex-items-center justify-between"}
            [:h2 {:class "text-lg font-medium leading-6 text-gray-900"}
             (tr [:gig/attendance])]
            (when-not archived?
              [:div
               (ui/button :label (if show-committed?
                                   (tr [:gig/show-all])
                                   (tr [:gig/show-committed]))
                          :size :xsmall
                          :priority :primary
                          :centered? true
                          :attr {:hx-get (comp-name) :hx-target (hash ".") :hx-vals {:show-committed? (not show-committed?)}})])]

           [:div {:class "border-t border-gray-200"}
            (if archived?
              (rt/map-indexed gig-attendance-archived req attendances-by-section)
              (rt/map-indexed gig-attendance req attendances-by-section))]]]
;;;; Comments Section
         #_[:section {:aria-labelledby "notes-title"}
            [:div {:class "bg-white shadow sm:overflow-hidden sm:rounded-lg"}
             (gigs-detail-page-comment req gig)]]]]]))

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

(defn li-log-play [{:keys [db] :as req} idx play]
  (let [was-played? (some? (:played/play-id play))
        path (fn [name]
               (str idx "_song_"  name))
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

(defn log-plays [{:tempura/keys [tr] :keys [db params] :as req}]
  (let [gig-id (:gig/gig-id (:path-params req))
        _ (assert gig-id)
        gig (q/retrieve-gig db gig-id)
        plays (q/plays-by-gig db gig-id)
        songs-not-played (q/songs-not-played plays (q/find-all-songs db))
            ;; our log-play component wants to have "plays" for every song
            ;; so we enrich the actual plays with stubs for the songs that were not played
        plays (sort-by #(-> % :played/song :song/title) (concat plays
                                                                (map (fn [s] {:played/song s}) songs-not-played)))]

    [:form {:id (util/id :comp/log-plays-form)
            :hx-post (url/endpoint-path req :app.gigs.routes/log-plays {:gig/gig-id gig-id})
            :class "space-y-4"}
     (ui/page-header :title (list  (:gig/title gig) " (" (ui/datetime (:gig/date gig)) ")")
                     :subtitle (tr [:gig/play-log-subtitle]))
     (log-play-legend tr)
     [:ul {:class "toggler-container"}
      (map-indexed (partial li-log-play req)  plays)]
     [:div
      [:div {:class "flex justify-end space-x-2"}
       (ui/link-button :label (tr [:action/cancel]) :attr {:href (url/link-gig-new gig)} :priority :white)
       (ui/button :label (tr [:action/save]) :priority :primary)]]]))
