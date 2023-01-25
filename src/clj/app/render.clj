(ns app.render
  (:require
   [ctmx.render :as ctmx.render]
   [hiccup.page :as hiccup.page]
   [hiccup.util :as hiccup.util]
   [hiccup2.core :refer [html]]
   [clojure.java.io :as io]))

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

(defn html-response [body]
  {:status 200
   :headers {"Content-Type" "text/html"}
   ;:body (pretty-print-html body)
   :body  body})

(defn html-status-response
  ([status-code body]
   (html-status-response status-code {} body))
  ([status-code extra-headers body]
   {:status status-code
    :headers (merge  {"Content-Type" "text/html"} extra-headers)
                                        ;:body (pretty-print-html body)
    :body  body}))

(defn head [title]
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]
   [:title (or title "Probematic")]
   ;; [:link {:rel "stylesheet" :href "https://rsms.me/inter/inter.css"}]
   [:link {:rel "stylesheet" :href "/font/inter/inter.css"}]
   [:link {:rel "stylesheet" :href "/css/compiled/main.css"}]])

(defn body-end []
  (list
   [:script {:src "/js/hyperscript.org@0.9.7.js"
             :integrity "sha384-6GYN8BDHOJkkru6zcpGOUa//1mn+5iZ/MyT6mq34WFIpuOeLF52kSi721q0SsYF9"}]
   [:script {:src "/js/htmx.js"
             :integrity "sha384-mrsv860ohrJ5KkqRxwXXj6OIT6sONUxOd+1kvbqW351hQd7JlfFnM0tLetA76GU0"}]
   [:script {:src "/js/nprogress.js"}]
   [:script {:src "/js/popperjs@2-dev.js"}]
   [:script {:src "/js/tippy@6-dev.js"}]
   [:script {:src "/js/sortable@1.14.0.js"}]
   [:script {:src "/js/app.js" :type :module}]))

(defn html5-response
  ([body] (html5-response nil body))
  ([{:keys [js title]} body]
   (html-response
    (html5-safe
     (head title)
     [:body (ctmx.render/walk-attrs body)
      (body-end)]
     (when js [:script {:src (str "/js" js)}])))))

(defn post-login-client-side-redirect
  "When using a SameSite=strict session cookie, after the OAUTH2 login the session cookie will not be sent. We need to interrupt the server-side redirect
  with this client side redirect to trigger the SameSite=strict allow policy so the session cookie will be sent."
  [session cookies relative-uri]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :session session
   :cookies cookies
   :body  (html5-safe
           [:head
            [:title "Probematic"]
            [:style
             (hiccup.util/raw-string (-> (io/resource "public/css/login-interstitial.css") slurp))]
            [:meta {:http-equiv "refresh" :content (str "0;URL='" relative-uri "'")}]]
           [:body
            [:div.container
             [:div.content
              [:noscript
               [:p [:a {:href relative-uri} "Continue"]]]
              [:div.spinner
               [:div]
               [:div]
               [:div]]
              [:p "Logging in..."]]]])})
