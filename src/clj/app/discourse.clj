(ns app.discourse
  (:require
   [org.httpkit.client :as client]
   [selmer.parser :as selmer]
   [app.datomic :as d]
   [clojure.set :as set]
   [clojure.string :as str]
   [datomic.client.api :as datomic]
   [integrant.repl.state :as state]
   [jsonista.core :as j]
   [martian.core :as martian]
   [martian.httpkit :as martian-http]
   [app.urls :as url]
   [app.ui :as ui]
   [app.queries :as q]
   [app.debug :as debug]
   [medley.core :as m]
   [app.gigs.domain :as domain]))

(defn add-authentication-header [api-key username]
  {:name ::add-authentication-header
   :enter (fn [ctx]
            (-> ctx
                (assoc-in [:request :headers "Content-Type"] "multipart/form-data;")
                (assoc-in [:request :headers "Api-Key"] api-key)
                (assoc-in [:request :headers "Api-Username"] username)))})

(def url-discourse-open-api "https://docs.discourse.org/openapi.json")

(defn list-users [m]
  (let [{:keys [status body] :as r}
        @(martian/response-for m :admin-list-users {:flag "active" :show_emails true})]
    (if (= 200 status)
      (j/read-value body j/keyword-keys-object-mapper)
      {:error r})))
