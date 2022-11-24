(ns app.file-browser.routes
  (:require
   [app.layout :as layout]
   [app.file-browser.views :as view]
   [ctmx.core :as ctmx]))

(defn choose-file-page []
  (ctmx/make-routes
   "/choose-file"
   (fn [req]
     (layout/app-shell req
                       (view/choose-file-page req)))))

(defn file-browser-routes []
  ["" {:app.route/name :app/songs}
   (choose-file-page)])
