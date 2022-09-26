(ns app.routes.songs
  (:require
   [app.render :as render]
   [app.icons :as icon]
   [ctmx.core :as ctmx]
   [tick.core :as t]
   [clojure.string :as clojure.string]))
(defn bool-bubble [is-active]
  [:span {:class
          (render/cs "px-2 inline-flex text-xs leading-5 font-semibold rounded-full"
                     (when is-active "text-green-800 bg-green-100")
                     (when (not is-active) "text-red-800 bg-red-100"))}
   (if is-active "Active" "Inactive")])

(defn datetime [dt]
  (if dt
    [:time {:dateetime (str dt)}
     (t/format (t/formatter "dd-MMM-yyyy") dt)]
    "never"))

(defn song-row [{:song/keys [title active last-played score play-count]}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"]
    [:a {:href "#", :class "block hover:bg-gray-50"}
     [:div {:class "px-4 py-4 sm:px-6"}
      [:div {:class "flex items-center justify-between"}
       [:p {:class "truncate text-sm font-medium text-indigo-600"}
        title]
       [:div {:class "ml-2 flex flex-shrink-0"}
        (bool-bubble active)]]
      [:div {:class "mt-2 sm:flex sm:justify-between"}
       [:div {:class "sm:flex"}
        [:p {:class "flex items-center text-sm text-gray-500"}
         (icon/hashtag {:class style-icon})
         play-count]
        [:p {:class "mt-2 flex items-center text-sm text-gray-500 sm:mt-0 sm:ml-6"}

         (icon/star-outline {:class style-icon})
         score]]
       [:div {:class "mt-2 flex items-center text-sm text-gray-500 sm:mt-0"}
        (icon/calendar {:class style-icon})
        [:p "Last Played "
         (datetime last-played)]]]]]))

(defn song-list [songs]
  (if (empty? songs)
    "Songs not found"
    [:ul {:role "list", :class "divide-y divide-gray-200"}
     (map (fn [song]
            [:li
             (song-row song)]) songs)]))

(def _songs [{:song/title "Hooray"
              :song/score 0
              :song/play-count 1
              :song/last-played (t/today)
              :song/active true}
             {:song/title "Watermelon Man"
              :song/score 0
              :song/play-count 1
              :song/last-played (t/today)
              :song/active true}])

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
  [:div {:class "relative rounded-md border border-gray-300 px-3 py-2 shadow-sm focus-within:border-indigo-600 focus-within:ring-1 focus-within:ring-indigo-600"}
   [:label {:for "song", :class "absolute -top-2 left-2 -mt-px inline-block bg-white px-1 text-xs font-medium text-gray-900"}
    "Song"]
   [:input {:type "text"
            :name "song"
            :id "song"
            :class "block w-full border-0 p-0 text-gray-900 placeholder-gray-500 focus:ring-0 sm:text-sm"
            :placeholder "Watermelon Man"
            :hx-get "songs-list"
            :hx-trigger "keyup changed delay:500ms"
            :hx-target "#songs-list"}]])

(ctmx/defcomponent ^:endpoint songs-list [req song]
  (let [filtered-songs (search-songs song _songs)]

    [:div {:class "overflow-hidden bg-white shadow sm:rounded-md"
           :id "songs-list"}
     (song-list filtered-songs)]))

(defn songs-routes []
  (ctmx/make-routes
   "/songs"
   (fn [req]
     (render/html5-response
      [:div {:class "mt-6"}
       (songs-filter req)
       (songs-list req "")]))))
