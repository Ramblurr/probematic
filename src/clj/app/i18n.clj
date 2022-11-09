(ns app.i18n
  (:require [clojure.set      :refer [intersection]]
            [clojure.java.io :as clojure.java.io]
            [taoensso.tempura :as tempura :refer [tr]]
            [taoensso.encore  :as enc]))

(def default-locale :en)

(defn load-resource [filename & second]
  (try
    (let [content (enc/read-edn (slurp (clojure.java.io/resource filename)))]
      (if (not content)
        (if (not second)
          (load-resource (str "/" filename) :second)
          (throw
           (ex-info "Failed to load dictionary resource"
                    {:filename filename})))
        content))
    (catch Exception e
      (throw
       (ex-info "Failed to load dictionary resource"
                {:filename filename})))))

(defn read-langs []
  {:en (load-resource "lang/en.edn")
   :de (load-resource "lang/de.edn")})

(defn tr-opts [param-langs] {:dict param-langs :default-locale default-locale})

(defn supported-lang [param-langs accept-langs]
  (if-not (empty? accept-langs)
    (let [accepted-empty-removed (filter #(>= (count %1) 2) accept-langs)
          keyword-accepted-langs (vec
                                  (map
                                   #(keyword (subs % 0 2))
                                   accepted-empty-removed))
          accept-langs-set (into #{} keyword-accepted-langs)
          langs-set (into #{} (keys param-langs))
          lang-intersection (intersection langs-set accept-langs-set)
          lang-match (first
                      (filter
                       #(contains? lang-intersection %)
                       keyword-accepted-langs))]
      (or lang-match default-locale))
    default-locale))

(defn tr-with
  ([param-langs langs]
   (when (first langs)
     (partial tr (tr-opts param-langs) langs))))

(defn tr-from-req [req]
  (:tempura/tr req))
