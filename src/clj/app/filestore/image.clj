(ns app.filestore.image
  (:require
   [app.file-utils :as fs]
   [clojure.string :as str]
   [medley.core :as m]
   [mikera.image.core :as imgz])
  (:import
   [java.io File PrintWriter StringWriter]
   [java.util Properties]
   [org.im4java.core ConvertCmd IMOperation MogrifyCmd]
   [org.im4java.core IMOperation Info]))

(def thumbnail-modes #{:thumbnail-down})

(def supported-formats [{:format :gif :ext ".gif" :mime-type "image/gif" :im-tag "GIF"}
                        {:format :jpeg :ext ".jpeg" :mime-type "image/jpeg" :im-tag "JPEG"}
                        {:format :png :ext ".png" :mime-type "image/png" :im-tag "PNG"}
                        {:format :svg :ext ".svg" :mime-type "image/svg+xml" :im-tag "SVG"}
                        {:format :heic :ext ".heic" :mime-type "image/heic" :im-tag "HEIC"}
                        {:format :webp :ext ".webp" :mime-type "image/webp" :im-tag "WEBP"}])

(def format-lookup (m/index-by :format supported-formats))
(def ext-lookup (m/index-by :ext supported-formats))
(def mime-lookup (m/index-by :mime-type supported-formats))
(def im-tag-lookup (m/index-by :im-tag supported-formats))
(def format->extension #(-> % format-lookup :ext))
(def format->mime #(or (-> % format-lookup :mime-type) "application/octet-stream"))
(def im-tag->format #(-> % im-tag-lookup :format))

#_(defn mime->extension [mime-type]
    (case mime-type
      "application/pdf"    ".pdf"
      "application/zip"    ".zip"
      "image/apng"         ".apng"
      "image/avif"         ".avif"
      "image/gif"          ".gif"
      "image/jpeg"         ".jpg"
      "image/png"          ".png"
      "image/svg+xml"      ".svg"
      "image/webp"         ".webp"
      "text/plain"         ".txt"
      nil))

(defn- debug-cmd [cmd operation props]
  (let [sw (StringWriter.)
        pw (PrintWriter. sw)]
    (.createScript cmd pw operation (Properties.))
    (.toString sw)))

(defn- prepare-input [{:keys [path stream]}]
  (if path
    [path nil]
    (let [tmp (fs/tempfile)]
      (with-open [os (java.io.FileOutputStream. tmp)]
        (org.apache.commons.io.IOUtils/copy stream os))
      [(.getPath ^File tmp) tmp])))

(defn- generic-process
  [{:keys [input format operation] :as params}]
  (let [[path f] (prepare-input input)
        _ (assert path "input path required")
        _ (assert format "output format required")
        ext    (format->extension format)
        tmp    (fs/tempfile :prefix "snorga." :suffix ext)]

    #_(tap> (debug-cmd (ConvertCmd.) operation (into-array (map str [path tmp]))))
    (doto (ConvertCmd.)
      (.run operation (into-array (map str [path tmp]))))

    (when f
      (fs/delete-if-exists f))

    (assoc params
           :ext ext
           :format format
           :mime-type  (format->mime format)
           :size   (fs/size tmp)
           :out-file   tmp)))

(defn process-thumbnail-down
  "Create a thumbnail of the image, scaling down to fit within the specified dimensions preserving the aspect ratio, will not upscale."
  [{:keys [quality width height] :as params}]
  (assert (and width height) "width and height required")
  (assert quality "quality required")
  (let [op (doto (IMOperation.)
             (.addImage)
             (.autoOrient)
             (.strip)
             (.thumbnail ^Integer (int width) ^Integer (int height) ">")
             (.quality (double quality))
             (.addImage))]
    (generic-process (assoc params :operation op))))

(defn strip-metadata-in-place!
  "Strip all metadata from the image in place."
  [{:keys [input]}]
  (assert (:path input) "In place operations require a path on disk, not a stream")
  (let [op (doto (IMOperation.)
             (.autoOrient)
             (.strip)
             (.addImage))]

    #_(tap> [:path (:path input) :cmd (debug-cmd (MogrifyCmd.) op (into-array (map str [(:path input)])))])
    (doto (MogrifyCmd.)
      (.run op (into-array (map str [(:path input)]))))))

(defn process-thumbnail [{:keys [thumbnail-mode] :as params}]
  (condp = thumbnail-mode
    :thumbnail-down (process-thumbnail-down params)
    nil))

(defn- format-> [f]
  ;; imagemagick's identify in basic mode will return a format string like "JPEG"
  ;; but in verbose mode it will return a string like "JPEG (Joint Photographic Experts Group JFIF format)"
  ;; this function attempts to parse both
  (if (str/includes? f "(")
    (str/trim (first (str/split f #"\(")))
    f))

(defn- info-> [^Info info]
  (let [props (enumeration-seq (.getPropertyNames info))
        format (im-tag->format (format-> (.getProperty info "Format")))
        all-info (into {}
                       (for [prop props]
                         [prop (.getProperty info prop)]))]
    (-> all-info
        (assoc
         :format format
         :mime-type (or (.getProperty info "Mime type") (format->mime format))
         :width  (.getPageWidth info)
         :height (.getPageHeight info)))))

(defn identify
  ([path]
   (-> (str path)
       (Info. true)
       (info->))))

(defn identify-detailed
  ([path]
   (-> (str path)
       (Info. false)
       (info->))))

(comment
  (process-thumbnail-down {:input {:path "resources/public/img/tuba-robot-boat-1000.jpg"}
                           :quality 70
                           :width 10
                           :height 10
                           :format :jpeg})

  (process-thumbnail {:thumbnail-mode :thumbnail-down
                      :input {:path "resources/public/img/tuba-robot-boat-1000.jpg"}
                      :quality 70
                      :width 10
                      :height 10
                      :format :jpeg})
  (identify-detailed "resources/public/img/tuba-robot-boat-1000.jpg")
  (identify "/home/ramblurr/downloads/Test/IMG_3635.HEIC")
  (identify-detailed "/home/ramblurr/downloads/Test/test-stripped.heic")

  ;; rcf
  ;;
  )
