(ns app.response
  (:require
   [ctmx.response :as response]))

(defn redirect
  "Redirect to location. Will use HX-Redirect if request is from htmx, otherwise will use standard redirect."
  [req location]
  (if (:htmx? req)
    (response/hx-redirect location)
    (response/redirect location)))
