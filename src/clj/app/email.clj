(ns app.email
  (:require
   [selmer.parser :as selmer]
   [app.datomic :as d]
   [hiccup2.core :refer [html]]
   [com.yetanalytics.squuid :as sq]
   [tick.core :as t]
   [clojure.string :as str]
   [app.util :as util]))

(def sent-statuses #{:email-status/not-sent
                     :email-status/sent
                     :email-status/send-error})

(def email-templates #{:email-template/gig-created
                       :email-template/gig-updated})

(defn email-html [data])

(defn send-email! [{:keys [mailgun]} to subject plain html])

(defn queue-email! [{:keys [conn]} to subject plain html])

(defn build-email [to member subject body-html body-plain]
  (util/remove-nils
   {:email/to to
    :email/subject subject
    :email/body-plain body-plain
    :email/body-html body-html
    :email/member-recipient (when member (d/ref member))
    :email/sent-status :email-status/not-sent
    :email/created-at (t/inst)
    :email/email-id (sq/generate-squuid)}))

(defn template-snippet-gig-details [tr {:gig/keys [date end-date title set-time call-time end-time status location more-details pay-deal gig-type]}]
  [:div
   [:p [:strong title]]
   [:ul
    [:li (tr [:gig/date]) ": " date]
    (when end-date
      [:li (tr [:gig/end-date]) ": " end-date])
    (when call-time
      [:li (tr [:gig/call-time]) ": " call-time])
    (when set-time
      [:li (tr [:gig/set-time]) ": " set-time])
    (when end-time
      [:li (tr [:gig/end-time]) ": " end-time])
    (when location
      [:li (tr [:gig/location]) location])
    [:li (tr [:gig/status]) (tr [status])]
    [:li (tr [:gig/gig-type]) (tr [gig-type])]
    (when pay-deal
      [:li (tr [:gig/pay-deal]) pay-deal])]

   (when more-details
     [:p more-details])])

(defn template-snippet-gig-details-plain [tr {:gig/keys [date end-date title set-time call-time end-time status location more-details pay-deal gig-type]}]
  (selmer/render
   "{{title}}

{% for item in some.values %}
{% endfor %}
* {{date-label}} {{date}}

"
   [:div
    [:p [:strong title]]
    [:ul
     [:li (tr [:gig/date]) ": " date]
     (when end-date
       [:li (tr [:gig/end-date]) ": " end-date])
     (when call-time
       [:li (tr [:gig/call-time]) ": " call-time])
     (when set-time
       [:li (tr [:gig/set-time]) ": " set-time])
     (when end-time
       [:li (tr [:gig/end-time]) ": " end-time])
     (when location
       [:li (tr [:gig/location]) location])
     [:li (tr [:gig/status]) (tr [status])]
     [:li (tr [:gig/gig-type]) (tr [gig-type])]
     (when pay-deal
       [:li (tr [:gig/pay-deal]) pay-deal])]

    (when more-details
      [:p more-details])]))

(defn template-gig-created-email [tr member gig]
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
         (template-snippet-gig-details tr gig)
         [:p]
         [:hr]
         [:p [:strong (tr [:email/can-you-make-it?])]]
         [:p [:a {:href ""} (tr [:email/can-make-it])]]
         [:p [:a {:href ""} (tr [:email/cannot-make-it])]]
         [:p [:a {:href ""} (tr [:email/want-reminder])]]
         [:p [:a {:href ""} (tr [:email/gig-info-page])]]
         [:p]
         [:p (tr [:email/sign-off])]])))

(defn template-gig-created-email-plain [tr member gig]
  (selmer/render
   "{{greeting}},
{{intro}}

-----
{{can-you-make-it}}

{{can-make-it}} - {{can-make-it-link}}
{{cannot-make-it}} - {{cannot-make-it-link}}
{{want-reminder}} - {{want-reminder-link}}

{{gig-info-page}}

{{sign-off}}
"
   {:greeting (tr [:email/greeting])
    :intro (tr [(case (:gig/gig-type gig)
                  :gig.type/probe :email/new-probe-added
                  :gig.type/extra-probe :email/new-extra-probe-added
                  :gig.type/meeting :email/new-meeting-added
                  :gig.type/gig :email/new-gig-added)])
    :can-you-make-it (tr [:email/can-you-make-it?])
    :can-make-it (tr [:email/can-make-it])
    :can-make-it-link ""

    :cannot-make-it (tr [:email/cannot-make-it])
    :cannot-make-it-link ""

    :want-reminder (tr [:email/want-reminder])
    :want-reminder-link ""

    :gig-info-page (tr [:email/gig-info-page])
    :sign-off (tr [:email/sign-off])

    ;;
    }
   (template-snippet-gig-details tr gig)))

(defn template-gig-edited-email
  "edited-attrs should be a list of gig entity attribute names that were edited"
  [tr member gig edited-attrs]
  [:div
   [:p
    (tr [:email/greeting])]
   [:p (tr [:email/gig-edited])]
   [:p]
   [:p (tr [:email/gig-edit-type]) (str/join ", " (map #(tr [%]) edited-attrs))]
   [:p]
   (template-snippet-gig-details tr gig)
   [:p]
   [:hr]
   [:p [:strong (tr [:email/need-to-change-availability?])]]
   [:p [:a {:href ""} (tr [:email/gig-info-page])]]
   [:p]
   [:p (tr [:email/sign-off])]])

(defn build-gig-created-email [{:keys [tr]} member gig]
  (build-email
   (:member/email member)
   (tr [:email-subject/gig-created] [(:gig/title gig)])
   (template-gig-created-email tr member gig)))

(defn build-gig-updated-email [{:keys [tr]} member gig edited-attrs]
  (build-email
   (:member/email member)
   (tr [:email-subject/gig-updated] [(:gig/title gig)])
   (template-gig-edited-email tr member gig edited-attrs)))
