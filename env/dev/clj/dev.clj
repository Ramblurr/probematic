(ns dev
  (:require
   [app.routes.pedestal-reitit]
   [app.ig]
   [app.routes.pedestal-reitit]
   [app.gigo.core :as gigo]
   [app.jobs.sync-gigs :as sync-gigs]
   [app.jobs.sync-members :as sync-members]
   [app.schemas :as schemas]
   [integrant.repl.state :as state]
   [malli.dev :as md]
   [datomic.client.api :as d]
   [ol.app.dev.dev-extras :as dev-extra]
   [clojure.tools.namespace.repl :as repl]
   [ol.system :as system]
   [browser :as browser]
   [jsonista.core :as j]
   [clojure.string :as str]
   [tick.core :as t]
   [medley.core :as m]))

(repl/disable-reload! (find-ns 'browser))

(set! *print-namespace-maps* false)

;(mr/set-default-registry! schemas/registry)
(def datomic (-> state/system :app.ig/datomic-db))
(def conn (:conn datomic))
(def app (-> state/system :app.ig.router/routes))

(defn go []
  (dev-extra/go)
  (browser/open-browser "http://localhost:6161/"))

(defn reset []
  (dev-extra/reset)
  (browser/refresh))

(defn halt []
  (dev-extra/halt))

(comment
  (go)
  (eta/chrome {:path-driver "/usr/bin/chromedriver"
               :path-browser "/usr/bin/chromium-freeworld"})

  @browser

  (dev-extra/go)
  (dev-extra/halt)
  (dev-extra/reset)

;;;  SEEDS

  (do
    (require '[integrant.repl.state :as state])
    (def env (:app.ig/env state/system))
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def gigo (:app.ig/gigo-client state/system))
    (def sno "ag1zfmdpZy1vLW1hdGljciMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDA")) ;; rcf

  (do
    (let [members (j/read-value (slurp "/var/home/ramblurr/src/sno/probematic2/gigo-members.json") j/keyword-keys-object-mapper)
          tx-data (map (fn [{:keys [gigo_key email nick name section occ]}]
                         {:member/gigo-key gigo_key
                          :member/email email
                          :member/nick nick
                          :member/name name
                          :member/section [:section/name section]
                          :member/active? (not occ)}) members)]
      (d/transact conn {:tx-data tx-data}))
    (sync-members/update-member-data! conn (sync-members/fetch-people-data! (:airtable env)))
    (gigo/update-cache! gigo)
    (sync-gigs/update-gigs-db! conn @gigo/gigs-cache)

    (d/transact conn {:tx-data
                      (map (fn [{:keys [id display_name]}]
                             [:db/add [:member/gigo-key id] :member/nick display_name])
                           (gigo/get-band-members! gigo sno))})) ;; END SEEDS

  (require '[tick.core :as t])
  (require '[clojure.string :as str])
  (defn try-parse-date [date]
    (try
      (t/at (t/parse-date date (t/formatter "M/d/yyyy")) (t/midnight))
      (catch Throwable e
        nil)))

  (defn gig-archive-tx [{:keys [date plans title gig_id details setlist leader paydeal outfit location]}]
    (let [date-real (try-parse-date date)]
      {:gig/gig-id gig_id
       :gig/title title
       :gig/location location
       :gig/more-details details
       :gig/leader leader
       :gig/setlist setlist
       :gig/outfit outfit
       :gig/date date-real
       :gig/pay-deal paydeal
       :plans plans}))

  (def plan "\ndrums\n\tFelix Hofer (Felix drum) - No Plan \n\tLukas - No Plan \n\tSebastian  (Sebastian ) - Definitely \n\tSebliz - No Plan \n\tTeresa (Teresa) - Definitely - gehen wir danach frühstücken? ;)\n\nbasses\n\tFabio Schafferer (Fabio) - Definitely - ich würd gern die tuba nicht mit auf die uni nehmen müssen.. \n\tFelix Rauch (Felix sax) - Definitely - obwohls schon saufrüh is freu ich mich, dasswir wieder draussn sind, und 2 leut mehr als vor 1 jahr.\n\nsax alto\n\tKlaus Falkensammer (Klausi) - Definitely - (: Juhu!\n\tAndrina - Definitely \n\ttanjae - Can't Do It \n\nsax tenor\n\tAndrea Christmann (Andrea) - Probably Not - Leider zu kurzfristig, muss früh im Büro sein, Besprechungstermin \n\tleo - Definitely - i müsst mir an sitz mitnehmen und kann nur statoinär spielen\n\ntrumpets\n\tChristian - Definitely \n\tandreaslageder - No Plan \n\tauercat - No Plan \n\tBenedikt (Benedikt) - Definitely \n\tmar_ry - Definitely - wenn mich wer aus dem bett klingelt...\n\nclarinet\n\tLau - No Plan \n\tJulia (Julia) - No Plan \n\nflute\n\tandrea - Definitely \n\th.krismer - No Plan \n")
  (defn parse-plan [plan]
    (if (or (not plan) (str/includes? plan "The gig was cancelled"))
      nil
      (map (fn [s]
             (if (str/starts-with? s "\t")
               (if-let [m (re-matches (re-pattern "\t(.*) \\((.*)\\) - (.*) - (.*)$")  s)]
                 {:name (nth m 1) :nick (nth m 2) :plan (nth m 3) :comment (nth m 4)}
                 (if-let [m (re-matches (re-pattern "\t(.*) \\((.*)\\) - (.*)$")  s)]
                   {:name (nth m 1) :nick (nth m 2) :plan (nth m 3)}
                   (if-let [m (re-matches (re-pattern "\t(.*) - (.*) - (.*)$")  s)]
                     {:name (nth m 1) :plan (nth m 2) :comment (nth m 3)}
                     (if-let [m (re-matches (re-pattern "\t(.*) - (.*)$")  s)]
                       {:name (nth m 1) :plan (nth m 2)}
                       :not-parsed))))
               :not-person))
           (str/split-lines plan))))
  (parse-plan plan)
  (defn gig-plans [{:keys [plans] :as gig}]
    (assoc gig :plans
           (->> (parse-plan plans)
                (remove #(= :not-person %))
                (map #(m/map-vals str/trim %)))))

  (let [archive (map #(j/read-value % j/keyword-keys-object-mapper)
                     (str/split-lines (slurp "/var/home/ramblurr/src/sno/gigoarchive/output.jsonl")))
        tx (->> archive
                (take 100)
                (map gig-archive-tx)
                (map gig-plans))]
    (->> tx
         ;; (map #(select-keys % [:gig/title :gig/date]) )
         (remove #(nil? (:gig/date %))))
    ;;
    )

  (halt)
  (dev-extra/go)

  (require '[datomic.dev-local :as dl])
  (dl/release-db {:system "dev" :db-name "probematic"})
  (md/start! schemas/malli-opts)
  (md/stop!)

  (set-prep! {:profile :dev})
  (keys state/system)
  (-> state/system :app.ig/pedestal)
  (-> state/system :app.ig/env)
  (-> state/system :app.ig/profile)

  (system/config {:profile :dev})

  (system/system-config {:profile :dev})

  ;;
  )
