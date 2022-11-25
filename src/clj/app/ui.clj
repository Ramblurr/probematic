(ns app.ui
  (:refer-clojure :exclude [time])
  (:import
   (java.text DecimalFormat NumberFormat)
   (java.util Locale))
  (:require
   [hiccup2.core :refer [html]]
   [app.humanize :as humanize]
   [app.i18n :as i18n]
   [app.icons :as icon]
   [app.render :as render]
   [app.util :as util]
   [clojure.string :as str]
   [ctmx.render :as ctmx.render]
   [jsonista.core :as j]
   [medley.core :as m]
   [tick.core :as t]))

(defn cs [& names]
  (clojure.string/join " " (filter identity names)))

(defn cs-tw [& names]
  (clojure.string/join " "
                       (->> names (filter identity) (map name))))

(comment
  ;; here's an interesting way to use tailwind outside of strings, as pure symbols
  (defn tw-fn [symbols]
    (mapv name symbols))

  (defmacro tw [& names]
    (tw-fn names))
  ;; example
  [:div {:class (cs (tw min-h-full text-black)
                    (when true (tw border))
                    (when false (tw ml-6)))}]

  ;; not using this because it arguably doesn't bring you much.. and, crucially,
  ;; it breaks tailwind classes with colons in them: i.e., sm:flex
  )

