(ns app.rand-human-id
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def animals (-> (io/resource "animals.txt") slurp str/split-lines))
(def adjectives (-> (io/resource "adjectives.txt") slurp str/split-lines))

(defn human-id []
  (str (rand-int 1000)
       " "
       (rand-nth adjectives)
       " "
       (rand-nth animals)))

(comment
  (human-id)
  ;;
  )
