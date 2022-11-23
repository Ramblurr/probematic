(ns app.songs.views
  (:require
   [app.gigs.controller :as gig.controller]
   [app.i18n :as i18n]
   [app.icons :as icon]
   [app.queries :as q]
   [app.songs.controller :as controller]
   [app.ui :as ui]
   [app.urls :as url]
   [app.util :as util]
   [clojure.string :as clojure.string]
   [ctmx.core :as ctmx]
   [ctmx.response :as response]))

(ctmx/defcomponent ^:endpoint songs-log-play [{:keys [db] :as req}]
  (ctmx/with-req req
    (let [result (and post? (controller/log-play! req))]
      (if (:play result)
        (response/hx-redirect "/songs/")
        (let [conn (-> req :system :conn)
              songs (q/find-all-songs db)
              gigs (gig.controller/find-all-gigs db)]

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

(defn song-detail [{:keys [db] :as req} song-id]
  (if-let [{:song/keys [title last-played]} (controller/retrieve-song db song-id)]
    [:div
     (ui/page-header :title title
                     :subtitle (list  "Last played " (ui/datetime last-played))
                     :buttons (list  (ui/button :label "Comment"
                                                :priority :white
                                                :centered? true
                                                :attr {:href "/songs/new"})
                                     (ui/button :label "Log Play"
                                                :priority :primary
                                                :centered? true
                                                :class "items-center justify-center "
                                                :attr {:href (str "/song/log-play/" song-id "/")})))]

    [:div
     [:p "Song not found."]
     [:a {:href "/songs/" :class "underline hover:text-indigo-600 text-indigo-500"} "Back to song list"]]))
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

(defn song-row [{:song/keys [title active last-played score play-count] :as song}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"]
    [:a {:href (url/link-song song) :class "block hover:bg-gray-50"}
     [:div {:class "px-4 py-4 sm:px-6"}
      [:div {:class "flex items-center justify-between"}
       [:p {:class "truncate text-sm font-medium text-indigo-600"}
        title]
       [:div {:class "ml-2 flex flex-shrink-0"}
        (ui/bool-bubble active)]]
      [:div {:class "mt-2 sm:flex sm:justify-between"}
       [:div {:class "flex"}
        [:p {:class "flex items-center text-sm text-gray-500"}
         (icon/hashtag {:class style-icon})
         "Played "
         play-count]
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
  [:div
   (ui/page-header :title "Songs"
                   :buttons (list
                             (ui/button :tag :a :label "Log Play"
                                        :priority :primary
                                        :centered? true
                                        :attr {:href "/songs/log-play/"}
                                        :icon icon/plus)
                             (ui/button :tag :a :label "New Song"
                                        :priority :white
                                        :centered? true
                                        :attr {:href "/songs/new"})))
   [:div {:class "flex space-x-4 mt-8 sm:mt-0 bg-white"}
    (songs-filter req)]

   (songs-list req "")
                                        ; (song-toggle-list songs)
   ])
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
