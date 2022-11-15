(ns app.routes.login
  (:require
   [clojure.tools.logging :as log]
   [app.render :as render]
   [ctmx.response :as response]
   [ctmx.rt :as rt]
   [ctmx.core :as ctmx]))

(defn add-user [username]
  (log/info "new user" username)
  true)

(defn pw-match? [v]
  true)

(defn notice-routes []
  (ctmx/make-routes
   "/notice"
   (fn [req]
     (ui/html5-response
      [:div.container
       [:div.row.my-2
        [:div.col
         "Bids are legally binding.  Please bid in good faith"]]
       [:a.btn.btn-primary {:href "/bid"} "I agree"]]))))

(comment
  (macroexpand
   '(ctmx/defcomponent ^:endpoint username-prompt [req username]
      (ctmx/with-req req
        (if post?                       ;(and post? (add-user username))
          (do
            (log/info "POSTED" post? username)
            (assoc
             (response/hx-redirect "/notice")
             :session {:username username}))
          (do
            (log/info "ELLO" post? username id)
            [:form {:id id :hx-post "username-prompt"}
             [:h3 "Please choose a username"]
             [:input.my-2 {:type "text" :name "username" :placeholder "Username"}] [:br]
             [:input {:type "submit"}] [:br]
             (when post? [:span.badge.badge-warning "Username taken.  Please choose another"])]))))))

(ctmx/defcomponent ^:endpoint click-div-post [req ^:long numclicks]
  [:form {:id id
          :hx-post "click-div-post"
          :hx-trigger "click"}
   [:input {:type "hidden" :name "numclicks" :value (inc numclicks)}]
   "You have post clicked me " numclicks " times!"])

(ctmx/defcomponent ^:endpoint click-div [req ^:long num-clicks]
  [:form {:id id
          :hx-get "click-div"
          :hx-trigger "click"}
   [:input {:type "hidden" :name "num-clicks" :value (inc num-clicks)}]
   "You have clicked me " num-clicks " times!"])

(defn login-routes []
  (ctmx/make-routes
   "/login"
   (fn [req]
     (ui/html5-response

      [:div
       (click-div req 0)
       (click-div-post req 0)]))))
