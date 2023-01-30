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
   [medley.core :as m]
   [app.gigs.domain :as domain]
   [app.config :as config]))

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
                 (d/find-all db :member/member-id [:member/name :member/email :member/member-id])
                 (map first)
                 (map #(update % :member/email str/lower-case)))
        joined (set/join user-list members {:email :member/email})
        txs (->> joined
                 (map #(select-keys % [:member/member-id :avatar_template :id :username]))
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
  (when-not (str/blank? v)
    (->> (str/split-lines v)
         (map (fn [line]
                (str "> " line)))
         (str/join "\n"))))

(defn gig->markdown-post [env {:gig/keys [attendance-summary status planned-songs title more-details location contact leader date end-date] :as gig}]
  (selmer/render
   "
<!--- DO NOT EDIT THIS POST,

HEY YOU


.. YEA.. YOU!


DONT EDIT THIS POST!!


WHY?


PROBEMATIC AUTOMATICALLY UPDATES IT.


IF YOU WANT TO CHANGE SOMETHING..

EDIT IT IN PROBEMATIC



SO... STOP EDITING IT NOW


.... k thx













WHY ARE YOU STILL HERE?







GO TO PROBEMATIC!!


{{probematic-link}}

--->
**Bestätigt: {{confirmed}}**


**Datum:** {{time}} {{date}}

**Ort:** {{location}}

{% if details|not-empty %}**Programm:**
{{details|safe}}{% endif %}

{% if not leader|empty? %}**Gig-Master:** {{leader}}{% endif %}

{% if counts|not-empty %}
**Attendance**:
{% for item in counts %}{{item.icon}} {{item.value}}
{% endfor %}
{% endif %}
{% if planned-songs|not-empty %}
**{{planned-songs-title}}**:

{% for item in planned-songs %}{{item.position}}. [{{item.label}}]({{item.href}}) {% if item.extra %}{{item.extra}}{% endif %}
{% endfor %}
{% endif %}


[**:arrow_right: More info in the Probematic**]({{probematic-link}})

"
   {:confirmed
    (case status
      :gig.status/confirmed ":gig_confirmed:"
      :gig.status/unconfirmed ":gig_unconfirmed:"
      :gig.status/cancelled ":gig_cancelled:")
    :probematic-link (url/absolute-link-gig env (:gig/gig-id gig))
    :date (ui/gig-date-plain gig)
    :time (ui/gig-time gig)
    :location location
    :details  (markdown-quote more-details)
    :leader leader
    :planned-songs  planned-songs
    :planned-songs-title (if (domain/setlist-gig? gig) "Setlist" "Probeplan")
    :counts  (map (fn [[plan count]]
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

(defn format-topic-title [gig]
  (str (:gig/title gig) " " (ui/gig-date-plain gig)))

(defn category-for [dev-mode? {:gig/keys [gig-type gig-id]}]
  (if-let [[_ v] (find {:gig.type/probe 7
                        :gig.type/extra-probe 7
                        :gig.type/meeting 9
                        :gig.type/gig 6} gig-type)]

    (if dev-mode? 4 v)
    (throw (ex-info "Unknown discourse category for gig type" {:gig-type gig-type
                                                               :gig-id gig-id}))))

(defn update-topic-for-gig [env gig topic]
  (let [topic-title (format-topic-title gig)
        category-id (category-for (config/dev-mode? env) gig)]
    (when (or
           (not= category-id (:category_id topic))
           (not= topic-title (:title topic)))
      (request! env {:method :put
                     :url (format "/t/-/%s.json" (:id topic))
                     :headers {"content-type" "application/json"}
                     :body (j/write-value-as-string
                            {:title topic-title
                             :category_id category-id})}))))

(defn new-thread-for-gig
  "Creates a new topic for the gig, returns the topic id."
  [env gig]
  (let [gig-id (:gig/gig-id gig)]
    (:id
     (request! env
               {:method :post
                :url "/posts.json"
                :form-params {:title (format-topic-title gig)
                              :raw (gig->markdown-post env gig)
                              :category (category-for (config/dev-mode? env) gig)
                              :embed_url (url/absolute-link-gig env gig-id)
                              :external_id gig-id}}))))

(defn summarize-attendance [gig {:keys [db]}]
  (assoc gig :gig/attendance-summary
         (->> (q/attendances-for-gig db (:gig/gig-id gig))
              (remove #(nil? (:attendance/plan %)))
              (group-by :attendance/plan)
              (m/map-vals count))))

(defn planned-songs [gig {:keys [env db]}]
  (assoc gig :gig/planned-songs
         (map (fn [{:song/keys [title song-id] :keys [emphasis position]}]
                {:href (url/absolute-link-song env song-id)
                 :label title
                 :position (when position (inc position))
                 :extra (when (= :probeplan.emphasis/intensive emphasis) (str " (intensive)"))})
              (q/planned-songs-for-gig db (:gig/gig-id gig)))))

(defn upsert-thread-for-gig!
  "If a thread was created, returns the topic id, otherwise nil."
  [{:keys [env db] :as sys} gig-id]
  (let [gig (-> (q/retrieve-gig db gig-id)
                (summarize-attendance sys)
                (planned-songs sys))
        topic (topic-for-gig {:env env} gig)]
    (if-let [post-id (:id (first-post-for-topic topic))]
      (do
        (update-topic-for-gig env gig topic)
        (update-post-for-gig env gig post-id))
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
  (d/find-all db :member/member-id [:member/name :member/email :member/member-id :member/avatar-template :member/discourse-id :member/nick])

  (def g (q/retrieve-gig db "01860302-7e21-8c75-915a-ab04fc38d0c0"))
  (def g2 (q/retrieve-gig db "01860302-7e21-8c75-915a-ab04fc38d0c1"))
  (:id (first-post-for-topic (topic-for-gig {:env env} g)))

  (ui/gig-time g2)
  (url/absolute-link-song env
                          (:song/song-id)
                          (first
                           (q/planned-songs-for-gig db (:gig/gig-id g2))))

  (:category_id
   (topic-for-gig {:env env} g2))
  (upsert-thread-for-gig! {:env env :db db} (:gig/gig-id g2)) ;; rcf

  ;;
  )
