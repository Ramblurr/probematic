(ns user)

(defn dev
  "Load and switch to the 'dev' namespace."
  []
  (require 'dev)
  (in-ns 'dev)
  :loaded)

(comment
  (dev)

  (require '[portal.api :as p])
  (p/open {:launcher :emacs})

  ;;
  )
