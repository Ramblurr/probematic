(ns app.demo
  (:import
   [java.util.concurrent TimeUnit]
   [com.github.javafaker Faker])
  (:require [clojure.string :as str]
            [datomic.client.api :as d]
            [clojure.tools.logging :as log]
            [tick.core :as t]
            [app.gigs.domain :as domain]))

(def faker (Faker.))

(defn last-name []
  (-> faker (.name) (.lastName)))

(defn first-name []
  (-> faker (.name) (.firstName)))

(defn person-name []
  (-> faker (.name) (.name)))

(defn phone []
  (-> faker (.phoneNumber) (.phoneNumber)))

(defn email []
  (-> faker (.internet) (.emailAddress)))

(defn gigo-key []
  (-> faker (.letterify "???????????????????????????????????????????????")))

(defn section []
  (rand-nth ["flute"
             "bass"
             "percussion"
             "trumpet"
             "dance"
             "sax alto"
             "sax tenor"
             "trombone/bombardino"
             "sax soprano/clarinet"]))

(defn member []
  (let [full-name (person-name)]
    {:member/gigo-key (gigo-key)
     :member/name full-name
     :member/nick (-> full-name (str/split #" ") first (str/lower-case))
     :member/email (email)
     :member/phone (phone)
     :member/active? true}))

(defn rand-member-txs []
  [(merge (member) {:member/section [:section/name (section)]})])

(defn rand-members-txs
  "Return a list of txs of length amt with new random members"
  [amt]
  (flatten (repeatedly amt rand-member-txs)))

(defn seed-random-members! [conn]
  (log/info "Seeding random members")

  (d/transact conn {:tx-data [{:member/gigo-key "admin"
                               :member/name "Admin Nimda"
                               :member/nick "admin"
                               :member/email "admin@example.com"
                               :member/phone "+112738127387"
                               :member/section [:section/name "bass"]
                               :member/active? true}]})
  (d/transact conn {:tx-data (rand-members-txs 30)}))

(defn seed-gigs! [conn]
  (log/info "Seeding gigs")
  (d/transact conn {:tx-data
                    (map domain/gig->db
                         [{:gig/gig-id (gigo-key)
                           :gig/title "Rehersal"
                           :gig/location "Proberaum"
                           :gig/gig-type :gig.type/probe
                           :gig/status :gig.status/confirmed
                           :gig/date  (t/>> (t/date) (t/new-period 7 :days))
                           :gig/contact        [:member/gigo-key "admin"]
                           :gig/call-time (t/time "19:00")
                           :gig/set-time (t/time "19:30")}
                          {:gig/gig-id (gigo-key)
                           :gig/title "Rehersal"
                           :gig/location "Proberaum"
                           :gig/gig-type :gig.type/probe
                           :gig/status :gig.status/confirmed
                           :gig/date  (t/>> (t/date) (t/new-period 14 :days))
                           :gig/contact        [:member/gigo-key "admin"]
                           :gig/call-time (t/time "19:00")
                           :gig/set-time (t/time "19:30")}
                          {:gig/gig-id (gigo-key)
                           :gig/title "Treibhaus Gig"
                           :gig/location "Treibhaus"
                           :gig/gig-type :gig.type/gig
                           :gig/status :gig.status/unconfirmed
                           :gig/date  (t/>> (t/date) (t/new-period 6 :days))
                           :gig/contact        [:member/gigo-key "admin"]
                           :gig/call-time (t/time "13:00")
                           :gig/leader     "Norbert"
                           :gig/post-gig-plans "Pizza and beer"
                           :gig/more-details   "We're playing at Weltfest again this year."
                           :gig/pay-deal "500 EUR + drinks"
                           :gig/set-time (t/time "13:20")}])}))