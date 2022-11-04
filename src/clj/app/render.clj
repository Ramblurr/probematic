(ns app.render
  (:import [java.text NumberFormat DecimalFormat]
           [org.w3c.tidy Tidy]
           [java.util Locale]
           [java.io ByteArrayInputStream ByteArrayOutputStream])
  (:require
   [clojure.string :as clojure.string]
   [hiccup.core :as hiccup]
   [hiccup.page :as hiccup.page]
   [hiccup2.core :refer [html]]
   [medley.core :as m]
   [ctmx.render :as ctmx.render]
   [ctmx.response :as response]
   [app.util :as util]))

(defmacro html5-safe
  "Create a HTML5 document with the supplied contents. Using hiccup2.core/html to auto escape strings"
  [options & contents]
  (if-not (map? options)
    `(html5-safe {} ~options ~@contents)
    (if (options :xml?)
      `(let [options# (dissoc ~options :xml?)]
         (str (html {:mode :xml}
                    (hiccup.page/xml-declaration (options# :encoding "UTF-8"))
                    (hiccup.page/doctype :html5)
                    (hiccup.page/xhtml-tag options# (options# :lang) ~@contents))))
      `(let [options# (dissoc ~options :xml?)]
         (str (html {:mode :html}
                    (hiccup.page/doctype :html5)
                    [:html options# ~@contents]))))))

(defn configure-pretty-printer
  "Configure the pretty-printer (an instance of a JTidy Tidy class) to
generate output the way we want -- formatted and without sending warnings.
 Return the configured pretty-printer."
  []
  (doto (new Tidy)
    (.setSmartIndent true)
                                        ;(.setTrimEmptyElements true)
    (.setShowWarnings false)
    (.setQuiet true)))

(defn pretty-print-html
  "Pretty-print the html and return it as a string."
  [html]
  (let [swrtr (ByteArrayOutputStream.)]
    (.parse (configure-pretty-printer) (ByteArrayInputStream. (.getBytes (str html)))  swrtr)
    (str swrtr)))

(defn html-response [body]
  {:status 200
   :headers {"Content-Type" "text/html"}
   ;:body (pretty-print-html body)
   :body  body})

(defn html5-response
  ([body] (html5-response nil body))
  ([js body]
   (html-response
    (html5-safe
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport"
              :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]

      ;; [:link {:rel "stylesheet" :href "https://rsms.me/inter/inter.css"}]
      [:link {:rel "stylesheet"
              :href "/css/compiled/main.css"}]]
     [:body body]

     [:script {:src "/js/hyperscript.org@0.9.7.js"
               :integrity "sha384-6GYN8BDHOJkkru6zcpGOUa//1mn+5iZ/MyT6mq34WFIpuOeLF52kSi721q0SsYF9"}]
     [:script {:src "/js/htmx.js"
               :integrity "sha384-mrsv860ohrJ5KkqRxwXXj6OIT6sONUxOd+1kvbqW351hQd7JlfFnM0tLetA76GU0"}]
     [:script {:src "/js/nprogress.js"}]
     [:script {:src "/js/helpers.js"}]
     (when js [:script {:src (str "/js" js)}])))))

(defn snippet-response [body]
  (cond
    (not body) response/no-content
    (map? body) body
    :else (-> body ctmx.render/html response/html-response)))

(defn trigger-response [trigger-name body]
  {:status 200
   :headers {"Content-Type" "text/html" "HX-Trigger" trigger-name}
   :body (ctmx.render/html body)})

(defn partial-response [body]
  (html-response
   (hiccup/html body)))

(defn cs [& names]
  (clojure.string/join " " (filter identity names)))

(defn select [& {:keys [id label options value extra-attrs]
                 :or {extra-attrs {}}}]
  (let [selected-value value]
    [:div
     [:label {:for id :class "block text-sm font-medium text-gray-700"} label]
     [:select (merge {:name id :class "mt-1 block w-full rounded-md border-gray-300 py-2 pl-3 pr-10 text-base focus:border-indigo-500 focus:outline-none focus:ring-indigo-500 sm:text-sm"}
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

(defn textarea [& {:keys [label value hint id name rows]
                   :or {rows 3}}]
  [:div {:class "sm:col-span-6"}
   [:label {:for name :class "block text-sm font-medium text-gray-700"} label]
   [:div {:class "mt-1"}
    [:textarea {:id id :name name :rows rows :class "block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm"}
     (when value value)]]
   (when hint
     [:p {:class "mt-2 text-sm text-gray-500"}
      hint])])

(defn input [& {:keys [type label name placeholder value extra-attrs class pattern title]}]
  [:div {:class (cs class "flex-grow relative rounded-md border border-gray-300 px-3 py-2 shadow-sm focus-within:border-indigo-600 focus-within:ring-1 focus-within:ring-indigo-600")}
   [:label {:for name :class "absolute -top-2 left-2 -mt-px inline-block bg-white px-1 text-xs font-medium text-gray-900"}
    label]
   [:input (util/remove-nils (merge (or extra-attrs {})
                                    {:class "block w-full border-0 p-0 text-gray-900 placeholder-gray-500 focus:ring-0 sm:text-sm"
                                     :type type
                                     :pattern pattern
                                     :title title
                                     :name name
                                     :value value
                                     :required true
                                     :placeholder placeholder}))]])

(defn text [& opts]
  (apply input (conj opts "text" :type)))

(def button-priority-classes {:secondary
                              "border-transparent bg-indigo-100 px-4 py-2  text-indigo-700 hover:bg-indigo-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
                              :white
                              "border-gray-300 bg-white px-4 py-2 text-sm  text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 focus:ring-offset-gray-100"
                              :primary
                              "border-transparent bg-indigo-600 px-4 py-2  text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 focus:ring-offset-gray-100"
                              :white-rounded "rounded-full border border-gray-300 bg-white text-gray-700 shadow-sm hover:bg-gray-50"})

(def button-sizes-classes {:xsmall "px-2.5 py-1.5 text-xs"
                           :small "px-3 py-2 text-sm leading-4"
                           :normal "px-4 py-2 text-sm"
                           :large "px-4 py-2 text-base"
                           :xlarge "px-6 py-3 text-base"})

(def button-icon-sizes-classes {:xsmall "h-2 w-2"
                                :small "h-3 w-3"
                                :normal "h-5 w-5"
                                :large "h-5 w-5"
                                :xlarge "h-5 w-5"})

(defn button [& {:keys [tag label disabled? class attr icon priority centered? size]
                 :or   {class ""
                        priority :white
                        size  :normal
                        disabled? false
                        tag :button}
                 :as   args}]

  [tag (merge
        {:class
         (cs
          "inline-flex items-center border font-medium"
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
  [:div {:class "px-4 py-4 sm:flex sm:items-center sm:justify-between sm:px-6 lg:px-8"}
   [:div {:class "flex items-center space-x-5"}
    [:div
     [:h1 {:class "text-2xl font-bold text-gray-900"} title]
     (when subtitle
       [:p {:class "text-sm font-medium text-gray-500"}
        subtitle])]]
   [:div {:class "justify-stretch mt-6 flex flex-col-reverse space-y-4 space-y-reverse sm:flex-row-reverse sm:justify-end sm:space-y-0 sm:space-x-3 sm:space-x-reverse md:mt-0 md:flex-row md:space-x-3"}
    buttons]])

(defn member-select [& {:keys [id value label members :variant]
                        :or {label "Member"
                             variant :inline}}]
  (let [options
        (map (fn [m]
               {:value (:member/gigo-key m)
                :label (:member/name m)}) members)]
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

(defn toggle [& {:keys [label hx-target hx-get active? id]}]
  [:div {:class "flex items-center"}
   [:button {:type "button" :hx-target hx-target :hx-get hx-get
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
