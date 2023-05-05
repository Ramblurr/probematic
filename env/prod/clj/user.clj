(ns user
  (:require
   [portal.api :as inspect]))

(defn debug-in-prod []
  (inspect/open {:theme :portal.colors/gruvbox
                 :portal.launcher/host "0.0.0.0"
                 :portal.launcher/port  7001})
  (add-tap portal.api/submit))

(comment
  (require '[clojure.tools.logging :as log])
  (log/info "hello")

  (require '[integrant.repl.state :as state])

  (require '[app.dashboard.routes :as dashboard.routes])
  (dashboard.routes/routes)

  (keys state/system)
  ;;
  )
