(ns app.email.templates
  (:require
   [app.config :as config]
   [app.secret-box :as secret-box]
   [app.ui :as ui]
   [app.urls :as url]
   [app.util :as util]
   [clojure.string :as str]
   [hiccup2.core :refer [html]]
   [selmer.parser :as selmer]
   [selmer.util :as selmer.util]))

(def plans-template-key {:plan/no-response "noresponse"
                         :plan/definitely "definitely"
                         :plan/probably "probably"
                         :plan/unknown "unknown"
                         :plan/probably-not "probablynot"
                         :plan/definitely-not "definitelynot"
                         :plan/not-interested "notinterested"
                         :reminder "reminder"})

(defn template-key-gig-attendance [attendance-plan]
  (str "plan_" (get plans-template-key attendance-plan)))

(defn template-link-gig-attendance [env attendance-plan]
  (str (url/absolute-gig-answer-link-base env) "/?answer=%recipient." (template-key-gig-attendance attendance-plan) "%"))

(defn payload-for-attendance [env gig-id gigo-key attendance-plan]
  (secret-box/encrypt
   {:member/gigo-key gigo-key
    :gig/gig-id gig-id
    :attendance/plan attendance-plan}
   (config/app-secret-key env)))

(defn payload-for-reminder [env gig-id gigo-key]
  (secret-box/encrypt
   {:member/gigo-key gigo-key
    :gig/gig-id gig-id
    :reminder true}
   (config/app-secret-key env)))

(defn template-values-gig-attendance [env gig-id members]
  (reduce (fn [recipient-vars {:member/keys [gigo-key email]}]
            (-> recipient-vars
                (assoc-in  [email (template-key-gig-attendance :plan/definitely)]
                           (payload-for-attendance env gig-id gigo-key :plan/definitely))
                (assoc-in  [email (template-key-gig-attendance :plan/definitely-not)]
                           (payload-for-attendance env gig-id gigo-key :plan/definitely-not))
                (assoc-in [email (template-key-gig-attendance :reminder)]
                          (payload-for-reminder env gig-id gigo-key))))

          {} members))

(defn template-snippet-gig-details [{:keys [tr env]} {:gig/keys [date end-date title set-time call-time end-time status location more-details pay-deal gig-type]}]
  [:div
   [:p [:strong title]]
   [:ul
    [:li (tr [:gig/gig-type]) ": " (tr [gig-type])]
    [:li (tr [:gig/status]) ": " (tr [status])]
    [:li (tr [:gig/date]) ": " (ui/format-dt date)]
    (when end-date
      [:li (tr [:gig/end-date]) ": " (ui/format-dt end-date)])
    (when call-time
      [:li (tr [:gig/call-time]) ": " (ui/format-time call-time)])
    (when set-time
      [:li (tr [:gig/set-time]) ": " (ui/format-time set-time)])
    (when end-time
      [:li (tr [:gig/end-time]) ": " (ui/format-time end-time)])
    (when location
      [:li (tr [:gig/location]) ": " location])
    (when pay-deal
      [:li (tr [:gig/pay-deal]) ": " pay-deal])]

   (when more-details
     [:p (tr [:gig/more-details]) ": " [:br] more-details])])

(defn template-snippet-gig-details-plain [{:keys [tr env]} {:gig/keys [date end-date title set-time call-time end-time status location more-details pay-deal gig-type]}]
  (selmer/render
   "# {{title}}

{% for item in gig-details %}* {{item.name}}: {{item.value}}
{% endfor %}
{% if not more-details|empty? %}{{more-details-label}}:
{{more-details}}{% endif %}"
   {:title title
    :more-details more-details
    :more-details-label (tr [:gig/more-details])
    :gig-details (util/remove-nils [;
                                    {:name (tr [:gig/gig-type]) :value (tr [gig-type])}
                                    {:name (tr [:gig/status]) :value (tr [status])}
                                    {:name (tr [:gig/date]) :value  (ui/format-dt date)}
                                    (when end-date
                                      {:name (tr [:gig/end-date]) :value (ui/format-dt end-date)})
                                    (when call-time
                                      {:name (tr [:gig/call-time]) :value (ui/format-time call-time)})
                                    (when set-time
                                      {:name (tr [:gig/set-time]) :value (ui/format-time set-time)})
                                    (when end-time
                                      {:name (tr [:gig/end-time]) :value (ui/format-time end-time)})
                                    (when location
                                      {:name (tr [:gig/location]) :value location})
                                    (when pay-deal
                                      {:name (tr [:gig/pay-deal]) :value pay-deal})
                                    ;;
                                    ])}))
