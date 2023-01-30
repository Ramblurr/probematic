(ns app.routes.pedestal-reitit
  (:require [reitit.interceptor :as reitit.interceptor]
            [io.pedestal.interceptor])
  (:import [io.pedestal.interceptor Interceptor]))

; It appears as though Reitit.pedestal doesn't support pedestal interceptors of
; the io.pedestal.interceptor.Interceptor form, as opposed to hashmap or
; function interceptors.

; https://github.com/metosin/reitit/issues/330
(extend-protocol reitit.interceptor/IntoInterceptor
  Interceptor
  (into-interceptor [this data opts]
    (reitit.interceptor/into-interceptor (into {} this) data opts)))

(defn nop "Does nothing. It is here to prevent tools from cleaning up an unused ns" [])
