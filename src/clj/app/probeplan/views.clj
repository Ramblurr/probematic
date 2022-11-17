(ns app.probeplan.views
  (:require
   [app.util :as util]
   [app.urls :as url]
   [app.ui :as ui]
   [app.probeplan.controller :as controller]
   [ctmx.response :as response]
   [app.icons :as icon]
   [ctmx.core :as ctmx]
   [tick.core :as t]
   [ctmx.rt :as rt]
   [medley.core :as m]
   [app.queries :as q]
   [app.i18n :as i18n]))

(ctmx/defcomponent ^:endpoint probeplan-index-page [{:keys [db] :as req}]
  [:div "hello!"])
