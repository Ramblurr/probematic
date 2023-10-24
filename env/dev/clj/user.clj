(ns user
  (:require
   [clojure.tools.namespace.repl :as repl]
   [portal.api :as inspect]))

(defn dev
  "Load and switch to the 'dev' namespace."
  []
  (require 'dev)
  (in-ns 'dev)
  :loaded)

(repl/disable-reload! *ns*)
(inspect/open {:theme :portal.colors/gruvbox})
(add-tap portal.api/submit)

(comment

  ;; Clear all values in the portal inspector window
  (inspect/clear)

  ;; Close the inspector
  (inspect/close)) ;; End of rich comment block

(comment
  (dev)

  (require '[portal.api :as p])
  (p/open {:theme :portal.colors/gruvbox})
  (dev)


  ;;
  )
