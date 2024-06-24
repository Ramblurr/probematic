(ns app.insurance.excel
  (:require
   [dk.ative.docjure.spreadsheet :as excel]
   [tarayo.core :as tarayo]
   [tick.core :as t])

  (:import
   [java.io ByteArrayOutputStream]
   [java.util Locale]))

(def START-ROW 10) ;; 0 indexed
(def STUCKPREIS-COL 7)
(def TOTAL-COL 8)
(def SHEET-NAME "Inventar")

(defn coverage->row [{:instrument.coverage/keys [value insurer-id item-count types] :as coverage}]
  (let [nachzeit? (some #(= "Nachzeit im Auto" (:insurance.coverage.type/name %)) types)
        proberaum? (some #(= "Proberaum" (:insurance.coverage.type/name %)) types)
        item-count (or item-count 1)
        {:instrument/keys [category description serial-number build-year name model make owner images-share-url]} (:instrument.coverage/instrument coverage)]
    [item-count
     name
     make
     model
     serial-number
     build-year
     (str (:instrument.category/name category) "; " description)
     value
     (* item-count value)
     (if nachzeit? "x" "")
     (if proberaum? "x" "")
     ""                                 ; klavier transport
     ""                                 ; wert zuwachs
     (:member/name owner)
     insurer-id
     (or images-share-url "")]))

(defn get-cell-style-at [sheet row col]
  (let [r (nth  (excel/row-seq sheet) row)
        c (nth (excel/cell-seq r) col)]
    (.getCellStyle c)))

(defn clear-rows! [sheet]
  (let [rows  (drop START-ROW (excel/row-seq sheet))]
    ;; Remove all the rows under the template header
    (doseq [row rows]
      (excel/remove-row! sheet row))))

(defn set-item-styles! [row normal-style stuckpreis-style total-style]
  (doseq [cell (excel/cell-seq row)]
    (when cell
        ;; (.setLocked (.getCellStyle cell) false)
      (when (>=  (.getRowIndex cell) START-ROW)
        (.setCellStyle cell normal-style))
      (when (and (>=  (.getRowIndex cell) START-ROW)
                 (= STUCKPREIS-COL (.getColumnIndex cell)))
        (.setCellStyle cell stuckpreis-style))
      (when (and (>=  (.getRowIndex cell) START-ROW)
                 (= TOTAL-COL (.getColumnIndex cell)))
        (.setCellStyle cell total-style)))))

(defn add-blank-rows! [sheet count style]
  (dotimes [i count]
    (let [row (excel/add-row! sheet (repeat 15 ""))]
      (excel/set-row-style! row style))))

(defn add-label-row! [sheet label style]
  (let [row (excel/add-row! sheet ["" "" label "" "" "" "" "" ""])]
    (excel/set-row-style! row style)))

(defn -add-instruments! [sheet normal-style stuckpreis-style total-style label-style title items]
  (when (seq items)
    (add-label-row! sheet title label-style)
    (doseq [item items]
      (let [row (excel/add-row! sheet item)]
        (set-item-styles! row normal-style stuckpreis-style total-style)))))

(defn- generate-excel [fname output-fname new-items changed-items removed-items]
  (let [wb               (excel/load-workbook-from-resource fname)
        sheet            (excel/select-sheet SHEET-NAME wb)
        total-style      (get-cell-style-at sheet 4 TOTAL-COL)
        stuckpreis-style (doto (get-cell-style-at sheet START-ROW STUCKPREIS-COL)
                           (.setLocked false))
        label-style      (doto  (excel/create-cell-style! wb {:font {:size 14 :bold true} :wrap false})
                           (.setLocked false))
        normal-style     (doto
                          (excel/create-cell-style! wb {})
                           (.setLocked false)
                           (.setFillBackgroundColor (excel/color-index :white)))
        date-today       (t/format (t/formatter "dd MMM yyyy" Locale/GERMAN) (t/today))
        add-instruments! (partial -add-instruments! sheet normal-style stuckpreis-style total-style label-style)]
    (clear-rows! sheet)
    (add-instruments! (format "Neue Instrumente: (Ab %s)" date-today) new-items)
    (add-blank-rows! sheet 3 normal-style)
    (add-instruments! (format "Änderungen: (Ab %s)" date-today) changed-items)
    (add-blank-rows! sheet 3 normal-style)
    (add-instruments! (format "Entfernung: (Ab %s)" date-today) removed-items)
    (excel/save-workbook! output-fname wb)))

(defn generate-excel-changeset! [{:insurance.policy/keys [covered-instruments] :as policy} output]
  (let [changed (into [] (filter #(= :instrument.coverage.change/changed (:instrument.coverage/change %)) covered-instruments))
        removed (into [] (filter #(= :instrument.coverage.change/removed (:instrument.coverage/change %)) covered-instruments))
        new (into [] (filter #(= :instrument.coverage.change/new (:instrument.coverage/change %)) covered-instruments))]

    (generate-excel  "insurance-changes-template.xls" output
                     (map coverage->row new)
                     (map coverage->row changed)
                     (map coverage->row removed)))
  output)

(defn send-email! [policy smtp-params from to subject body attachment-filename]
  (with-open [conn (tarayo/connect smtp-params)]
    (let [output-stream (ByteArrayOutputStream.)]
      (generate-excel-changeset! policy output-stream)
      (tarayo/send! conn {:from    from
                          :to      to
                          :subject subject
                          :body    [{:content body}
                                    {:content      (.toByteArray output-stream)
                                     :content-type "application/vnd.ms-excel" :filename attachment-filename}]}))))

(comment

  (do
    (require '[integrant.repl.state :as state])
    (require '[datomic.client.api :as datomic])
    (def conn (-> state/system :app.ig/datomic-db :conn))
    (def db (datomic/db conn))
    (let [{:insurance.policy/keys [covered-instruments]} (q/retrieve-policy db #uuid "018bf625-1a68-8327-b386-fbb9e80dc987")
          changed (into [] (filter #(= :instrument.coverage.change/changed (:instrument.coverage/change %)) covered-instruments))
          removed (into [] (filter #(= :instrument.coverage.change/removed (:instrument.coverage/change %)) covered-instruments))
          new (into [] (filter #(= :instrument.coverage.change/new (:instrument.coverage/change %)) covered-instruments))]

      (generate-excel  "insurance-changes-template.xls" "insurance-changes-template-changed.xls"
                       (map coverage->row new)
                       (map coverage->row changed)
                       (map coverage->row removed)))) ;; rcf
  )
