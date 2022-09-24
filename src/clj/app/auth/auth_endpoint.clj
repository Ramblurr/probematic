(ns app.auth.auth-endpoint
  (:require
   [ring.util.http-response :as http-response]
   [medley.core :as m]
   [clojure.java.io :as io]))

(defn authenticate [system username password]
  (when-let [foo 1]
    (m/assoc-some {} :customer-id 1)))

(defn login-handler! [system req]
  (if-let [{:keys [username password]} (:body-params req)]
    (if-let [profile (authenticate system username password)]
      (-> (http-response/ok profile)
          (assoc-in  [:session :app.auth/identity] profile))
      (http-response/unauthorized))
    (http-response/bad-request)))

(defn login-form-handler! [system req]
  (if-let [{:strs [username password]} (:form-params req)]
    (if-let [profile (authenticate system username password)]
      (-> (http-response/found "/graphiql")
          (assoc-in  [:session :app.auth/identity] profile))
      (http-response/found "/login-backend"))
    (http-response/bad-request)))

(defn logout-handler! [_ _]
  (->
   (http-response/found "/login")
    ; logout is performed in ring by setting the session to nil
   (assoc :session nil)))

(defn logged-in-handler [req]
  (http-response/ok {:loggedIn (some?
                                (get-in req [:session :app.auth/identity :app.auth/roles]))}))

(defn login-page [_ _]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (slurp (io/resource "login.html"))})