(defn sync-avatars! [{:keys [env conn]}]
  (let [db (datomic/db conn)
        {:keys [api-key username forum-url]} (:discourse env)
        m (martian-http/bootstrap-openapi url-discourse-open-api {:server-url forum-url
                                                                  :interceptors (concat martian/default-interceptors
                                                                                        [(add-authentication-header api-key username)]
                                                                                        [martian-http/perform-request])})
        user-list (list-users m)
        members (->>
                 (d/find-all db :member/gigo-key [:member/name :member/email :member/gigo-key])
                 (map first)
                 (map #(update % :member/email str/lower-case)))
        joined (set/join user-list members {:email :member/email})
        txs (->> joined
                 (map #(select-keys % [:member/gigo-key :avatar_template :id :username]))
                 (map #(update % :id str))
                 (map #(set/rename-keys % {:avatar_template :member/avatar-template :id :member/discourse-id :username :member/nick})))]
    (d/transact conn {:tx-data txs})))
(defn wrap-auth [req {:keys [discourse] :as env}]
  (-> req
      (assoc-in [:headers "Api-Key"] (:api-key discourse))
      (assoc-in [:headers "Api-Username"] (:username discourse))))

(defn wrap-api-url [req {:keys [discourse]}]
  (update-in req [:url] (fn [path] (str (:forum-url discourse) path))))

(defn request! [env req]
  (let [{:keys [status body] :as r} @(client/request
                                      (-> req
                                          (wrap-auth env)
                                          (wrap-api-url env)))]

    (if (= 200 status)
      (j/read-value body j/keyword-keys-object-mapper)
      (throw (ex-info "Discourse Error" {:resp r})))))

(defn markdown-quote [v]
  (when v
    (->> (str/split-lines v)
         (map (fn [line]
                (str "> " line)))
         (str/join "\n"))))

(defn gig->markdown-post [env {:gig/keys [gig-type attendance-summary status planned-songs title more-details location contact leader date end-date] :as gig}]
  (selmer/render
   "**Bestätigt: {{confirmed}}**


**Datum:** {{time}} {{date}}

**Ort:** {{location}}

{% if not details|empty? %}**Programm:**
{{details|safe}}{% endif %}

{% if not leader|empty? %}**Gig-Master:** {{leader}}{% endif %}

**Attendance**:

{% for item in counts %}{{item.icon}} {{item.value}}
{% endfor %}

**{{planned-songs-title}}**:

{% for item in planned-songs %}{{item.position}}. [{{item.label}}]({{item.href}}) {% if item.extra %}{{item.extra}}{% endif %}
{% endfor %}


[**:arrow_right: More info in the Probematic**]({{probematic-link}})

"
   {:confirmed
    (case status
      :gig.status/confirmed ":gig_confirmed:"
      :gig.status/unconfirmed ":gig_unconfirmed:"
      :gig.status/cancelled ":gig_cancelled:")
    :probematic-link (url/absolute-link-gig env (:gig/gig-id gig))
    :date "" #_(ui/gig-date-plain gig)
    :time (ui/gig-time gig)
    :location location
    :details (markdown-quote more-details)
    :leader leader
    :planned-songs (debug/xxx planned-songs)
    :planned-songs-title (if (domain/setlist-gig? gig) "Setlist" "Probeplan")
    :counts (map (fn [[plan count]]
                   {:icon
                    (get
                     {:plan/no-response "**—**"
                      :plan/definitely ":gruener_kreis:"
                      :plan/probably ":gruener_ringel:"
                      :plan/unknown ":fragezeichen:"
                      :plan/probably-not ":roter_quadratischer_umriss:"
                      :plan/definitely-not ":rotes_quadrat:"
                      :plan/not-interested ":schwarz_kreuz:"} plan)
                    :value (str count)})

                 attendance-summary)}))

(defn topic-for-gig [{:keys [env]} gig]
  (try
    (request! env
              {:method :get
               :url (format "/t/external_id/%s.json" (:gig/gig-id gig))})
    (catch Throwable e
      (if (= 404 (-> (ex-data e) :resp :status))
        nil
        (throw e)))))

(defn first-post-for-topic [topic]
  (m/find-first (fn [{:keys [post_number]}]
                  (= 1 post_number))
                (-> topic
                    :post_stream
                    :posts)))

(defn update-post-for-gig [env gig post-id]
  (request! env
            {:method :put
             :url (format "/posts/%s.json" post-id)
             :headers {"content-type" "application/json"}
             :body (j/write-value-as-string
                    {:raw (gig->markdown-post env gig)
                     :edit_reason "something changed in probematic"})})
  nil)

(defn new-thread-for-gig
  "Creates a new topic for the gig, returns the topic id."
  [env gig]
  (let [gig-id (:gig/gig-id gig)]
    (:id
     (request! env
               {:method :post
                :url "/posts.json"
                :form-params {:title (str (:gig/title gig) " " (ui/gig-date-plain gig))
                              :raw (gig->markdown-post env gig)
                              :category 4
                              ;; :skip_validations true
                              ;; :auto_track false
                              ;; :embed_url (str (url/absolute-link-gig env gig-id) "#")
                              :external_id gig-id}}))))

(defn summarize-attendance [gig db]
  (assoc gig :gig/attendance-summary
         (->> (q/attendances-for-gig db (:gig/gig-id gig))
              (group-by :attendance/plan)
              (m/map-vals count))))

(defn planned-songs [gig {:keys [env db]}]
  (assoc gig :gig/planned-songs
         (map (fn [{:song/keys [title song-id] :keys [emphasis position]}]
                {:href (url/absolute-link-song env song-id)
                 :label title
                 :position (inc position)
                 :extra (when (= :probeplan.emphasis/intensive emphasis) (str " (intensive)"))})
              (q/planned-songs-for-gig db (:gig/gig-id gig)))))

(defn upsert-thread-for-gig
  "If a thread was created, returns the topic id, otherwise nil."
  [{:keys [db env] :as sys} gig]
  (let [gig (-> gig
                (summarize-attendance db)
                (planned-songs sys))]
    (if-let [post-id (:id (first-post-for-topic (topic-for-gig {:env env} gig)))]
      (update-post-for-gig env gig post-id)
      (new-thread-for-gig env gig))))

(comment

  (do
    (require '[integrant.repl.state :as state])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def env (-> state/system :app.ig/env))
    (def db  (datomic/db conn)) ;; rcf
    (let [{:keys [api-key username forum-url]} (:discourse env)]
      (def m (martian-http/bootstrap-openapi url-discourse-open-api {:server-url "https://forum.streetnoise.at"
                                                                     :interceptors (concat martian/default-interceptors
                                                                                           [(add-authentication-header api-key username)]
                                                                                           [martian-http/perform-request])})))) ;; rcf

  (sort-by first (martian/explore m))
  (martian/explore m :admin-list-users)
  (d/find-all db :member/gigo-key [:member/name :member/email :member/gigo-key :member/avatar-template :member/discourse-id :member/nick])

  (def g (q/retrieve-gig db "0185cfb2-4ac4-8cab-b1d9-5dd1fd1a3e20"))
  (def g2 (q/retrieve-gig db "ag1zfmdpZy1vLW1hdGljcjMLEgRCYW5kIghiYW5kX2tleQwLEgRCYW5kGICAgMD9ycwLDAsSA0dpZxiAgMCil42RCQw"))
  (:id (first-post-for-topic (topic-for-gig {:env env} g)))

  (upsert-thread-for-gig {:env env :db db} g2) ;; rcf

  ;;
  )
