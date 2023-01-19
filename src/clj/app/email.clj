(ns app.email
  (:require
   [selmer.parser :as selmer]
   [app.i18n :as i18n]
   [app.email.domain :as domain]
   [app.email.email-worker :as email-worker]
   [app.datomic :as d]
   [hiccup2.core :refer [html]]
   [com.yetanalytics.squuid :as sq]
   [tick.core :as t]
   [clojure.string :as str]
   [app.util :as util]
   [app.ui :as ui]))

(defn queue-email! [sys email]
  (email-worker/enqueue-mail! sys email))

(defn build-email [to subject body-html body-plain]
  (util/remove-nils
   {:email/bulk? false
    :email/email-id (sq/generate-squuid)
    :email/tos [to]
    :email/subject subject
    :email/body-plain body-plain
    :email/body-html body-html
    :email/created-at (t/inst)}))

(defn build-bulk-emails [tos recipient-variables subject body-html body-plain]
  (util/remove-nils
   {:email/bulk? true
    :email/recipient-variables recipient-variables
    :email/email-id (sq/generate-squuid)
    :email/tos tos
    :email/subject subject
    :email/body-plain body-plain
    :email/body-html body-html
    :email/created-at (t/inst)}))

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
   "# {{title}}

{% for item in gig-details %}* {{item.name}}: {{item.value}}
{% endfor %}
{% if not more-details|empty? %}More details:
{{more-details}}{% endif %}"
   {:title title
    :more-details more-details
    :gig-details (util/remove-nils [;
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
                                    {:name (tr [:gig/status]) :value (tr [status])}
                                    {:name (tr [:gig/gig-type]) :value (tr [gig-type])}
                                    (when pay-deal
                                      {:name (tr [:gig/pay-deal]) :value pay-deal})
                                    ;;
                                    ])}))
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

(defn template-gig-created-email-plain [tr gig]
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
    :gig-info (template-snippet-gig-details-plain tr gig)
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

(comment

  (do
    (def gig {:gig/pay-deal "Unterkunft, Verpflegung kostet nix, wir bekommen auch einen kräftigen Fahrtkostenzuschuss, und weitgehend Essen&trinken. die bandkassa nehmen wir mit für noch mehr Essen&Trinken."
              :gig/status :gig.status/confirmed
              :gig/title "Skappa’nabanda!"
              :gig/gig-id "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgIDg4ZGXCAw"
              :gig/gig-type :gig.type/gig
              :gig/setlist "Programm für Donnerstag im Detail:\r"
              :gig/more-details "FestivalOrt:\r"
              :gig/date #inst "2015-06-03T22:00:00.000-00:00"
              :gig/location "GRAZ"})
    (def gig2 {:gig/status :gig.status/confirmed
               :gig/call-time (t/time "18:47")
               :gig/title "Probe"
               :gig/gig-id "0185a673-9f2d-8b0e-8f1a-e70db25c9add"
               :gig/contact {:member/name "SNOrchestra"
                             :member/gigo-key "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA6K70hwoM"
                             :member/nick "SNO"}
               :gig/gig-type :gig.type/probe
               :gig/set-time (t/time "19:00")
               :gig/date  (t/date "2023-01-18")
               :gig/location "Proberaum in den Bögen"})

    (def tr (i18n/tr-with (i18n/read-langs) ["en"]))) ;; rcf

  (spit "plain-email.txt"
        (str
         "\n++++\n"
         (template-gig-created-email-plain tr gig)
         "\n++++\n"
         (template-gig-created-email-plain tr gig2))) ;; rcf

;;
  )
