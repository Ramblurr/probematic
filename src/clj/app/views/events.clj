(ns app.views.events
  (:require
   [app.views.shared :as ui]
   [app.controllers.events :as controller]
   [ctmx.response :as response]
   [app.render :as render]
   [app.icons :as icon]
   [ctmx.core :as ctmx]
   [app.db :as db]))

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
                    log the closest parent <li.toggler/>
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

(defn kw->str [kw]
  (str (namespace kw)
       "/"
       (name kw)))

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

(ctmx/defcomponent ^:endpoint event-log-play [req]
  (ctmx/with-req req
    (let [gig-id (-> req :path-params :gig/id)
          result (and post? (controller/log-play! req gig-id))]
      (if (:plays result)
        (response/hx-redirect "/events/")
        (let [conn (-> req :system :conn)
              songs (db/songs @conn)
              gig (db/gig-by-id @conn gig-id)]

          [:form {:id id :hx-post (path ".")
                  :class "space-y-4"}
           (log-play-legend)
           [:ul {:class "toggler-container"}
            (map-indexed (fn [idx song]
                           (let [check-id (str (path "intensive") "_" idx)
                                 radio-id (str (path "feeling") "_" idx)
                                 song-title (:song/title song)
                                 feeling-value  (kw->str (or (-> result :params (get song-title) :played/rating) :play-rating/not-played))

                                 intensive?  (= :play-emphasis/intensiv (-> result :params (get song-title) :played/emphasis))
                                 toggler-class (get {"play-rating/not-played" "toggler--not-played"
                                                     "play-rating/good" "toggler--feeling-good"
                                                     "play-rating/bad" "toggler--feeling-bad"
                                                     "play-rating/ok" "toggler--feeling-meh"} feeling-value)]
                             [:li {:class (render/cs "toggler" toggler-class)}
                              [:input {:type "hidden" :value (:song/title song) :name (str (path "song") "_" idx)}]
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
                                                               {:id  (path "feeling/bad")  :label "Uh-oh" :value "play-rating/bad" :icon icon/sad :size :large :class "icon-sad"  :data "toggler--feeling-bad"}])]
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
                                 (icon/fist-punch {:class "h-8 w-8"})]]]])) songs)]
           [:div
            [:div {:class "flex justify-end"}
             [:a {:href "/songs", :class "btn btn-sm btn-clear-normal"} "Cancel"]
             [:button {:class "ml-3 btn btn-sm btn-indigo-high"
                       :type "submit"} "Save"]]]])))))

(defn event-row [{:gig/keys [status title location date id]}]
  (let [style-icon "mr-1.5 h-5 w-5 flex-shrink-0 text-gray-400"]
    [:a {:href  (str "/event/" id "/") , :class "block hover:bg-gray-50"}
     [:div {:class "px-4 py-4 sm:px-6"}
      [:div {:class "flex items-center justify-between"}
       [:p {:class "truncate text-sm font-medium text-indigo-600"}
        (ui/gig-status-icon status)
        title]
       [:div {:class "ml-2 flex flex-shrink-0"}
        (render/button :tag :a :attr {:href (str  "/event/" id  "/log-play/")} :label "Log Plays" :priority :white-rounded :size :small)]]

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

(ctmx/defcomponent ^:endpoint events-list-page [req]
  (let [events (db/gigs-future @(-> req :system :conn))]
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
                  (event-row event)]) events)])]
      (ui/divider-left "Past")
      [:div {:class "overflow-hidden bg-white shadow sm:rounded-md"
             :id "songs-list"}]]]))
