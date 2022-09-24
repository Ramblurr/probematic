(ns app.routes.pedestal-reitit
  (:require [reitit.interceptor :as interceptor]
            [io.pedestal.interceptor])
  (:import [io.pedestal.interceptor Interceptor]))

; It appears as though Reitit.pedestal doesn't support pedestal interceptors of
; the io.pedestal.interceptor.Interceptor form, as opposed to hashmap or
; function interceptors.

; https://github.com/metosin/reitit/issues/330
(extend-protocol interceptor/IntoInterceptor
  Interceptor
  (into-interceptor [this data opts]
    (interceptor/into-interceptor (into {} this) data opts)))
