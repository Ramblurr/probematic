(ns app.songs.views
  (:require
   [app.file-browser.views :as file.browser.view]
   [app.i18n :as i18n]
   [app.icons :as icon]
   [app.queries :as q]
   [app.songs.controller :as controller]
   [app.ui :as ui]
   [app.urls :as url]
   [app.util :as util]
   [clojure.string :as clojure.string]
   [ctmx.core :as ctmx]
   [ctmx.response :as response]
   [medley.core :as m]))

(ctmx/defcomponent ^:endpoint songs-log-play [{:keys [db] :as req}]
  (ctmx/with-req req
    (let [result (and post? (controller/log-play! req))]
      (if (:play result)
        (response/hx-redirect "/songs/")
        (let [conn (-> req :system :conn)
              songs (q/find-all-songs db)
              gigs (q/find-all-gigs db)]

          [:form {:id id :hx-post (path ".")
                  :class "space-y-4"}
           (list
            (ui/select :id (path "song-id")
                       :label "Songs"
                       :value (value "song")
                       :options (map (fn [s]
                                       {:value (:song/title s)
                                        :label (:song/title s)
                                        :selected? false}) songs))

            (ui/select :id (path "gig-id")
                       :label "Gig/Probe"
                       :value (value "song")
                       :options (map (fn [{:gig/keys [gig-id title date]}]
                                       {:value gig-id
                                        :label (str title " " (when date (ui/format-dt date)))
                                        :selected? false}) gigs)))

           (ui/radio-button-group  :id (path  "play-type") :label "Play Type"
                                   :required? true
                                   :value (value "play-type")
                                   :options [{:id (path "play-type/gig") :label "Gig" :value "play-emphasis/gig"  :size :large}
                                             {:id (path "play-type/gig") :label "Probe: Intensiv" :value "play-emphasis/intensiv"  :size :large}
                                             {:id (path "play-type/gig") :label "Probe: Durch" :value "play-emphasis/durch"  :size :large}])
           (ui/radio-button-group  :id (path  "feeling") :label "How'd it go?"
                                   :required? true
                                   :value (value "feeling")
                                   :class "emotion-radio"
                                   :options [{:id (path "feeling/good") :label "Nice!" :value "play-rating/good" :icon icon/smile :size :large :class "icon-smile"}
                                             {:id (path "feeling/ok")  :label "Okay" :value "play-rating/ok" :icon icon/meh :size :large :class "icon-meh"}
                                             {:id  (path "feeling/bad")  :label "Uh-oh" :value "play-rating/bad" :icon icon/sad :size :large :class "icon-sad"}])
           (ui/textarea :name (path  "comment") :label "Thoughts?"
                        :value (value "comment"))

           [:div
            [:div {:class "flex justify-end"}
             [:a {:href "/songs", :class "btn btn-sm btn-clear-normal"} "Cancel"]
             [:button {:class "ml-3 btn btn-sm btn-indigo-high"
                       :type "submit"} "Save"]]]])))))

(declare song-sheet-music)
(declare song-sheet-music-edit)

(ctmx/defcomponent ^:endpoint song-sheet-music-selected [{:keys [db] :as req} section-name selected-path]
  (when (util/post? req)
    (let [db-after (controller/add-sheet-music! req (parse-uuid (-> req :path-params :song-id))  section-name selected-path)]
      (song-sheet-music-edit (assoc req :db db-after)))))

