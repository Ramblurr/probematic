;; From https://github.com/clojusc/ring-redis-session/commit/7bd934794066924d06447c090a9800ad881fd98b
;; Copyright © 2013 Zhe Wu wu@madk.org
;; Copyright © 2016-2018 Clojure-Aided Enrichment Center
;; Distributed under the Eclipse Public License, the same as Clojure.
(ns app.session (:require
                 [ring.middleware.session.store :as api]
                 [taoensso.carmine :as redis])
    (:import
     [java.util UUID]))

(defn new-session-key [prefix]
  (str prefix ":" (str (UUID/randomUUID))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Method implementations   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn read-redis-session
  "Read a session from a Redis store."
  [this session-key]
  (let [conn (:redis-conn this)]
    (when session-key
      (when-let [data (redis/wcar conn (redis/get session-key))]
        (let [read-handler (:read-handler this)]
          (when (and (:expiration this) (:reset-on-read this))
            (redis/wcar conn (redis/expire session-key (:expiration this))))
          (read-handler data))))))

(defn write-redis-session
  "Write a session to a Redis store."
  [this old-session-key data]
  (let [conn (:redis-conn this)
        session-key (or old-session-key (new-session-key (:prefix this)))
        expiri (:expiration this)]
    (let [write-handler (:write-handler this)]
      (if expiri
        (redis/wcar conn (redis/setex session-key expiri (write-handler data)))
        (redis/wcar conn (redis/set session-key (write-handler data)))))
    session-key))

(defn delete-redis-session
  "Delete a session in a Redis store."
  [this session-key]
  (redis/wcar (:redis-conn this) (redis/del session-key))
  nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocol Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord RedisStore [redis-conn prefix expiration reset-on-read read-handler write-handler])

(def store-behaviour {:read-session read-redis-session
                      :write-session write-redis-session
                      :delete-session delete-redis-session})

(extend RedisStore api/SessionStore store-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn redis-store
  "Creates a redis-backed session storage engine."
  ([redis-conn]
   (redis-store redis-conn {}))
  ([redis-conn {:keys [prefix expire-secs reset-on-read read-handler write-handler]
                :or {prefix "session"
                     read-handler identity
                     write-handler identity
                     reset-on-read false}}]
   (->RedisStore redis-conn prefix expire-secs reset-on-read read-handler write-handler)))
