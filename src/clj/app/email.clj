(ns app.email
  (:require
   [app.secret-box :as secret-box]

   [app.config :as config]
   [selmer.parser :as selmer]
   [selmer.util :as selmer.util]
   [app.i18n :as i18n]
   [app.email.domain :as domain]
   [app.email.email-worker :as email-worker]
   [hiccup2.core :refer [html]]
   [com.yetanalytics.squuid :as sq]
   [tick.core :as t]
   [clojure.string :as str]
   [app.util :as util]
   [app.ui :as ui]
   [app.urls :as url]
   [app.debug :as debug]))

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

(defn build-bulk-emails [tos subject body-html body-plain recipient-variables]
  (util/remove-nils
   {:email/bulk? true
    :email/recipient-variables recipient-variables
    :email/email-id (sq/generate-squuid)
    :email/tos tos
    :email/subject subject
    :email/body-plain body-plain
    :email/body-html body-html
    :email/created-at (t/inst)}))

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

(defn template-snippet-gig-details-plain [{:keys [tr env]} {:gig/keys [date end-date title set-time call-time end-time status location more-details pay-deal gig-type]}]
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
(defn template-gig-created-email [{:keys [tr env] :as sys} gig]
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

(defn template-gig-created-email-plain [{:keys [tr env] :as sys} gig]
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

(defn template-gig-edited-email
  "edited-attrs should be a list of gig entity attribute names that were edited"
  [{:keys [tr env]} member gig edited-attrs]
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

(defn template-gig-created-recipient-variables [{:keys [env]} gig members]
  (template-values-gig-attendance env (:gig/gig-id gig) members))

(defn build-gig-created-email [{:keys [tr] :as sys} gig members]
  (build-bulk-emails
   (mapv :member/email members)
   (tr [:email-subject/gig-created] [(:gig/title gig)])
   (template-gig-created-email sys gig)
   (template-gig-created-email-plain sys gig)
   (template-gig-created-recipient-variables sys gig members)))

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

    (def member {:member/email "alice@example.com"
                 :member/gigo-key "abcd123u"})

    (def member2 {:member/email "bob@example.com"
                  :member/gigo-key "zxcysdf"})

    (def tr (i18n/tr-with (i18n/read-langs) ["en"]))
    (def sys {:tr tr :env {:app-secret-key "hunter2" :app-base-url "https://foobar.com"}})) ;; rcf

  (spit "plain-email.txt"
        (str
         "\n++++\n"
         (template-gig-created-email-plain sys gig)
         "\n++++\n"
         (template-gig-created-email-plain sys gig2))) ;; rcf

  (build-gig-created-email sys gig [member member2])

  ;;
  )