(defn gig-created-email-html [{:keys [tr env] :as sys} gig]
  (let [can-make-it-link (template-link-gig-attendance env :plan/definitely)
        cannot-make-it-link (template-link-gig-attendance env :plan/definitely-not)
        reminder-link (template-link-gig-attendance env :reminder)
        gig-link (url/absolute-link-gig env (:gig/gig-id gig))]
    (str (html
          [:div
           [:p
            (tr [:email/greeting])]
           [:p (tr [(case (:gig/gig-type gig)
                      :gig.type/probe :email/new-probe-added
                      :gig.type/extra-probe :email/new-extra-probe-added
                      :gig.type/meeting :email/new-meeting-added
                      :gig.type/gig :email/new-gig-added)])]
           [:p]
           (template-snippet-gig-details sys gig)
           [:p]
           [:hr]
           [:p [:strong (tr [:email/can-you-make-it?])]]
           [:p [:a {:href can-make-it-link} (tr [:email/can-make-it])]]
           [:p [:a {:href cannot-make-it-link} (tr [:email/cannot-make-it])]]
           [:p [:a {:href reminder-link} (tr [:email/want-reminder])]]
           [:p [:a {:href gig-link} (tr [:email/gig-info-page])]]
           [:p]
           [:p (tr [:email/sign-off])]]))))

(defn gig-created-email-plain [{:keys [tr env] :as sys} gig]
  (selmer.util/without-escaping
   (let [can-make-it-link (template-link-gig-attendance env :plan/definitely)
         cannot-make-it-link (template-link-gig-attendance env :plan/definitely-not)
         reminder-link (template-link-gig-attendance env :reminder)
         gig-link (url/absolute-link-gig env (:gig/gig-id gig))]
     (selmer/render
      "{{greeting}}

{{intro}}

{{gig-info}}
-----
{{can-you-make-it}}

{{can-make-it}}: {{can-make-it-link}}

{{cannot-make-it}}: {{cannot-make-it-link}}

{{want-reminder}}: {{want-reminder-link}}

{{gig-info-page}}:  {{gig-info-page-link}}

{{sign-off}}
"
      {:greeting (tr [:email/greeting])
       :intro (tr [(case (:gig/gig-type gig)
                     :gig.type/probe :email/new-probe-added
                     :gig.type/extra-probe :email/new-extra-probe-added
                     :gig.type/meeting :email/new-meeting-added
                     :gig.type/gig :email/new-gig-added)])
       :gig-info (template-snippet-gig-details-plain sys gig)
       :can-you-make-it (tr [:email/can-you-make-it?])
       :can-make-it (tr [:email/can-make-it])
       :can-make-it-link can-make-it-link

       :cannot-make-it (tr [:email/cannot-make-it])
       :cannot-make-it-link cannot-make-it-link

       :want-reminder (tr [:email/want-reminder])
       :want-reminder-link reminder-link

       :gig-info-page (tr [:email/gig-info-page])
       :gig-info-page-link gig-link
       :sign-off (tr [:email/sign-off])

        ;;
       }
      (template-snippet-gig-details sys gig)))))

(defn gig-updated-email-html
  "edited-attrs should be a list of gig entity attribute names that were edited"
  [{:keys [tr env] :as sys} gig edited-attrs]
  (str (html
        [:div
         [:p
          (tr [:email/greeting])]
         [:p (tr [:email/gig-edited])]
         [:p]
         [:p (tr [:email/gig-edit-type]) ": " (str/join ", " (map #(tr [%]) edited-attrs))]
         [:p]
         (template-snippet-gig-details sys gig)
         [:p]
         [:hr]
         [:p [:a {:href (url/absolute-link-gig  env (:gig/gig-id gig))} (tr [:email/need-to-change-availability?])]]
         [:p]
         [:p (tr [:email/sign-off])]])))

(defn gig-updated-email-plain [{:keys [tr env] :as sys} gig edited-attrs]
  (selmer.util/without-escaping
   (let [gig-link (url/absolute-link-gig env (:gig/gig-id gig))]
     (selmer/render
      "{{greeting}}

{{intro}}

{{gig-edit-type-label}}:
{{gig-edit-type-attrs}}

-----
{{gig-info}}
-----
{{need-to-change}}
{{gig-info-page}}:  {{gig-info-page-link}}

{{sign-off}}
"
      {:greeting (tr [:email/greeting])
       :intro (tr [:email/gig-edited])
       :gig-edit-type-label (tr [:email/gig-edit-type])
       :gig-edit-type-attrs (str/join ", " (map #(tr [%]) edited-attrs))
       :gig-info (template-snippet-gig-details-plain sys gig)
       :need-to-change (tr [:email/need-to-change-availability?])
       :gig-info-page (tr [:email/gig-info-page])
       :gig-info-page-link gig-link
       :sign-off (tr [:email/sign-off])

       ;;
       }
      (template-snippet-gig-details sys gig)))))

(defn gig-created-recipient-variables [{:keys [env]} gig members]
  (template-values-gig-attendance env (:gig/gig-id gig) members))

(defn gig-updated-recipient-variables [{:keys [env]} gig members]
  (template-values-gig-attendance env (:gig/gig-id gig) members))
