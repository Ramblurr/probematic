(ns app.auth-test
  (:require [clojure.test :refer :all]))
(def raw-profile {:role "GLOBAL_ADMIN"
                  :locale    "en_US"
                  :id        1
                  :fullName  "Alice"
                  :timeFormat "HH:mm"
                  :username  "alice@alice.com"
                  :companyId 1})

(def profile {:app.auth/roles      #{:global_admin}
              :locale    "en_US"
              :id        1
              :full-name  "Alice"
              :time-format "HH:mm"
              :username  "alice@alice.com"})