(ctmx/defcomponent ^:endpoint song-sheet-music-picker [{:keys [db] :as req} section-name selected-path]
  (when (util/post? req)
    (let [tr (i18n/tr-from-req req)]
      (file.browser.view/file-picker-panel req
                                           {:target-params
                                            {:endpoint (util/comp-name #'song-sheet-music-selected) :values {:section-name section-name} :target (hash ".")}
                                            :id id
                                            :title (tr [:song/choose-sheet-music-title])
                                            :subtitle (tr [:song/choose-sheet-music-subtitle] [section-name])
                                            :root-dir "/Noten - Scores"
                                            :current-dir "/Noten - Scores/aktuelle Stücke"}))))
(ctmx/defcomponent ^:endpoint song-sheet-music-remove [req sheet-id]
  (when (util/delete? req)
    (let [db-after (controller/remove-sheet-music! req (parse-uuid sheet-id))]
      (song-sheet-music-edit (assoc req :db db-after)))))

(defn sheet-music-section-sheet-rw [target tr {:sheet-music/keys [title sheet-id]}]
  (ui/rich-li {:icon icon/file-pdf-outline}
              (ui/rich-li-text {} title)
              (ui/rich-li-action (ui/button :size :2xsmall :priority :white-destructive :label (tr [:action/remove])
                                            :hx-target target
                                            :hx-delete (util/comp-name #'song-sheet-music-remove) :hx-vals {:sheet-id (str sheet-id)}))))

(defn sheet-music-section-rw [target tr {:section/keys [name default?] :as section}]
  (ui/dl-item (if default? (tr [:song/other-sheet-music]) name)
              [:div {:class "flex flex-col"}
               (ui/rich-ul {}
                           (map (partial sheet-music-section-sheet-rw target tr) (:sheet-music/_section section)))
               [:div {:class "flex justify-end mt-2"}
                (ui/button :size :xsmall :priority :white :label (tr [:action/add]) :icon icon/plus :class ""
                           :hx-vals {:section-name name}
                           :hx-target target
                           :hx-post (util/comp-name #'song-sheet-music-picker))]]))

(ctmx/defcomponent ^:endpoint song-sheet-music-edit [{:keys [db] :as req}]
  song-sheet-music-picker
  song-sheet-music-remove
  (let [tr              (i18n/tr-from-req req)
        song-id         (parse-uuid (-> req :path-params :song-id))
        sections        (q/sheet-music-for-song db song-id)
        default-section (m/find-first :section/default? sections)
        sections        (remove :section/default? sections)]
    (ui/panel
     {:title   (tr [:song/sheet-music-title]) :id id
      :buttons (ui/button :label (tr [:action/done]) :priority :primary :centered? true
                          :attr {:hx-get (util/comp-name #'song-sheet-music) :hx-vals {:edit? false} :hx-target (hash ".")})}
     [:dl {:class "grid grid-cols-1 gap-x-4 gap-y-8 sm:grid-cols-2"}

      (sheet-music-section-rw (hash ".") tr default-section)
      (map (partial sheet-music-section-rw (hash ".") tr) sections)])))

(ctmx/defcomponent ^:endpoint song-sheet-music [{:keys [db] :as req}]
  song-sheet-music-edit
  (let [song-id                               (parse-uuid (-> req :path-params :song-id))
        {:song/keys [title active?] :as song} (controller/retrieve-song db song-id)
        tr                                    (i18n/tr-from-req req)
        sections-sheets                       (q/sheet-music-for-song db song-id)
        default-section                       (m/find-first :section/default? sections-sheets)
        sections-sheets                       (remove :section/default? sections-sheets)]
    (ui/panel {:title   (tr [:song/sheet-music-title]) :id id
               :buttons (ui/button :label (tr [:action/edit]) :priority :white :centered? true
                                   :attr {:hx-get (util/comp-name #'song-sheet-music-edit) :hx-vals {:edit? true} :hx-target (hash ".")})}
              [:dl {:class "grid grid-cols-1 gap-x-4 gap-y-8 sm:grid-cols-2"}
               (ui/dl-item (tr [:song/other-sheet-music])
                           (ui/rich-ul {}
                                       (map (fn [{:sheet-music/keys [title] :file/keys [webdav-path]}]
                                              (ui/rich-li {:icon icon/file-pdf-outline}
                                                          (ui/rich-li-text {} title)
                                                          (ui/rich-li-action-a :href (url/link-file-download webdav-path) :label (tr [:action/download]))))
                                            (:sheet-music/_section default-section))))
               (map (fn [section-sheet]
                      (ui/dl-item (:section/name section-sheet)
                                  (ui/rich-ul {}
                                              (map (fn [{:sheet-music/keys [title] :file/keys [webdav-path]}]
                                                     (ui/rich-li {:icon icon/file-pdf-outline}
                                                                 (ui/rich-li-text {} title)
                                                                 (ui/rich-li-action-a :href (url/link-file-download webdav-path) :label (tr [:action/download]))))
                                                   (:sheet-music/_section section-sheet)))
                                  "sm:col-span-1")) sections-sheets)])))

(ctmx/defcomponent ^:endpoint song-detail-page [{:keys [db] :as req} ^:boolean edit?]
  (let [song-id                          (parse-uuid (-> req :path-params :song-id))
        post?                            (util/post? req)
        song                             (if post? (controller/update-song! req (parse-uuid (-> req :path-params :song-id)))
                                             (controller/retrieve-song db song-id))
        {:song/keys [title active? composition-credits
                     arrangement-credits arrangement-notes
                     origin solo-count]} song
        tr                               (i18n/tr-from-req req)
        comp-name                        (util/comp-namer #'song-detail-page)]
    [:form {:id id :hx-post (comp-name)}
     (if edit?
       (ui/page-header-full :title
                            (ui/text :label (tr [:song/title]) :name (path "title") :value title)
                            :subtitle [:div {:class "mt-2"}
                                       (ui/toggle-checkbox :label (tr [:song/active]) :name (path "active?") :checked? active?)]

                            :buttons
                            (list
                             (ui/button :label (tr [:action/cancel]) :priority :white :centered? true
                                        :attr {:hx-get (comp-name) :hx-vals {:edit? false} :hx-target (hash ".")})
                             (ui/button :label (tr [:action/save]) :priority :primary  :centered? true)))

       (ui/page-header :title title
                       :subtitle (ui/song-active-bubble song)
                       :buttons (list
                                 (ui/button :label (tr [:action/edit]) :priority :white :centered? true
                                            :attr {:hx-get (comp-name) :hx-vals {:edit? true} :hx-target (hash ".")})

                                 (ui/button :label (tr [:song/log-play])
                                            :priority :primary
                                            :centered? true
                                            :class "items-center justify-center "
                                            :attr {:href (str "/song/log-play/" song-id "/")}))))

     (if edit?
       (ui/panel {:title (tr [:song/background-title])}
                 (ui/dl
                  (ui/dl-item (tr [:song/solo-count]) (ui/input :type "number" :min "0" :step "1" :label "" :name (path "solo-count") :value solo-count :required? false))
                  (ui/dl-item (tr [:song/composition-credits])
                              (ui/text :label "" :name (path "composition-credits") :value composition-credits :required? false))
                  (ui/dl-item (tr [:song/arrangement-credits])
                              (ui/text :label "" :name (path "arrangement-credits") :value arrangement-credits :required? false))
                  (ui/dl-item (tr [:song/origin])
                              (ui/textarea :label "" :name (path "origin") :value origin :required? false)
                              "sm:col-span-3")
                  (ui/dl-item (tr [:song/arrangement-notes])
                              (ui/textarea :label "" :name (path "arrangement-notes") :value arrangement-notes :required? false)
                              "sm:col-span-3")))
       (ui/panel {:title (tr [:song/background-title])}
                 (ui/dl
                  (ui/dl-item (tr [:song/solo-count]) solo-count)
                  (ui/dl-item (tr [:song/composition-credits]) composition-credits)
                  (ui/dl-item (tr [:song/arrangement-credits]) arrangement-credits)
                  (ui/dl-item (tr [:song/origin]) origin
                              "sm:col-span-3")
                  (ui/dl-item (tr [:song/arrangement-notes]) arrangement-notes
                              "sm:col-span-3"))))
     (ui/panel {:title (tr [:song/play-stats-title])}
               (ui/dl
                (ui/dl-item (tr [:song/total-plays]) "0")
                (ui/dl-item (tr [:song/gig-count]) "0")
                (ui/dl-item (tr [:song/probe-count]) "0")
                (ui/dl-item (tr [:song/last-played-gig]) [:a {:class "link-blue" :href "#"} "Demo"])
                (ui/dl-item (tr [:song/last-played-probe]) [:a {:class "link-blue" :href "#"} "2022-11-23"])))
     (song-sheet-music req)]))

(ctmx/defcomponent ^:endpoint song-new [req]
  (let [tr (i18n/tr-from-req req)]
    (if (util/post? req)
      (do
        (controller/create-song! req)
        (response/hx-redirect "/songs/"))
      [:div
       (ui/page-header :title (tr [:song/create-title])
                       :subtitle (tr [:song/create-subtitle]))

       (ui/panel {}
                 [:form {:hx-post (util/comp-name #'song-new) :class "space-y-8 divide-y divide-gray-200"}
                  [:div {:class "space-y-8 divide-y divide-gray-200 sm:space-y-5"}
                   [:div {:class "space-y-6 sm:space-y-5"}
                    [:div
                     [:h3 {:class "text-lg font-medium leading-6 text-gray-900"}]
                     [:p {:class "mt-1 max-w-2xl text-sm text-gray-500"}]]
                    [:div {:class "space-y-6 sm:space-y-5"}
                     (ui/text-left :label (tr [:song/title]) :id (path "title") :placeholder "Watermelon Man" :value (value "title"))
                     (ui/toggle-checkbox-left :id (path "active?") :label (tr [:song/active]) :checked? true :name (path "active?"))]]]

                  [:div {:class "pt-5"}
                   [:div {:class "flex justify-end"}
                    [:a {:href "/songs" :class "rounded-md border border-gray-300 bg-white py-2 px-4 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}
                     (tr [:action/cancel])]
                    [:button {:type "submit" :class "ml-3 inline-flex justify-center rounded-md border border-transparent bg-indigo-600 py-2 px-4 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}
                     (tr [:action/create])]]]])])))

(defn song-row [{:song/keys [title last-played score total-plays] :as song}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"]
    [:a {:href (url/link-song song) :class "block hover:bg-gray-50"}
     [:div {:class "px-4 py-4 sm:px-6"}
      [:div {:class "flex items-center justify-between"}
       [:p {:class "truncate text-sm font-medium text-indigo-600"}
        title]
       [:div {:class "ml-2 flex flex-shrink-0"}
        (ui/song-active-bubble song)]]
      [:div {:class "mt-2 sm:flex sm:justify-between"}
       [:div {:class "flex"}
        [:p {:class "flex items-center text-sm text-gray-500"}
         (icon/hashtag {:class style-icon})
         "Played "
         total-plays]
        [:p {:class "mt-2 flex items-center text-sm text-gray-500 mt-0 ml-6"}

         (icon/star-outline {:class style-icon})
         "Score "
         score]]
       [:div {:class "mt-2 flex items-center text-sm text-gray-500 sm:mt-0"}
        (icon/calendar {:class style-icon})
        [:p "Last Played "
         (ui/datetime last-played)]]]]]))

(defn song-list [songs]
  (if (empty? songs)
    "Songs not found"
    [:ul {:role "list", :class "divide-y divide-gray-200"}
     (map (fn [song]
            [:li
             (song-row song)]) songs)]))

(defn norm [s]
  (-> s
      (clojure.string/upper-case)
      (clojure.string/trim)))

(defn search-songs [keyword all-songs]
  (filter (fn [s]
            (clojure.string/includes?
             (norm (:song/title s))
             (norm keyword))) all-songs))

(ctmx/defcomponent ^:endpoint songs-filter [req]
  [:div {:class "flex-grow relative rounded-md border border-gray-300 px-3 py-2 shadow-sm focus-within:border-indigo-600 focus-within:ring-1 focus-within:ring-indigo-600"}
   [:label {:for "song", :class "absolute -top-2 left-2 -mt-px inline-block bg-white px-1 text-xs font-medium text-gray-900"}
    "Search Songs"]
   [:input {:type "text"
            :name "song"
            :id id
            :class "block w-full border-0 p-0 text-gray-900 placeholder-gray-500 focus:ring-0 sm:text-sm"
            :placeholder "Watermelon Man"
            :hx-get "songs-list"
            ;; :hx-push-url "true"
            :hx-trigger "keyup changed delay:500ms"
            :hx-target (hash "../songs-list")}]])

(ctmx/defcomponent ^:endpoint songs-list [{:keys [db] :as req} song]
  (let [all-songs (q/find-all-songs db)
        filtered-songs (search-songs song all-songs)]
    [:div {:class "overflow-hidden bg-white shadow sm:rounded-md"
           :id id}
     (song-list filtered-songs)]))

(ctmx/defcomponent songs-page [req]
  (let [tr (i18n/tr-from-req req)]
    [:div
     (ui/page-header :title (tr [:song/list-title])
                     :buttons (list
                               (ui/button :tag :a :label (tr [:song/log-play])
                                          :priority :primary
                                          :centered? true
                                          :attr {:href "/songs/log-play/"}
                                          :icon icon/plus)
                               (ui/button :tag :a :label (tr [:song/create-title])
                                          :priority :white
                                          :centered? true
                                          :attr {:href "/songs/new"})))
     [:div {:class "flex space-x-4 mt-8 sm:mt-0 bg-white"}
      (songs-filter req)]

     (songs-list req "")
                                        ; (song-toggle-list songs)
     ]))
(defn song-toggler [{:song/keys [title selected]}]
  [:li (comment {:class
                 (ui/cs
                  "rounded border-4 mx-0 my-1 p-2 block basis-1/2 "
                  (if  selected "border-green-200" "border-gray-200"))
                 :_ "on click toggle between .border-gray-200 and .border-green-200"})
   [:label {:for title}
    [:input {:type "checkbox"
             :id title
             :class "sr-only peer"}]
    [:span {:class "rounded border-4 mx-0 my-1 p-2 block basis-1/2 border-gray-200 peer-checked:border-green-200"}  title]]])

(defn song-toggle-list [all-songs]
  [:ul {:class "p-0 m-0 flex flex-wrap"}
   (map song-toggler all-songs)])
