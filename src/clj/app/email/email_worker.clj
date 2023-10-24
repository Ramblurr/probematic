(ns app.email.email-worker
  (:require
   [clojure.tools.logging :as log]
   [app.email.domain :refer [QueuedEmailMessage]]
   [app.email.mailgun :as mailgun]
   [app.schemas :as s]
   [sentry-clj.core :as sentry]
   [taoensso.carmine :as car]
   [taoensso.carmine.message-queue :as car-mq]))

(def email-queue-name "email-send-queue")

(defn track-email-error!  [email attempt result throwable]
  (sentry/send-event
   (doto
    {:message (or (:message result) (ex-message throwable) "Error occured in email-worker")
     :extra {:email email
             :attempt attempt
             :result result}
     :throwable throwable} tap>)))

(defn handler
  "Has strict return contract with carmine. See http://ptaoussanis.github.io/carmine/taoensso.carmine.message-queue.html#var-worker"
  [sys message attempt]
  (tap> {:email-worker/received message :email-worker/attempt attempt})
  (try
    (if (s/valid? QueuedEmailMessage message)
      (let [result (if (:email/batch? message)
                     (mailgun/send-batch! sys
                                          (:email/tos message)
                                          (:email/subject message)
                                          (:email/body-plain message)
                                          (:email/body-html message)
                                          (:email/recipient-variables message))

                     (mailgun/send-email! sys
                                          (first (:email/tos message))
                                          (:email/subject message)
                                          (:email/body-plain message)
                                          (:email/body-html message)))]
        (tap> {:email-send result})
        (if (:error result)
          (do
            (track-email-error! message attempt result nil)
            (if (:retry? result)
              {:status :retry :backoff-ms 5000}
              {:status :error}))
          {:status :success}))
      (do
        (track-email-error! message attempt (s/explain-human QueuedEmailMessage message) nil)
        {:status :error}))
    (catch Throwable e
      (tap> e)
      (track-email-error! message attempt nil e)
      {:status :error})))

(defn start! [{:keys [redis] :as sys}]
  (log/info "Starting email worker")
  (car-mq/worker redis email-queue-name
                 {:handler (fn [{:keys [message attempt]}] (handler sys message attempt))
                  :eoq-backoff-ms 50
                  :throttle-ms 50}))
(defn stop! [worker]
  (car-mq/stop worker))

(defn queue-mail! [redis-opts email]
  (assert (:email/subject email))
  (car/wcar redis-opts
            (car-mq/enqueue email-queue-name email)))

(comment
  (do
    (require '[integrant.repl.state :as state])
    (require '[hiccup2.core :refer [html]])

    (def recipient1 "foo@example.com")
    (def recipient2 "foo+test@example.com") ;; rcf
    (def redis-opts (-> state/system :app.ig/redis))) ;; rcf

  (queue-mail! redis-opts
               {:email/batch? true
                :email/email-id "foo"
                :email/tos []
                :email/subject "Hello from probematic"
                :email/body-plain  "Hello world, %recipient.foobar%"
                :email/body-html (str
                                  (html [:div [:h1 "Hello world!"]
                                         [:p " and it is '%recipient.foobar%'"]
                                         [:p [:a {:href "https://streetnoise.at/%recipient.foobar%"} "streetnoise.at"]]
                                         [:p "Things and stuff!"]]))
                :email/recipient-variables {recipient2 {:foobar "FOOBARRECIPIENT2"} recipient1 {:foobar "FOOBARRECIPIENT1"}}})

  ;;
  )
