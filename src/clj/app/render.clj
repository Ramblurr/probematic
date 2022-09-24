(ns app.render
  (:require
   [hiccup.core :as hiccup]
   [hiccup.page :refer [html5]]))

(defn html-response [body]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body body})

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

     [:script {:src "/js/htmx.js"}]
     [:script {:src "/js/helpers.js"}]
     (when js [:script {:src (str "/js" js)}])))))
