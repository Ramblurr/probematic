(ns user)

(defn dev
  "Load and switch to the 'dev' namespace."
  []
  (require 'dev)
  (in-ns 'dev)
  :loaded)

(comment
;;;  manual htmx handler
  (defn songs-routes-manual [{:keys [conn]}]
    [""
     ["/songs" {:get {:handler (partial #'handler-list-songs conn)
                      :parameters {:query [:map [:song {:default ""}  :string]]}}}]])
  (defn handler-list-songs [conn {:keys [parameters htmx?] :as req}]
    (let [{:keys [song]} (:query parameters)]
      (if htmx?
        (render/partial-response
         (songs-list req conn song))
        (render/html5-response
         [:div {:class "mt-6"}
          [:div {:class "flex space-x-4"}
           (songs-filter req)
           [:a {:href "/songs/new" :class "flex-initial inline-flex items-center rounded-md border border-transparent bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}  "<!-- Heroicon name: mini/envelope -->"
            (icon/plus {:class "-ml-1 mr-2 h-5 w-5"})
            "Song"]]

          (songs-list req conn song)
                                        ; (song-toggle-list songs)
          ]))))

  (dev)
  ;;
  )
