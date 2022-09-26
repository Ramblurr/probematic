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

(defn cs [& names]
  (clojure.string/join " " (filter identity names)))
