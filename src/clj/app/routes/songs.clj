(ns app.routes.songs
  (:require
   [app.routes.shared :as ui]
   [app.render :as render]
   [app.db :as db]
   [app.icons :as icon]
   [ctmx.core :as ctmx]
   [ctmx.form :as form]
   [tick.core :as t]
   [clojure.string :as clojure.string]
   [ctmx.response :as response]))

(defn song-row [{:song/keys [title active last-played score play-count]}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"]
    [:a {:href (str  "/song/" title "/"), :class "block hover:bg-gray-50"}
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

(ctmx/defcomponent ^:endpoint songs-list [req song]
  (let [all-songs (db/songs @(-> req :system :conn))
        filtered-songs (search-songs song all-songs)]
    [:div {:class "overflow-hidden bg-white shadow sm:rounded-md"
           :id id}
     (song-list filtered-songs)]))

(defn song-toggler [{:song/keys [title selected]}]
  [:li (comment {:class
                 (render/cs
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

(defn songs-list-routes []
  (ctmx/make-routes
   "/songs"
   (fn [req]
     (render/html5-response
      [:div
       (render/page-header :title "Songs"
                           :buttons (list
                                     (render/button :label "Log Play"
                                                    :priority :primary
                                                    :centered? true
                                                    :attr {:href "/songs/log-play/"}
                                                    :icon icon/plus)
                                     (render/button :label "New Song"
                                                    :priority :white
                                                    :centered? true
                                                    :attr {:href "/songs/new"})))
       [:div {:class "flex space-x-4 mt-8 sm:mt-0"}
        (songs-filter req)]

       (songs-list req "")
                                        ; (song-toggle-list songs)
       ]))))

(ctmx/defcomponent ^:endpoint song-new [req song-name]
  (case (:request-method req)
    :post
    (let [song (-> req
                   :params form/json-params-pruned
                   :song)
          conn (-> req :system :conn)]
      (db/create-song! conn song)
      (response/redirect "/songs/"))

    [:form {:id id :hx-post "song-new" :class "mt-6"}
     (render/text "New Song Name"  (path "song") "Watermelon Man" (value "song"))
     [:div {:class "pt-5"}
      [:div {:class "flex justify-end"}
       [:a {:href "/songs", :class "btn btn-sm btn-clear-normal"} "Cancel"]
       [:button {:class "ml-3 btn btn-sm btn-indigo-high"
                 :type "submit"
                 :hx-swap-oob "true"} "Save"]]]]))

(defn songs-new-routes []
  (ctmx/make-routes
   "/songs/new"
   (fn [req]
     (render/html5-response
      (song-new req "")))))

(defn song-detail [req song-title]
  (if-let [song (db/song-by-title @(-> req :system :conn) song-title)]
    [:div
     (render/page-header :title song-title

                         :subtitle (list  "Last played " (ui/datetime (:song/last-played song)))
                         :buttons (list  (render/button :label "Comment"
                                                        :priority :white
                                                        :centered? true
                                                        :attr {:href "/songs/new"})
                                         (render/button :label "Log Play"
                                                        :priority :primary
                                                        :centered? true
                                                        :class "items-center justify-center "
                                                        :attr {:href (str "/song/log-play/" song-title "/")})))]

    [:div
     [:p "Song not found."]
     [:a {:href "/songs/" :class "underline hover:text-indigo-600 text-indigo-500"} "Back to song list"]]))

(defn song-detail-routes []
  (ctmx/make-routes
   "/song/{song/title}/"
   (fn [req]
     (render/html5-response
      (song-detail req (-> req :path-params :song/title))))))

(defn song-select [songs]
  [:div
   [:label {:for "song", :class "block text-sm font-medium text-gray-700"} "Song"]
   [:select {:id "song", :name "location", :class "mt-1 block w-full rounded-md border-gray-300 py-2 pl-3 pr-10 text-base focus:border-indigo-500 focus:outline-none focus:ring-indigo-500 sm:text-sm"}
    (for [song songs]
      [:option {:selected false} (:song/title song)])]])

(defn gig-select [gigs]
  [:div
   [:label {:for "song", :class "block text-sm font-medium text-gray-700"} "Gig/Probe"]
   [:select {:id "song", :name "location", :class "mt-1 block w-full rounded-md border-gray-300 py-2 pl-3 pr-10 text-base focus:border-indigo-500 focus:outline-none focus:ring-indigo-500 sm:text-sm"}
    (for [{:gig/keys [id date title]} gigs]
      [:option {:selected false :value id}])]])

(ctmx/defcomponent ^:endpoint songs-log-play [req]
  (case (:request-method req)
    :post
    (let [params (-> req
                     :params form/json-params-pruned)
          conn (-> req :system :conn)]
      ;; TODO log the play
      (response/redirect "/songs/"))
    (let [conn (-> req :system :conn)
          songs (db/songs @conn)
          gigs (db/gigs @conn)]

      [:form {:id id :hx-post (path ".")}
   ;; (render/text "New Song Name"  (path "song") "Watermelon Man" (value "song"))
       (list
        (render/select (path "song") "Songs" (map (fn [s]
                                                    {:value (:song/title s)
                                                     :label (:song/title s)
                                                     :selected? false}) songs))

        (render/select (path "gig") "Gig/Probe" (map (fn [{:gig/keys [id title date]}]
                                                       {:value id
                                                        :label (str title " " (when date (ui/format-dt date)))
                                                        :selected? false}) gigs)))
       [:div
        [:div {:class "flex justify-end"}
         [:a {:href "/songs", :class "btn btn-sm btn-clear-normal"} "Cancel"]
         [:button {:class "ml-3 btn btn-sm btn-indigo-high"
                   :type "submit"
                   :hx-swap-oob "true"} "Save"]]]])))

(defn songs-log-play-routes []
  (ctmx/make-routes
   "/songs/log-play/"
   (fn [req]
     (render/html5-response
      [:div
       (render/page-header :title "Log Play")
       (songs-log-play req)]))))

(defn songs-routes []
  [""
   (song-detail-routes)
   (songs-log-play-routes)
   (songs-list-routes)
   (songs-new-routes)])
