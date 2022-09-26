(ns app.gigo
  (:require
   [app.util :refer [namespace-keys] :as u]
   ;[sno.gigo-sms.metrics :as metrics]
   [org.httpkit.sni-client :as sni-client]
   [org.httpkit.client :as client]
   [jsonista.core :as j]
   [clojure.string :as s]
   [medley.core :as m]
   [clojure.tools.logging :as log]
   [tick.core :as t]
   [tick.alpha.interval :as t.i]
   [clojure.string :as clojure.string]
   [integrant.repl.state :as state])
  (:import (java.time.format DateTimeParseException)
           (java.time DayOfWeek)))

(alter-var-root #'org.httpkit.client/*default-client* (fn [_] sni-client/default-client))

(def base-url "https://www.gig-o-matic.com/api/")

(defn auth
  "Authenticates to gigo with the given email and password. Returns the auth cookie."
  [email password]
  (let [resp @(client/post (str base-url "authenticate") {:form-params {:email    email
                                                                        :password password}})]

    (if (= 200 (:status resp))
      (get-in resp [:headers :set-cookie])
      nil)))

(defn add-auth
  "Sets the cookie header on the request.
  Cookie comes from the :cookie in config, if it exists.
  Or if :cookie-atom exists, it will be derefed. :cookie always takes precedence"
  [req config]
  (let [cookie (or (:cookie config)
                   (when (:cookie-atom config)
                     @(:cookie-atom config)))]
    (-> req
        (assoc-in [:headers "cookie"] cookie))))

(defn add-url [req]
  (update-in req [:url] (fn [url] (str base-url url))))

(defn parse-body
  "Parse json in http response body"
  [response]
  (try
    (if-let [json-body (some-> response :body (j/read-value j/keyword-keys-object-mapper))]
      (assoc response :body json-body)
      response)
    (catch Exception e
      response)))

(defn- request- [req config]
  (-> req
      (add-auth config)
      add-url
      client/request))

(defn update-cookie! [config new-cookie]
  (when (:cookie-atom config)
    (reset! (:cookie-atom config) new-cookie)))

(defn update-auth! [config]
  (when-let [new-cookie (auth (:username config) (:password config))]
    (update-cookie! config new-cookie)))

(defn check-auth! [config]
  (when (:cookie-atom config)
    (when-not @(:cookie-atom config)
      ;(tap> "preemptive auth update")
      (update-auth! config))))

(defn request! [req config]
  (check-auth! config)
  (let [resp @(request- req config)]
    (if (= 401 (:status resp))
      (do
        ;(tap> "inline auth update")
        (update-auth! config)
        @(request- req config))
      resp)))

(defn handle-401 []
  (throw (RuntimeException. "Authentication required.")))

(defn get-gig! [config id]
  (let [resp (parse-body (request! {:url (str "gig/" id)} config))]
    (case (:status resp)
      200 (:body resp)
      401 (handle-401)
      nil)))

(defn get-agenda! [config]
  (let [resp (parse-body (request! {:url "agenda"} config))]
    (case (:status resp)
      200 (:body resp)
      401 (handle-401)
      nil)))

(defn get-band-info! [config band-id]
  (let [resp (parse-body (request! {:url (str "band/" band-id)} config))]
    (case (:status resp)
      200 (:body resp)
      401 (handle-401)
      nil)))

(defn get-band-members! [config band-id]
  (let [resp (parse-body (request! {:url (str "band/members/" band-id)} config))]
    (case (:status resp)
      200 (:body resp)
      401 (handle-401)
      nil)))

(defn get-gig-attendance!
  "Given a gig id, fetches the gig details" [config id]
  (let [resp (parse-body (request! {:url (str "gig/plans/" id)} config))]
    (case (:status resp)
      200 (:body resp)
      401 (handle-401)
      nil)))

(def plan->attendance-kw {0 :attendance/no-response
                          1 :attendance/definitely          ; green
                          2 :attendance/probably            ; green circle
                          3 :attendance/dont-know           ; question mark
                          4 :attendance/probably-not        ; red square
                          5 :attendance/definitely-not      ; red
                          6 :attendance/not-interested      ; x
                          })

(def attendance-kw->plan (clojure.set/map-invert plan->attendance-kw))

(def attendance-set (set (vals plan->attendance-kw)))
(defn human-attendance [kw]
  (kw {:attendance/not-interested "not interested"
       :attendance/definitely     "coming"
       :attendance/probably       "probably coming"
       :attendance/probably-not   "probably not coming"
       :attendance/definitely-not "not coming"
       :attendance/dont-know      "question mark"}))

(defn summarize-attendance
  "Given an attendance response from get-gig-attendance!, returns a list of tuples [member name, attendance-kw]"
  [attendance]
  (map (fn [a] [(:the_member_name a) (-> a :the_plan :value plan->attendance-kw)]) attendance))

(defn members-from-attendance
  "Given an attendance response from get-gig-attendance!, returns a map of member_names -> member_key"
  [attendance]
  (reduce (fn [r a] (assoc r (:the_member_name a) (:the_member_key a))) {} attendance))

(defn sorted-gigs
  "Given an agenda response, returns a list of gigs sorted by date ascending."
  [agenda]
  (sort-by #(get-in % [:gig :date])
           (concat (:upcoming_plans agenda) (:weighin_plans agenda))))

(defn- time-parse [v]
  (if-not (empty? v)
    (try
      (t/parse-time (clojure.string/upper-case v) (t/formatter "h:mma"))
      (catch DateTimeParseException e
        (try
          (t/parse-time (clojure.string/upper-case v) (t/formatter "H:mm"))
          (catch DateTimeParseException e
            nil))))

    nil))

(defn date-time-parse [v]
  (if-not (empty? v)
    ; gigo sends back 00:00 for HH:mm, we only want the date
    (t/date (t/parse-date-time v (t/formatter "yyyy/MM/dd HH:mm")))
    nil))

(defn- enrich-gig [g]
  ;(tap> (dissoc g :attendance))
  (-> g
      (update :date date-time-parse)
      (update :enddate date-time-parse)
      (update :calltime time-parse)
      (update :settime time-parse)
      (update :endtime time-parse)
      (update :status {0 :status/unconfirmed 1 :status/confirmed 2 :status/cancelled})))

(defn get-all-gigs!
  "Returns a list of gigs sorted by date ascending."
  [config]
  (->> (-> config
           get-agenda!
           sorted-gigs)
       (mapv :gig)
       (mapv enrich-gig)))

(defn with-attendance!
  "Given a gig map, returns the map updated with attendance under the :attendance key"
  [config g]
  (assoc g :attendance
         (get-gig-attendance! config (:id g))))

(defn with-attendances!
  "Given a list of gigs, fetches the attendance for each gig added under the :attendance key"
  [config gigs]
  (mapv (partial with-attendance! config) gigs))

(defn get-all-gigs-with-attendance! [config]
  (->> (get-all-gigs! config)
       (with-attendances! config)))

(defn get-gig-with-attendance!
  [config id]
  (with-attendance! config (get-gig! config id)))

(defn get-next-gig!
  "Gets the next gig, with attendance info."
  [config]
  (let [g (first (get-all-gigs! config))]
    (assoc g :attendance (get-gig-attendance! config (:id g)))))

(defn group-by-attendance
  "Groups the members by their attendance response value.

  Given a gig, returns a map of attendance-kws -> members"
  [gig]
  (m/map-keys plan->attendance-kw
              (group-by #(get-in % [:the_plan :value]) (:attendance gig))))

(defn members-with-no-response
  "Given a gig, returns a list of member names and keys that have not replied with an attendance value."
  [gig]
  (mapv #(select-keys % [:the_member_name :the_member_key])
        (:attendance/no-response (group-by-attendance gig))))

(defn attendance-for-key
  "Given a list of attendances and a member key, returns the attendance map for the member"
  [attendance member-key]
  (m/find-first (fn [a] (= member-key (:the_member_key a))) attendance))

(defn attendance-for-name
  "Given a list of attendances and a member name, returns the attendance map for the member"
  [attendance member-name]
  (m/find-first (fn [a] (= member-name (:the_member_name a))) attendance))

(defn attendance-response
  "Given a gig and a key (:the_member_name or :the_member_name) and a corresponding value, returns their attendance response"
  [gig k v]
  (-> (m/find-first (fn [a] (= v (k a))) (:attendance gig))
      (get-in [:the_plan :value])
      plan->attendance-kw))

(defn attendance-response-for-by-name [gig name]
  (attendance-response gig :the_member_name name))

(defn attendance-response-for-by-key [gig key]
  (attendance-response gig :the_member_key key))

(defn first-gig-with-no-response-by-name
  "find the first gig that member has no response for. Assumes gigs is sorted by date ascending."
  [gigs member-name]
  (m/find-first (fn [g] (= :attendance/no-response (attendance-response-for-by-name g member-name)))
                gigs))

(defn first-gig-with-no-response-by-key
  "find the first gig that member has no response for. Assumes gigs is sorted by date ascending."
  [gigs member-key]
  (m/find-first (fn [g] (= :attendance/no-response (attendance-response-for-by-key g member-key)))
                gigs))

(defn need-reminding
  "Given a list of all gigs and a list of member names, returns a map from member name to gig id,
   where gig id is the next/first gig the member has no reply for."
  [gigs member-names]
  (reduce
   (fn [all m]
     (assoc all m (:id (first-gig-with-no-response-by-name gigs m))))
   {} member-names))

(defn set-plan-value!
  [config plan-id new-value]
  (let [resp (parse-body (request! {:method :put :url (str "plan/" plan-id "/value/" new-value)} config))]
    (case (:status resp)
      200 :ok
      401 (handle-401)
      (do (log/info "Failed to set gig attendance" resp)
          (throw (ex-info "Gigo API Error" {:resp resp}))))))

(defn set-gig-attendance!
  "Updates the attendance value for member for the given gig."
  [config member-key gig-id attendance-kw]
  (let [a (attendance-for-key (get-gig-attendance! config gig-id) member-key)
        ;_ (tap> (str "ATTENDANCE FOR KEY gig-id =" gig-id " memkey=" member-key))
        ;_ (tap> a)
        plan-id (get-in a [:the_plan :id])
        _ (assert plan-id (format "There must be a plan! gig=%s member=%s" gig-id member-key))
        new-value (attendance-kw->plan attendance-kw)]
    (log/info (format "setting attendance member=%s attendance=%s gig=%s" member-key attendance-kw gig-id))
    ;(metrics/inc-metric :metrics/attendance-changes)
    (set-plan-value! config plan-id new-value)))

(defn next-gig-needing-reply-from [gigs member-key]
  (first-gig-with-no-response-by-key gigs member-key))

(defn next-gig-needing-reply-from! [conf member-key]
  (let [gigs (get-all-gigs-with-attendance! conf)]
    (first-gig-with-no-response-by-key gigs member-key)))

(def attendances-coming #{:attendance/probably :attendance/definitely})
(def attendances-not-coming #{:attendance/definitely-not :attendance/probably-not})

(defn- first-gig-with-attendances-for
  "Returns the first gig in the sorted list where the member's response state is contained in the supplied set"
  [gigs member-key response-set]
  (m/find-first (fn [g] (contains? response-set (attendance-response-for-by-key g member-key)))
                gigs))

(defn next-gig-for [gigs member-key]
  (first-gig-with-attendances-for gigs member-key attendances-coming))

(defn next-gig-for! [conf member-key]
  (let [gigs (get-all-gigs-with-attendance! conf)]
    (first-gig-with-attendances-for gigs member-key attendances-coming)))

(defn to-date-time [g]
  (assert (some? g) "gig cannot be nil")
  (let [time (or (:calltime g)
                 (:settime g)
                 (t/midnight))]
    (->
     (:date g)
     (t/at time)
     (t/in "Europe/Vienna"))))

(defn seconds-between [pivot v]
  (t/seconds (t/between pivot v)))

(defn sort-nearest [gigs pivot]
  (->> gigs
       (map (fn [g]
              (assoc g :sort-key (Math/abs (seconds-between pivot (to-date-time g))))))
       (sort-by :sort-key)
       (map #(dissoc % :sort-key))))

(defn find-gig-closest-to
  "Given the list of gigs, and a date-time, will return the gig that is nearest to the date-time. Note that the nearest could be in the past."
  [gigs date-time]
  (first (sort-nearest gigs date-time)))

(defn wednesday-probe? [g]
  (and
   (s/includes?
    (s/lower-case (:title g)) "probe")
   (= DayOfWeek/WEDNESDAY (-> g :date (t/date) (t/day-of-week)))))

(defn non-wednesday-probe? [g]
  (and
   (s/includes?
    (s/lower-case (:title g)) "probe")
   (not= DayOfWeek/WEDNESDAY (-> g :date (t/date) (t/day-of-week)))))

(defn probe? [g]
  (or (wednesday-probe? g) (non-wednesday-probe? g)))

(defn gigs-in-range
  "start and end must be date-times"
  [gigs start end]
  (let [interval (t.i/new-interval start
                                   end)]
    (->> gigs
         (map (fn [g1]
                (assoc g1 :filter-key (t.i/relation interval (to-date-time g1)))))
         (filter #(= :contains (:filter-key %)))
         (map #(dissoc % :filter-key)))))

(defn gigs-in-week
  "Returns a list of gigs that are in the same calendar week as date-time, dt"
  [gigs dt]
  (gigs-in-range gigs (t/with dt :day-of-week 1) (t/with dt :day-of-week 7)))

(defn gigs-in-same-week
  "Returns a list of gigs that are in the same calendar week as gig, g"
  [gigs g]
  (let [dt (to-date-time g)]
    (->>
     (gigs-in-week gigs dt)
     (filter #(not= (:id g) (:id %))))))

(defn gig-by-id [gigs id]
  (m/find-first #(= id (:id %)) gigs))

(def gigs-cache (atom []))

(defn update-cache! [config]
  (let [gigs (get-all-gigs-with-attendance! config)]
    (when (not-empty gigs)
      (reset! gigs-cache gigs))))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require '[tick.core :as t])
    (def env (:app.ig/env state/system))
    (def _config (:app.ig/gigo-client state/system)))
  (find-gig-closest-to @gigs-cache (t/zoned-date-time "2022-03-12T12:00:00+01:00"))
  @gigs-cache
  (filter wednesday-probe? @gigs-cache)
  (mapv :status @gigs-cache)
  (update-cache! _config)
  (get-all-gigs! _config)

  (gigs-in-week @gigs-cache (t/zoned-date-time))
  (gigs-in-range @gigs-cache
                 (t/zoned-date-time "2022-02-13T00:00:00+01:00")
                 (t/zoned-date-time "2022-02-19T23:59:59+01:00"))
  (update-cache! _config)
  (auth (:username config) (:password config))
  @(:cookie-atom config)
  (def agenda (get-agenda! config))

  (def gig1 (get-next-gig! config))

  (def attendance (get-gig-attendance! config (:id gig1)))

  (def casey-key "ag1zfmdpZy1vLW1hdGljchMLEgZNZW1iZXIYgICA2NP7ggoM")
  (attendance-for (:attendance gig1) "Casey")
  (attendance-response (:attendance gig1) "Casey")
  (set-gig-attendance! config "Casey" (:id gig1) :attendance/definitely-not)
  (attendance-kw->plan :attendance/probably)

  (s/join "\n" (map first (summarize-attendance attendance)))

  (def just-gigs (get-all-gigs! config))

  (get-gig-attendance! config (first (mapv :gig/id all-gigs)))

  (def all-gigs (with-attendances! config just-gigs))
  (def id->gig (reduce (fn [all m] (assoc all (:id m) m)) {} all-gigs))
  (def member-names->member-keys (members-from-attendance (:attendance (first all-gigs))))

  all-gigs
  (def gig1 (first all-gigs))

  (:attendance/no-response (group-by-attendance gig1))

  (def gig-id->no-responses
    (reduce (fn [all g] (assoc all (:id g) (members-with-no-response g))) {} all-gigs))

  (attendance-value gig1 "Casey")

  ; find the first gig that member has no response for
  (m/find-first (fn [g] (= :attendance/no-response (attendance-response g "Felix"))) all-gigs)

  ; name->gig id
  (need-reminding all-gigs (keys member-names->member-keys))

  (def gigs (get-all-gigs-with-attendance! config))
  (first gigs)

  (find-gig-closest-to gigs (t/zoned-date-time))

  (require 'sc.api)
  (next-gig-for! config casey-key)
  (next-gig-needing-reply-from! config casey-key)
  (first-gig-with-no-response-by-key gigs casey-key)
  (attendance-response-for-by-key (first gigs) casey-key)

  ;
  )
