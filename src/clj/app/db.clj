(ns app.db
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as jdbc.sql]
   [honey.sql :as honey.sql]
   [honey.sql.helpers :as honey.helpers]
   [medley.core :as m]))

(defn find-one! [table]
  (fn [db id]
    (jdbc/execute-one!
     db
     (honey.sql/format
      {:select [:*]
       :from   [table]
       :where  [:= :id id]}))))

(defn insert-for-key!
  "Executes the insert statement, returning the GENERATED_KEY"
  [db stmt]
  (:GENERATED_KEY (jdbc/execute-one! db stmt (merge jdbc/snake-kebab-opts {:return-keys true}))))

(defn insert!
  "Executes the insert statement, returning the generated key"
  [key-map {:keys [ds table]}]
  (:generated-key (jdbc.sql/insert! ds table key-map jdbc/snake-kebab-opts)))

(defn update!-
  "Executes the update statement"
  [db stmt]
  (jdbc/execute! db stmt (merge jdbc/snake-kebab-opts {:return-keys true})))

(defn update!
  "Executes the insert statement, returning the GENERATED_KEY"
  [key-map {:keys [ds table id]}]
  (:next.jdbc/update-count (jdbc.sql/update! ds table key-map {:id id} jdbc/snake-kebab-opts)))

(defn update-and-return!
  "Executes the update statement, returns the updated row (fetched by id)"
  [key-map {:keys [ds table id] :as opts}]
  (update! key-map opts)
  ((find-one! table) ds id))

(defn delete!
  "Executes the delete statement, returning the number of rows affected"
  [db stmt]
  (let [result (jdbc/execute! db stmt jdbc/snake-kebab-opts)]
    (-> result first :next.jdbc/update-count)))

(defn count-by
  "See next.jdbc.sql/find-by-keys."
  ([db table]
   (count-by db table :all))
  ([db table key-map]
   (:total (first (jdbc.sql/find-by-keys db table key-map (merge jdbc/snake-kebab-opts {:columns [["count(*)" :total]]}))))))

(defn select-ns-keys
  "Like select-keys, but pulls out all the keys having namespace ns"
  [m ns]
  (m/filter-keys (fn [k] (= ns (namespace k))) m))

(defn coalesce-key
  "If you JOIN across multiple tables, you still get a flat hash map result. It would be nice to get structured hash maps back.
  Works for 1-1 relationships only.

  Params:
    m - is the flat map you want to coalesce
    id-key - the key in describing the id value of the 1-1 relationship, will be dissoced (e.g., :pet/owner_id)
    child-key - the key to assoc to the map desc describing the 1-1 relationship (e.g., :pet/owner)
    id-ns - the namespace of the 1-1 keys that will be coalesced under child-key (e.g, \"owner\")
  "
  [m id-key child-key id-ns]
  (let [child-m (select-ns-keys m (name id-ns))
        child-keys (keys child-m)]
    (if (empty? child-m)
      m
      (-> m
          (assoc child-key child-m)
          ((fn [m] (apply dissoc m child-keys)))
          (dissoc id-key)))))

(def ^:private where-ops
  {:eq      :=
   :gt      :>
   :lt      :<
   :gte     :>=
   :lte     :<=
   :ne      :!=
   :in      :in
   :like    :like
   :between :between
   :is_null [:is :is-not]
   :is :is
   :is-not :is-not})

(defn- parse-arg [gql-op op arg]
  (cond
    (map? arg) (vals arg)
    (= :is_null gql-op) [nil]
    :else  [arg]))

(defn- parse-op [gql-op op arg]
  (cond
    (= :is_null gql-op) (if arg (first op) (second op))
    :else
    op))

(defn- parse-rsc-where [rsc-where]
  (map (fn [[k v]]
         (let [entry (first v)
               gql-op (key entry)
               op (gql-op where-ops)
               arg (val entry)]
           (concat [(parse-op gql-op op arg) k] (parse-arg gql-op op arg))
           ;[op k (val entry)]
           ))
       rsc-where))

(defn- parse-and-or [op rsc-where-list]
  (let [whr-list (reduce (fn [v rsc-where]
                           (concat v (parse-rsc-where rsc-where)))
                         [] rsc-where-list)]
    (concat [op] whr-list)))

(defn parse-where [args]
  (let [whr (:where args)]
    (cond-> (parse-rsc-where (dissoc whr :and :or))
      (some? (:or whr)) (conj (parse-and-or :or (:or whr)))
      (some? (:and whr)) (conj (parse-and-or :and (:and whr))))))

(extend-protocol rs/ReadableColumn
  java.sql.Date
  (read-column-by-label [^java.sql.Date v _]     (.toLocalDate v))
  (read-column-by-index [^java.sql.Date v _2 _3] (.toLocalDate v))
  java.sql.Timestamp
  (read-column-by-label [^java.sql.Timestamp v _]     (.toInstant v))
  (read-column-by-index [^java.sql.Timestamp v _2 _3] (.toInstant v)))

(comment

  (parse-where {:where {:or [{:company_name {:eq "omg"}}
                             {:company_name {:like "Foo"}}]}})

  (parse-where {:where {:approved_at {:between {:min 100 :max 200}}}})
  (parse-where {:where {:approved_at {:gt 100}}})
  (parse-where {:where {:approved_at {:is nil}}})
  (args->sql-params {:where {:approved_at {:is_null false}}})

  ;
  )
