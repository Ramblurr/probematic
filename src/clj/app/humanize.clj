(ns app.humanize
  (:import [java.time.temporal Temporal ChronoUnit])
  (:require [tick.core :as t]
            [taoensso.tempura :as tempura]))

(def humanize-messages
  {:en
   {:missing ":en missing text"
    :now "now"
    :time
    {:second.ago "a second ago"
     :seconds.ago "%1 seconds ago"
     :minute.ago "a minute ago"
     :minutes.ago "%1 minutes ago"
     :hour.ago "a hour ago"
     :hours.ago "%1 hours ago"
     :day.ago "a day ago"
     :days.ago "%1 days ago"
     :week.ago "a week ago"
     :weeks.ago "%1 weeks ago"
     :month.ago "a month ago"
     :months.ago "%1 months ago"
     :year.ago "a year ago"
     :years.ago "%1 years ago"
     :second.within "in a second"
     :seconds.within "within %1 seconds"
     :minute.within "in a minute"
     :minutes.within "within %1 minutes"
     :hour.within "in an hour"
     :hours.within "within %1 hours"
     :day.within "in a day"
     :days.within "within %1 days"
     :week.within "in a week"
     :weeks.within "within %1 weeks"
     :month.within "in a month"
     :months.within "within %1 months"
     :year.within "in a year"
     :years.within "within %1 years"}}})

(def tr (partial tempura/tr {:dict humanize-messages} [:en]))

(defn- plural-msg [n singular plural]
  (if (> n 0)
    (tr [plural] [n])
    (tr [singular] [n])))

(defn from
  "Given a local date time or a local date, return a human-friendly representation of the amount of time difference relative
  to the current time or :now-t."
  [^Temporal then-t & {:keys [now-t]
                       :or {now-t (t/date-time)}}]
  (let [then-t (if (t/date? then-t)
                 (t/at then-t (t/midnight))
                 then-t)
        years (.between ChronoUnit/YEARS then-t now-t)
        months (.between ChronoUnit/MONTHS then-t now-t)
        weeks (.between ChronoUnit/WEEKS then-t now-t)
        days (.between ChronoUnit/DAYS then-t now-t)
        hours (.between ChronoUnit/HOURS then-t now-t)
        minutes (.between ChronoUnit/MINUTES then-t now-t)
        seconds (.between ChronoUnit/SECONDS then-t now-t)]
    (cond
      (> years 0) (plural-msg years :time/year.ago :time/years.ago)
      (> months 0) (plural-msg months :time/month.ago :time/months.ago)
      (> weeks 0) (plural-msg weeks :time/week.ago :time/weeks.ago)
      (> days 0) (plural-msg days :time/day.ago :time/days.ago)
      (> hours 0) (plural-msg hours :time/hour.ago :time/hours.ago)
      (> minutes 0) (plural-msg minutes :time/minute.ago :time/minutes.ago)
      (> seconds 0) (plural-msg seconds :time/second.ago :time/seconds.ago)

      (< years 0) (plural-msg years :time/year.within :time/years.within)
      (< months 0) (plural-msg months :time/month.within :time/months.within)
      (< weeks 0) (plural-msg weeks :time/week.within :time/weeks.within)
      (< days 0) (plural-msg days :time/day.within :time/days.within)
      (< hours 0) (plural-msg hours :time/hour.within :time/hours.within)
      (< minutes 0) (plural-msg minutes :time/minute.within :time/minutes.within)
      (< seconds 0) (plural-msg seconds :time/second.within :time/seconds.within)
      :else (tr [:now]))))

(comment
  (let [then-t (t/<< (t/date-time) (t/new-duration 5 :days))
        now-t (t/date-time)]
    (from then-t :now-t now-t))

  (let [then-t (t/<< (t/date-time) (t/new-duration 100 :days))
        now-t (t/date-time)]
    (from then-t :now-t now-t))

  (from (t/date-time) :now-t (t/date-time))
  (from (t/date))

;;
  )
