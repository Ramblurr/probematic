(ns app.probeplan.domain
  (:require [app.schemas :as s]))

(def probeplan-versions #{:probeplan.version/classic})

(def probeplan-classic-emphases #{:probeplan.emphasis/intensive :probeplan.emphasis/none})
(def probeplan-classic-default-emphasis :probeplan.emphasis/none)

(def str->play-emphasis (zipmap (map name probeplan-classic-emphases) probeplan-classic-emphases))

(defn probeplan-version? [v]
  (contains? probeplan-versions v))

(defn probeplan-classic-emphases? [v]
  (contains? probeplan-classic-emphases v))

(defn probeplan-classic-ordered-song? [v]
  (and (= 3 (count v))
       (int? (second v))
       (probeplan-classic-emphases? v)))

(def ProbeplanClassicEntity
  (s/schema
   [:map {:name :app.entity/probeplan-classic}
    [:probeplan/gig ::s/datomic-ref]
    [:probeplan/version (s/enum-from probeplan-versions)]
    [:probeplan.classic/ordered-songs [:sequential [:tuple ::s/datomic-ref :int (s/enum-from probeplan-classic-emphases)]]]]))
