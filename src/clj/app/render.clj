(ns app.render
  (:require
   [clojure.java.io :as io]
   [ctmx.render :as ctmx.render]
   [hiccup.page :as hiccup.page]
   [hiccup.util :as hiccup.util]
   [hiccup2.core :refer [html]]))

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

(defn head [title relative-prefix]
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]
   [:title (or title "Probematic")]
   ;; [:link {:rel "stylesheet" :href "https://rsms.me/inter/inter.css"}]
   [:link {:rel "stylesheet" :href (str relative-prefix "/font/inter/inter.css")}]
   [:link {:rel "stylesheet" :href (str relative-prefix  "/css/compiled/main.css")}]])

(defn body-end [relative-prefix]
  (list
   [:script {:src (str relative-prefix "/js/hyperscript.org@0.9.7.js")}]
   [:script {:src (str relative-prefix "/js/htmx.js")}]
   [:script {:src (str relative-prefix "/js/nprogress.js")}]
   [:script {:src (str relative-prefix "/js/popperjs@2-dev.js")}]
   [:script {:src (str relative-prefix "/js/tippy@6-dev.js")}]
   [:script {:src (str relative-prefix "/js/sortable@1.14.0.js")}]
   [:script {:src (str relative-prefix "/js/dropzone@6.0.0-beta.2.min.js")}]
   [:script {:src (str relative-prefix "/js/app.js") :type :module}]))

(defn html5-response
  ([body] (html5-response nil body))
  ([{:keys [js title]} body]
   (html-response
    (html5-safe
     (head title nil)
     [:body (ctmx.render/walk-attrs body)
      (body-end nil)]
     (when js [:script {:src (str "/js" js)}])))))

(defn html5-response-absolute
  ([{:keys [js title
            uri-prefix]} body]
   (html-response
    (html5-safe
     (head title uri-prefix)
     [:body (ctmx.render/walk-attrs body)
      (body-end uri-prefix)]
     (when js [:script {:src (str uri-prefix "/js" js)}])))))

(defn snippet-response [body]
  (ctmx.render/snippet-response body))

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
