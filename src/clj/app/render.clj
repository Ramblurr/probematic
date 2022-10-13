(ns app.render
  (:import (org.w3c.tidy Tidy))
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream))
  (:require
   [clojure.string :as clojure.string]
   [hiccup.core :as hiccup]
   [hiccup.page :refer [html5]]))

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
    (html5
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport"
              :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]

      [:link {:rel "stylesheet"
              :href "https://rsms.me/inter/inter.css"}]
      [:link {:rel "stylesheet"
              :href "/css/compiled/main.css"}]]
     [:body body]

     [:script {:src "/js/hyperscript.org@0.9.7.js"}]
     [:script {:src "/js/htmx.js"}]
     [:script {:src "/js/helpers.js"}]
     (when js [:script {:src (str "/js" js)}])))))

(defn partial-response [body]
  (html-response
   (hiccup/html body)))

(defn cs [& names]
  (clojure.string/join " " (filter identity names)))

(defn filter-vals
  ([m]
   (into {}
         (for [[k v] m :when v] [k v]))))

(defn input
  ([type title]
   (input type title nil nil nil))
  ([type title name]
   (input type title name nil nil))
  ([type title name placeholder]
   (input type title name placeholder nil))
  ([type title name placeholder value]
   [:div {:class "flex-grow relative rounded-md border border-gray-300 px-3 py-2 shadow-sm focus-within:border-indigo-600 focus-within:ring-1 focus-within:ring-indigo-600"}
    [:label {:for "song", :class "absolute -top-2 left-2 -mt-px inline-block bg-white px-1 text-xs font-medium text-gray-900"}
     title]
    [:input (filter-vals {:class "block w-full border-0 p-0 text-gray-900 placeholder-gray-500 focus:ring-0 sm:text-sm"
                          :type type
                          :name name
                          :value value
                          :required true
                          :placeholder placeholder})]]))

(def text (partial input "text"))

(def button-priority-classes {:secondary
                              "border-transparent bg-indigo-100 px-4 py-2 text-sm font-medium text-indigo-700 hover:bg-indigo-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
                              :white
                              "border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 focus:ring-offset-gray-100"
                              :primary
                              "border-transparent bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 focus:ring-offset-gray-100"})

(defn button [& {:keys [tag label disabled? class attr icon priority centered?]
                 :or   {class ""
                        priority :white
                        tag :a}
                 :as   args}]
  [tag (merge
        {:class
         (cs
          "inline-flex items-center rounded-md border"
          (priority button-priority-classes)
          (when centered? "items-center justify-center")
          class)}
        attr)
   (when icon (icon  {:class "-ml-1 mr-2 h-5 w-5"}))
   label])

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
