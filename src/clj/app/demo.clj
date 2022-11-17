(ns app.demo
  (:import
   [java.util.concurrent TimeUnit]
   [com.github.javafaker Faker])
  (:require [clojure.string :as str]
            [datomic.client.api :as d]
            [clojure.tools.logging :as log]))

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
                               :member/active? true}]})
  (d/transact conn {:tx-data (rand-members-txs 30)}))