(defn unauthorized-error-body [req]
  (let [tr (i18n/tr-from-req req)
        human-id (:human-id req)]
    [:body {:class "h-full"}
     [:div {:class "min-h-full bg-white px-4 py-16 sm:px-6 sm:py-24 md:grid md:place-items-center lg:px-8"}
      [:div {:class "mx-auto max-w-max"}
       [:main {:class "sm:flex"}
        [:p {:class "text-4xl font-bold tracking-tight text-indigo-600 sm:text-5xl"} "401"]
        [:div {:class "sm:ml-6"}
         [:div {:class "sm:border-l sm:border-gray-200 sm:pl-6"}
          [:h1 {:class "text-4xl font-bold tracking-tight text-gray-900 sm:text-5xl"}
           (tr [:error/unauthorized-title])]
          [:p {:class "mt-1 text-base text-gray-500"}
           (tr [:error/unauthorized-message])]
          [:p {:class "mt-1 text-base text-gray-500"}
           "Error Code: "
           [:span {:class "mt-1 text-base text-red-500 font-mono bg-red-100"}
            human-id]]]]]]
      [:div {:class "flex items-center justify-center mt-10"}
       [:img {:src "/img/tuba-robot-boat-1000.jpg" :class "rounded-md w-full sm:w-1/2"}]]
      [:div {:class "mx-auto max-w-max"}
       [:main {:class "sm:flex"}
        [:div {:class "sm:ml-6"}
         [:div {:class "mt-10 flex space-x-3 sm:border-l sm:border-transparent sm:pl-6"}
          [:a {:href "/", :class "inline-flex items-center rounded-md border border-transparent bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}
           (tr [:error/go-home])]]]]]]
     (render/body-end)]))
(defn not-found-error-body [req]
  (let [tr (i18n/tr-from-req req)
        human-id (:human-id req)]
    [:body {:class "h-full"}
     [:div {:class "min-h-full bg-white px-4 py-16 sm:px-6 sm:py-24 md:grid md:place-items-center lg:px-8"}
      [:div {:class "mx-auto max-w-max"}
       [:main {:class "sm:flex"}
        [:p {:class "text-4xl font-bold tracking-tight text-indigo-600 sm:text-5xl"} "404"]
        [:div {:class "sm:ml-6"}
         [:div {:class "sm:border-l sm:border-gray-200 sm:pl-6"}
          [:h1 {:class "text-4xl font-bold tracking-tight text-gray-900 sm:text-5xl"}
           (tr [:error/not-found-title])]
          [:p {:class "mt-1 text-base text-gray-500"}
           (tr [:error/not-found-message])]
          [:p {:class "mt-1 text-base text-gray-500"}
           "Error Code: "
           [:span {:class "mt-1 text-base text-red-500 font-mono bg-red-100"}
            human-id]]]]]]
      [:div {:class "flex items-center justify-center mt-10"}
       [:img {:src "/img/tuba-robot-boat-1000.jpg" :class "rounded-md w-full sm:w-1/2"}]]
      [:div {:class "mx-auto max-w-max"}
       [:main {:class "sm:flex"}
        [:div {:class "sm:ml-6"}
         [:div {:class "mt-10 flex space-x-3 sm:border-l sm:border-transparent sm:pl-6"}
          [:a {:href "/", :class "inline-flex items-center rounded-md border border-transparent bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}
           (tr [:error/go-home])]
          [:a {:href "#", :class "inline-flex items-center rounded-md border border-transparent bg-indigo-100 px-4 py-2 text-sm font-medium text-indigo-700 hover:bg-indigo-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}
           (tr [:error/notify])]]]]]]
     (render/body-end)]))
(defn unknown-error-body [req]
  (let [tr (i18n/tr-from-req req)
        human-id (:human-id req)]
    [:body {:class "h-full"}
     [:div {:class "min-h-full bg-white px-4 py-16 sm:px-6 sm:py-24 md:grid md:place-items-center lg:px-8"}
      [:div {:class "mx-auto max-w-max"}
       [:main {:class "sm:flex"}

        [:div {:class "sm:ml-6"}
         [:div {:class "sm:border-l sm:border-gray-200 sm:pl-6"}
          [:h1 {:class "text-4xl font-bold tracking-tight text-gray-900 sm:text-5xl"}
           (tr [:error/unknown-title])]
          [:p {:class "mt-1 text-base text-gray-500"}
           (tr [:error/unknown-message])]

          [:p {:class "mt-1 text-base text-gray-500"}
           "Error Code: "
           [:span {:class "mt-1 text-base text-red-500 font-mono bg-red-100"}
            human-id]]]]]]
      [:div {:class "flex items-center justify-center mt-10"}
       [:img {:src "/img/tuba-robot-boat-1000.jpg" :class "rounded-md w-full sm:w-1/2"}]]
      [:div {:class "mx-auto max-w-max"}
       [:main {:class "sm:flex"}
        [:div {:class "sm:ml-6"}
         [:div {:class "mt-10 flex space-x-3 sm:border-l sm:border-transparent sm:pl-6"}
          [:a {:href "/", :class "inline-flex items-center rounded-md border border-transparent bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}
           (tr [:error/go-home])]
          [:a {:href "#", :class "inline-flex items-center rounded-md border border-transparent bg-indigo-100 px-4 py-2 text-sm font-medium text-indigo-700 hover:bg-indigo-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"}
           (tr [:error/notify])]]]]]]
     (render/body-end)]))

(defn error-page-response-fragment [cause req status]
  (render/html-status-response (or status 404)
                               {"HX-Retarget" "body"}
                               (str (html (unknown-error-body req)))))

(defn error-page-response [cause req status]
  (render/html-status-response (or status 404)
                               (render/html5-safe
                                [:head
                                 [:meta {:charset "utf-8"}]
                                 [:meta {:name "viewport"
                                         :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]
                                 [:link {:rel "stylesheet" :href "/css/compiled/main.css"}]]
                                (ctmx.render/walk-attrs
                                 (condp = status
                                   404
                                   (not-found-error-body req)
                                   401
                                   (unauthorized-error-body req)
                                   (unknown-error-body req))))))

(def hx-trigger-types
  {:hx-trigger "HX-Trigger"
   :hx-trigger-after-settle "HX-Trigger-After-Settle"
   :hx-trigger-after-swap "HX-Trigger-After-Swap"})

(defn trigger-response
  ([trigger-name body]
   (trigger-response trigger-name body {}))
  ([trigger-name body {:keys [trigger-type data]
                       :or {trigger-type :hx-trigger}}]
   {:status 200
    :headers {"Content-Type" "text/html" (get hx-trigger-types trigger-type)
              (if data
                (j/write-value-as-string {trigger-name data})
                trigger-name)}
    :body (ctmx.render/html body)}))

(def select-size {:normal "text-base  sm:text-sm py-2 pl-3 pr-10 mt-1"
                  :small "text-xs py-1 pl-2 pr-5 "
                  :xsmall "text-xs py-1 pl-0 pr-0 "})

(def select-label-size {:normal "text-base  sm:text-sm "
                        :small "text-xs"
                        :xsmall "text-xs"})
(defn select [& {:keys [id label options value extra-attrs size required?]
                 :or {extra-attrs {}
                      size :normal}}]
  (let [selected-value value]
    [:div
     [:label {:for id :class (cs (get select-label-size size) "block font-medium text-gray-700")} label]
     [:select (merge {:name id :class
                      (cs  (get select-size size)
                           "block w-full rounded-md border-gray-300 focus:border-indigo-500 focus:outline-none focus:ring-indigo-500")
                      :required required?}

                     extra-attrs)
      (for [{:keys [value label selected?]} options]
        [:option {:selected (if (not (nil? selected?)) selected? (= value selected-value)) :value value} label])]]))

(defn required-label [required?]
  (when required?
    [:p {:class "text-red-400"} "required"]))

(defn select-left [& {:keys [id label options required?]
                      :or {required? true}}]
  [:div {:class "sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:border-t sm:border-gray-200 sm:pt-5"}
   [:label {:for id :class "block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"}
    label (required-label required?)]
   [:div {:class "mt-1 sm:col-span-2 sm:mt-0"}
    [:select {:id id :name id
              :required required?
              :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:max-w-xs sm:text-sm"}
     (for [{:keys [value label selected?]} options]
       [:option {:selected selected? :value value} label])]]])

(def icon-sizes {:small "h-3 w-3"
                 :normal "h-5 w-5"
                 :large "h-10 w-10"})

(def radio-option-state {:active "ring-2 ring-offset-2 ring-indigo-500"
                         :checked "bg-indigo-600 border-transparent text-white hover:bg-indigo-700"
                         :not-checked "bg-white border-gray-200 text-gray-900 hover:bg-gray-50"})

(defn radio-button  [idx {:keys [id name label value opt-id icon size class icon-class model disabled? required?]
                          :or {size :normal
                               class ""
                               required? false
                               icon-class ""
                               disabled? false}}]
  (let [checked? (= model value)]
    [:label {:id id
             :class
             (cs  "radio-button"
                  class
                  (if checked?
                    "radio-button-checked"
                    "radio-button-not-checked"))}

     [:input {:type "radio" :name name :value value :class "sr-only" :aria-labelledby id
              :required required?
              :checked checked?
              :_ "on change trigger radioChanged(value:[@value]) on .sr-only end
                  on radioChanged(value) if I match <:checked/>
                    add .radio-button-checked to the closest parent <label/>
                    remove .radio-button-not-checked from the closest parent <label/>
                  else
                    remove .radio-button-checked from the closest parent <label/>
                    add .radio-button-not-checked to the closest parent <label/>
                  end"}]
     [:span
      (when icon
        (icon {:checked checked?
               :disabled disabled?
               :class
               (cs
                (size icon-sizes)
                icon-class)}))
      (when label
        label)]]))

(defn radio-button-group [& {:keys [options id label class value required?]
                             :or {class ""
                                  required? false}}]
  [:fieldset
   [:legend {:class "block text-sm font-medium text-gray-700"} label]
   [:div {:class (cs "mt-1 flex items-center space-x-3" class)}
    (->> options
         (map #(merge {:name id :model value :required? required?} %))
         (map-indexed radio-button))]])

(defn textarea [& {:keys [label value hint id name rows placeholder]
                   :or {rows 3}}]
  [:div {:class "sm:col-span-6"}
   [:label {:for name :class "block text-sm font-medium text-gray-700"} label]
   [:div {:class "mt-1"}
    [:textarea {:id id :name name :rows rows
                :placeholder placeholder
                :class "block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm"}
     (when value value)]]
   (when hint
     [:p {:class "mt-2 text-sm text-gray-500"}
      hint])])

(def input-container-size {:normal "px-3 py-2 "
                           :small "px-1 py-1  text-xs"})
(def input-label-size {:normal "px-1 text-xs "
                       :small "px-0 text-xs"})
(def input-size {:normal "sm:text-sm"
                 :small "text-xs"})

(defn input [& {:keys [type label name placeholder value extra-attrs class pattern title size required?]
                :or {size :normal
                     required? true}}]
  [:div {:class (cs class (get input-label-size size)
                    (get input-container-size size)
                    "flex-grow relative rounded-md border border-gray-300 shadow-sm focus-within:border-indigo-600 focus-within:ring-1 focus-within:ring-indigo-600")}
   [:label {:for name :class "absolute -top-2 left-2 -mt-px inline-block bg-white font-medium text-gray-900"}
    label]
   [:input (util/remove-nils (merge (or extra-attrs {})
                                    {:class (cs (get input-size size) "block w-full border-0 p-0 text-gray-900 placeholder-gray-500 focus:ring-0")
                                     :type type
                                     :pattern pattern
                                     :title title
                                     :name name
                                     :value (if (= "null" value) nil value)
                                     :required required?
                                     :placeholder placeholder}))]])

(defn text [& opts]
  (apply input (conj opts "text" :type)))

(defn input-datetime [& {:keys [value name required?]}]
  [:input {:type "datetime-local" :name name
           :value (when value (t/date-time value))
           :required required?
           :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm"}])

(defn date [& {:keys [value name required?]}]
  [:input {:type "date" :name name
           :value (when value  (t/date value))
           :required required?
           :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm"}])

(defn input-time [& {:keys [value name required?]}]
  [:input {:type "time" :name name
           :value (when value (t/time value))
           :required required?
           :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm"}])

(def button-priority-classes {:secondary
                              "border-transparent bg-indigo-100 px-4 py-2  text-indigo-700 hover:bg-indigo-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
                              :white
                              "border-gray-300 bg-white px-4 py-2 text-sm  text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 focus:ring-offset-gray-100"
                              :white-destructive
                              "border-red-300 bg-white px-4 py-2 text-sm  text-red-600 shadow-sm hover:bg-red-50 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 focus:ring-offset-gray-100"
                              :primary
                              "border-transparent bg-indigo-600 text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 focus:ring-offset-gray-100"
                              :white-rounded "rounded-full border border-gray-300 bg-white text-gray-700 shadow-sm hover:bg-gray-50"})

(def button-sizes-classes {:2xsmall "px-1.5 py-0.5 text-xs"
                           :xsmall "px-2.5 py-1.5 text-xs"
                           :small "px-3 py-2 text-sm leading-4"
                           :normal "px-4 py-2 text-sm"
                           :large "px-4 py-2 text-base"
                           :xlarge "px-6 py-3 text-base"})

(def button-icon-sizes-classes {:xsmall "h-2 w-2"
                                :small "h-3 w-3"
                                :normal "h-5 w-5"
                                :large "h-5 w-5"
                                :xlarge "h-5 w-5"})

(defn button [& {:keys [tag label disabled? class attr icon priority centered? size hx-target hx-get hx-put hx-post hx-delete hx-vals form]
                 :or   {class ""
                        priority :white
                        size  :normal
                        disabled? false
                        tag :button}}]

  [tag (merge
        (util/remove-nils {:hx-target hx-target :hx-get hx-get :hx-post hx-post :hx-put hx-put :hx-delete hx-delete :hx-vals hx-vals :form form})
        {:class
         (cs
          "inline-flex items-center rounded-md border font-medium"
          ;; "inline-flex items-center border font-medium"
          ;; "inline-flex items-center rounded-md border"
          (size button-sizes-classes)
          (priority button-priority-classes)
          (when centered? "items-center justify-center")
          class)
         :disabled disabled?}
        attr)
   (when icon (icon  {:class (cs (size button-icon-sizes-classes)  (when label "-ml-1 mr-2"))}))
   label])

(defn link-button [& opts]
  (apply button (conj opts :a :tag)))

(defn page-header [& {:keys [title subtitle buttons] :as args}]
  [:div {:class "border-b border-gray-200 px-4 py-4 sm:flex sm:items-center sm:justify-between sm:px-6 lg:px-8 bg-white"}
   [:div {:class "min-w-0 flex-1"}
    [:h1 {:class
          ;; NOTE: i removed sm:truncate from this class list, because it cuts off the input label. later we might need a better solution when a title is very long
          "text-lg font-medium leading-6 text-gray-900"} title]
    (when subtitle
      [:p {:class "text-sm font-medium text-gray-500"}
       subtitle])]
   [:div {;; :class "mt-4 flex sm:mt-0 sm:ml-4"
          :class "justify-stretch mt-6 flex flex-col-reverse space-y-4 space-y-reverse sm:flex-row-reverse sm:justify-end sm:space-y-0 sm:space-x-3 sm:space-x-reverse md:mt-0 md:flex-row md:space-x-3"}
    buttons
    ;; [:button {:type "button" :class "sm:order-0 order-1 ml-3 inline-flex items-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 sm:ml-0"} "Share"]
    ;; [:button {:type "button" :class "order-0 inline-flex items-center rounded-md border border-transparent bg-purple-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-purple-700 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 sm:order-1 sm:ml-3"} "Create"]
    ]])

(defn page-header2 [& {:keys [title subtitle buttons] :as args}]
  [:div {:class "px-4 py-4 sm:flex sm:items-center sm:justify-between sm:px-6 lg:px-8"}
   [:div {:class "flex items-center space-x-5"}
    [:div
     [:h1 {:class "text-2xl font-bold text-gray-900 w-full"} title]
     (when subtitle
       [:p {:class "text-sm font-medium text-gray-500"}
        subtitle])]]
   [:div {:class "justify-stretch mt-6 flex flex-col-reverse space-y-4 space-y-reverse sm:flex-row-reverse sm:justify-end sm:space-y-0 sm:space-x-3 sm:space-x-reverse md:mt-0 md:flex-row md:space-x-3"}
    buttons]])

(defn page-header-full [& {:keys [title subtitle buttons] :as args}]
  [:div {:class "px-4 py-4 sm:flex sm:items-center sm:justify-between sm:px-6 lg:px-8 bg-white"}
   [:div {:class "flex items-center space-x-5 w-full sm:w-1/2"}
    [:div {:class "w-full"}
     [:h1 {:class "text-2xl font-bold text-gray-900 w-full"} title]
     (when subtitle
       [:p {:class "text-sm font-medium text-gray-500 w-full"}
        subtitle])]]
   [:div {:class "justify-stretch mt-6 flex flex-col-reverse space-y-4 space-y-reverse sm:flex-row-reverse sm:justify-end sm:space-y-0 sm:space-x-3 sm:space-x-reverse md:mt-0 md:flex-row md:space-x-3"}
    buttons]])

(defn member-nick
  "Renders the nickname of the member, if available, otherwise renders the name."
  [{:member/keys [name nick]}]
  (if (str/blank? nick)
    name
    nick))

(defn member-section
  "Renders the member's section"
  [{:member/keys [section]}]
  (if section
    (:section/name section)
    "No Section"))

(defn member-select [& {:keys [id value label members variant]
                        :or {label "Member"
                             variant :inline}}]
  (let [options
        (->> members
             (map (fn [m] {:value (:member/gigo-key m) :label (member-nick m)}))
             (sort-by :label))]
    (condp = variant
      :inline (select :id id
                      :label label
                      :value value
                      :options options)

      :inline-no-label (select :id id
                               :label ""
                               :value value
                               :options options)
      :left (select-left :id id
                         :label label
                         :value value
                         :options options))))

(defn section-select [& {:keys [id value label sections extra-attrs]}]
  (let [options (map (fn [{:section/keys [name]}]
                       {:value name :label name}) sections)]
    (select :id id :label label :value value :options options :extra-attrs extra-attrs)))

(defn motivation-select [& {:keys [id value label motivations extra-attrs]}]
  (let [options (map (fn [[k v]]
                       {:value k :label v}) motivations)]
    (select :id id :label label :value value :options options :extra-attrs extra-attrs :size :small)))

(defn song-select [& {:keys [id value label songs extra-attrs size] :or {size :small}}]
  (let [options (map (fn [{:song/keys [title song-id]}]
                       {:value song-id :label title}) songs)]
    (select :id id :label label :value value :options options :extra-attrs extra-attrs :size size)))

(defn instrument-select [& {:keys [id value label instruments :variant]
                            :or {label "Instrument"
                                 variant :inline}}]
  (let [options
        (map (fn [m]
               {:value (:instrument/instrument-id m)
                :label (str (-> m :instrument/owner :member/name) " " (:instrument/name m))})
             instruments)]
    (condp = variant
      :inline (select :id id
                      :label label
                      :value value
                      :options options)

      :inline-no-label (select :id id
                               :label ""
                               :value value
                               :options options)
      :left (select-left :id id
                         :label label
                         :value value
                         :options options))))

(defn instrument-category-select [& {:keys [id value label categories :variant]
                                     :or {label "Instrument Category"
                                          variant :inline}}]
  (let [options
        (map (fn [c]
               {:value (:instrument.category/category-id c)
                :label (:instrument.category/name c)})
             categories)]
    (condp = variant
      :inline
      (select :id id
              :label label
              :value value
              :options options)

      :inline-no-label
      (select :id id
              :label ""
              :value value
              :options options)
      :left
      (select-left :id id
                   :label label
                   :value value
                   :options options))))

(defn text-left [& {:keys [id value label placeholder required?]
                    :or {required? true}}]
  [:div {:class "sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:border-t sm:border-gray-200 sm:pt-5"}
   [:label {:for id :class "block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"}
    label (required-label required?)]
   [:div {:class "mt-1 sm:col-span-2 sm:mt-0"}
    [:input {:type "text"
             :name id :id id
             :value value
             :placeholder placeholder
             :required required?
             :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:max-w-xs sm:text-sm"}]]])

(defn factor-input [& {:keys [name value required? label]
                       :or {required? true}}]
  (input :value value :type "number"
         :name name
         :label label
         :required? required?
         :extra-attrs {:step "0.00000001" :min "0" :max "2.0"}))

(defn table-header [{:keys [title description body buttons]} & more]
  [:div {:class "px-4 sm:px-6 lg:px-8"}
   [:div {:class "sm:flex sm:items-center"}
    [:div {:class "sm:flex-auto"}
     [:h1 {:class "text-2xl font-semibold text-gray-900"} title]
     [:p {:class "mt-2 text-sm text-gray-700"} description]]
    [:div {:class "mt-4 sm:mt-0 sm:ml-16 sm:flex-none"}
     buttons]]
   [:div {:class "-mx-4 mt-8 overflow-hidden shadow ring-1 ring-black ring-opacity-5 sm:-mx-6 md:mx-0 md:rounded-lg"}
    (into [] (concat
              [:table {:class "min-w-full divide-y divide-gray-300"}]
              more))]])

(def table-row-priorities
  {:important ""
   :normal "sm:pl-6"
   :medium "hidden sm:table-cell"
   :low "hidden lg:table-cell"})

(def table-row-variant
  {:action "relative py-3.5 pl-3 pr-4 sm:pr-6"
   :number "text-right"})

(->>
 [{:label "Name" :priority :normal :key :name}
  {:label "Owner" :priority :medium :key :owner}
  {:label "Coverage" :priority :normal :key :coverage}
  {:label "Edit" :variant :action}]
 (group-by :key)
 (m/map-vals first))
[{}]

(into []
      (concat [1 2 3] [4 5 6]))

(defn table-row-head [headers]
  [:thead {:class "bg-gray-50"}
   [:tr
    (map-indexed (fn [idx {:keys [label variant priority]}]
                   [:th {:scope "col"
                         :class (cs
                                 "text-left text-sm font-semibold text-gray-900"
                                 (if  (= idx 0) "pl-4 pr-3" "px-3 py-3.5 ")
                                 (get table-row-priorities priority "")
                                 (get table-row-variant variant ""))}
                    [:span {:class (cs (when (= variant :action) "sr-only"))} label]])
                 headers)]])
(defn table-body [& more]
  (into [] (concat
            [:tbody {:class "divide-y divide-gray-200 bg-white"}]
            more)))

(defn table-body-old [headers data]
  (let [column-keys (->> headers (map :key) (filter some?))
        grouped-cols (->> headers (group-by :key) (m/map-vals first))]
    [:div {:class "-mx-4 mt-8 overflow-hidden shadow ring-1 ring-black ring-opacity-5 sm:-mx-6 md:mx-0 md:rounded-lg"}
     [:table {:class "min-w-full divide-y divide-gray-300"}
      (table-row-head headers)

      [:tbody {:class "divide-y divide-gray-200 bg-white"}
       (map-indexed (fn [_idx data-row]
                      [:tr
                       (map-indexed (fn [col-idx col-key]
                                      [:td {:class
                                            (cs
                                             (if (= 0 col-idx) "w-full max-w-0 py-4 pl-4 pr-3 sm:w-auto   sm:max-w-none sm:pl-6" "px-3 py-4")
                                             (get table-row-priorities (get-in grouped-cols [col-key :priority]))
                                             "text-sm font-medium text-gray-900")}
                                       (if-let [render-fn (get-in grouped-cols [col-key :render-fn])]
                                         (render-fn col-key data-row)
                                         (get data-row col-key))])
                                    column-keys)])
                    data)]]]))

(def currency-symbols {:EUR "€"})
(def currency-default-locale {:EUR Locale/GERMANY
                              :USD Locale/US})

(defn money-formatter
  ([] (money-formatter (Locale/getDefault)))
  ([^Locale locale]
   (NumberFormat/getCurrencyInstance locale)))

(defn money-format [v currency]
  (.format ^DecimalFormat (money-formatter (get currency-default-locale currency Locale/GERMANY)) v))

(defn money [value currency]
  (when value
    [:span
     [:span (money-format value currency)]]))

(defn money-input [& {:keys [label required? id]
                      :or {required? true}}]
  [:div
   [:label {:for "price", :class "block text-sm font-medium text-gray-700"} label]
   [:div {:class "relative mt-1 rounded-md shadow-sm"}
    [:div {:class "pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3"}
     [:span {:class "text-gray-500 sm:text-sm"} (:EUR currency-symbols)]]
    [:input {:type "text" :required required? :name id :id id :class "block w-full rounded-md border-gray-300 pl-7 pr-12 focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm" :placeholder "0.00"}]
    [:div {:class "pointer-events-none absolute inset-y-0 right-0 flex items-center pr-3"}
     [:span {:class "text-gray-500 sm:text-sm"} "EUR"]]]])

(defn checkbox [& {:keys [label id]}]
  [:div {:class "mt-2 relative flex items-start"}
   [:div {:class "flex h-5 items-center"}
    [:input {:type "checkbox" :id id :name id :class "h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"}]]
   [:div {:class "ml-3 text-sm"}
    [:label {:for id :class "font-medium text-gray-700"} label]]])

(defn toggle [& {:keys [label hx-target hx-get hx-vals active? id]}]
  [:div {:class "flex items-center"}
   [:button {:type "button" :hx-target hx-target :hx-get hx-get :hx-vals hx-vals
             :class (cs (if active? "bg-indigo-600" "bg-gray-200") "relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2")
             :_ "on click toggle between .bg-gray-200 and .bg-indigo-600 end
                 on click toggle between .translate-x-5 and .translate-x-0 on <span/> in me end "
             :role "switch"}
    [:span {:aria-hidden "true"
            :class (cs (if active? "translate-x-5" "translate-x-0") "pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out")}]]
   [:span {:class "ml-3"}
    [:span {:class "text-sm font-medium text-gray-900"} label]]])

(defn toggle-checkbox [& {:keys [label checked? name]}]
  [:label {:for name :class "inline-flex relative items-center cursor-pointer"}
   [:input {:type "checkbox" :checked checked? :class "sr-only peer" :name name :id name}]
   [:div {:class  (cs
                   "w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-indigo-300 dark:peer-focus:ring-indigo-800 rounded-full peer"
                   "dark:bg-gray-700 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px]"
                   "after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all"
                   "dark:border-gray-600 peer-checked:bg-indigo-600")}]
   [:span {:class "ml-3 text-sm font-medium text-gray-900 dark:text-gray-300"} label]])

(defn toggle-checkbox-left [& {:keys [label checked? name id]}]
  [:div {:class "sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:border-t sm:border-gray-200 sm:pt-5"}
   [:label {:for id :class "block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"}
    label]
   [:div {:class "mt-1 sm:col-span-2 sm:mt-0"}
    [:label {:for name :class "inline-flex relative items-center cursor-pointer"}
     [:input {:type "checkbox" :checked checked? :class "sr-only peer" :name name :id id}]
     [:div {:class  (cs
                     "w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-indigo-300 dark:peer-focus:ring-indigo-800 rounded-full peer"
                     "dark:bg-gray-700 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px]"
                     "after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all"
                     "dark:border-gray-600 peer-checked:bg-indigo-600")}]

             ;; :class "block w-full max-w-lg rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:max-w-xs sm:text-sm"
     [:span {:class "ml-3 text-sm font-medium text-gray-900 dark:text-gray-300"}]]]])

(defn avatar-img [member & {:keys [class]}]
  [:img {:class class
         :src (if-let [avatar-template (:member/avatar-template member)]
                (str "https://forum.streetnoise.at"
                     (str/replace avatar-template "{size}" "200"))
                "/img/default-avatar.png")}])

(defn hx-vals
  "Serializes the passed map as a json object, useful with :hx-vals. Omits nils"
  [m]

  (j/write-value-as-string
   (util/remove-nils m)))

(defn divider-center [title]
  [:div {:class "relative"}
   [:div {:class "absolute inset-0 flex items-center", :aria-hidden "true"}
    [:div {:class "w-full border-t border-gray-300"}]]
   [:div {:class "relative flex justify-center"}
    [:span {:class "bg-white px-3 text-lg font-medium text-gray-900"}
     title]]])

(defn divider-left
  ([title] (divider-left title nil))
  ([title button]
   [:div {:class "relative ml-4 sm:ml-0 "}
    [:div {:class "absolute inset-0 flex items-center", :aria-hidden "true"}
     [:div {:class "w-full border-t border-gray-300"}]]
    [:div {:class "relative flex items-center justify-between mb-2"}
     [:span {:class "bg-gray-100 pr-3 text-lg font-medium text-gray-900"}
      title]
     (when button
       button)]]))

(defn bool-bubble
  ([is-active]
   (bool-bubble is-active {true "Aktiv" false "Inaktiv"}))
  ([is-active labels]
   [:span {:class
           (cs "px-2 inline-flex text-xs leading-5 font-semibold rounded-full"
               (when is-active "text-green-800 bg-green-100")
               (when (not is-active) "text-red-800 bg-red-100"))}
    (get labels is-active)]))

(def gig-status-colors
  {:gig.status/confirmed {:text-color "text-green-800"
                          :bg-color "bg-green-100"
                          :label "Confirmed"}
   :gig.status/unconfirmed {:text-color "text-orange-800"
                            :bg-color "bg-orange-100"
                            :label "Unconfirmed"}

   :gig.status/cancelled {:text-color "text-red-800"
                          :bg-color "bg-red-100"
                          :label "Cancelled"}})

(def gig-status-icons
  {:gig.status/confirmed {:icon icon/circle-check
                          :color "text-green-500"}
   :gig.status/unconfirmed {:icon icon/circle-question
                            :color "text-orange-500"}
   :gig.status/cancelled {:icon icon/circle-xmark
                          :color "text-red-500"}})

(defn gig-status-bubble [status]
  (let [{:keys [text-color bg-color label]} (gig-status-colors status)]
    [:span {:class
            (cs "px-2 inline-flex text-xs leading-5 font-semibold rounded-full"
                text-color bg-color)}
     label]))

(defn song-active-bubble [song]
  (let [[text-color bg-color label] (if (:song/active? song)
                                      ["text-green-500" "bg-green-100" "Active"]
                                      ["text-red-500" "bg-red-100" "Inactive"])]
    [:span {:class
            (cs "px-2 inline-flex text-xs leading-5 font-semibold rounded-full"
                text-color bg-color)}
     label]))

(defn gig-status-icon [status]
  (let [{:keys [icon color]} (gig-status-icons status)]
    ;; mr-1.5
    (icon {:class (str " h-5 w-5 inline " color)})))

(defn format-dt [dt]
  (t/format (t/formatter "dd-MMM-yyyy") dt))

(defn gig-date-all [{:gig/keys [date call-time]}]
  (cond
    (and date call-time)
    (let [dt (t/at date call-time)]
      [:time {:datetime (str dt)}
       (t/format (t/formatter "dd.MM.yy E HH:mm") dt)])
    (some? date)
    [:time {:datetime (str date)}
     (t/format (t/formatter "dd.MM.yy E") date)]
    :else nil))

(defn gig-date [{:gig/keys [date call-time]}]
  (when date
    (t/format (t/formatter "dd.MM.yy E") date)))

(defn gig-time [{:gig/keys [date call-time]}]
  (when call-time
    (t/format (t/formatter "HH:mm") (t/at date call-time))))

(defn datetime [dt]
  (if dt
    [:time {:dateetime (str dt)}
     (format-dt dt)]
    "never"))

(defn time [t]
  (when t
    (t/format (t/formatter "HH:mm") t)))

(defn humanize-dt [dt]
  (when dt
    (let [local-dt (cond (inst? dt) (t/date-time dt)
                         :else dt)]
      [:time {:datetime (str local-dt)}
       (humanize/from local-dt)])))

(defn dl-item
  ([label value]
   (dl-item label value "sm:col-span-1"))
  ([label value class]
   [:div {:class (cs class)}
    [:dt {:class "text-sm font-medium text-gray-500"} label]
    [:dd {:class "mt-1 text-sm text-gray-900"}
     value]]))

(defn dl [& items]
  [:dl {:class "grid grid-cols-1 gap-x-4 gap-y-8 sm:grid-cols-3"}
   items])

(defn panel [{:keys [id title subtitle buttons]} & body]
  [:div {:class "mx-auto mt-8 grid max-w-3xl grid-cols-1 gap-6 sm:px-6 lg:max-w-7xl lg:grid-flow-col-dense lg:grid-cols-3" :id id}
   [:div {:class "space-y-6 lg:col-span-3 lg:col-start-1"}
    [:section
     [:div {:class "bg-white shadow sm:rounded-lg"}
      (when (or title buttons)
        [:div {:class "px-4 py-5 px-6  flex items-center justify-between "}
         (when (or title subtitle)
           [:div
            [:h2 {:class "text-lg font-medium leading-6 text-gray-900"} title]
            (when subtitle
              [:p {:class "text-sm font-medium text-gray-500 w-full"}
               subtitle])])
         [:div {:class "space-x-2 flex"}
          buttons]])
      [:div {:class "border-t border-gray-200 px-4 py-5 sm:px-6"}
       body]]]]])

(defn rich-li-action [body]
  [:div {:class "ml-4 flex-shrink-0"} body])

(defn rich-li-action-a [& {:keys [href label attrs]}]
  [:div {:class "ml-4 flex-shrink-0"} [:a
                                       (merge
                                        {:href href :class "font-medium text-blue-600 hover:text-blue-500"}
                                        attrs) label]])
(defn rich-li-text [_ body]
  [:span {:class "ml-2 w-0 flex-1 truncate"} body])

(defn rich-li [{:keys [icon]} & items]
  [:li {:class "flex items-center justify-between py-3 pl-3 pr-4 text-sm"}
   [:div {:class "flex w-0 flex-1 items-center"}
    (when icon
      (icon {:class "h-5 w-5 flex-shrink-0 text-gray-400"}))
    items]])

(defn rich-ul [_ & items]
  [:ul {:role "list", :class "divide-y divide-gray-200 rounded-md border border-gray-200"}
   items])
