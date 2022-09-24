(ns app.health.health-endpoint-test
  (:require [clojure.test :refer :all]
            [hato.client :as hc]
            [integrant.core :as ig]
            [jsonista.core :as json]
            [app.test-system :refer [with-system-fixture *system*]]
            [clojure.edn :as edn]
            [app.debug :as debug]
            [clojure.set :as set]))

(defn new-system
  "Define a minimal system which is just enough for the tests in this
  namespace to run"
  []
  {})

(use-fixtures :once (with-system-fixture))

(deftest system-test
  ; a basic test to sanity check our config loading and integrant system start
  (is *system*)
  (is (= (:app.ig/profile *system*) :test) "loading profile from config works")
  (is (= (get-in *system* [:app.ig/pedestal :io.pedestal.http/port]) 6162) "test http port is set"))

(deftest route-test-health
  (let [resp (hc/get "http://localhost:6162/api/health" {:headers {"accept" "application/edn"}})]
    (is (= (:status resp) 200))
    (is (= (-> resp :body (edn/read-string) :app :status)
           "up"))))
