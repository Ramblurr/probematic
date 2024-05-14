(ns app.util.zip
  (:require
   [app.file-utils :as fs]
   [clojure.java [io :as io]]
   [clojure.string :as s])
  (:import
   [java.io
    File
    FilterInputStream
    InputStream
    PipedInputStream
    PipedOutputStream]
   [java.nio.charset StandardCharsets]
   [java.text Normalizer Normalizer$Form]
   [java.util.zip ZipEntry ZipOutputStream]))

(defn temp-file-zip
  "Builds a zip into a temporary file with the given `content-fn`. Returns the file handle."
  [prefix suffix content-fn]
  (let [temp-file (fs/tempfile prefix suffix)]
    (with-open [zip (ZipOutputStream. (io/output-stream temp-file))]
      (content-fn zip)
      (.finish zip)
      (.flush zip)
      (.close zip))
    temp-file))

(defn de-accent
  "Replaces accent characters with base letters"
  [^String s]
  (when s (let [normalized (Normalizer/normalize s Normalizer$Form/NFD)]
            (clojure.string/replace normalized #"\p{InCombiningDiacriticalMarks}+" ""))))

(defn  encode-filename
  "Replaces all non-ascii chars and other that the allowed punctuation with dash.
   UTF-8 support would have to be browser specific, see http://greenbytes.de/tech/tc2231/"
  ^String [unencoded-filename]
  (when-let [de-accented (de-accent unencoded-filename)]
    (s/replace
     de-accented
     #"[^a-zA-Z0-9\.\-_ ]" "-")))

;; NOTE: I hate to have to use encode-filename here
;; in theory it should be possible to use utf-8 strings (like  wëird:user:înput:.jpeg)
;;  as zip entry file names, but the resulting zip files cannot be decompressed on linux:
;;  error:  cannot create instrument_w+?irduser+?nput.jpeg
;;          Invalid or incomplete multibyte or wide character
;;

(defn append-stream!
  "Appends the input stream `in` to the zip output stream `zip` with
  the name `file-name`"
  [^ZipOutputStream zip file-name in]
  (when in
    (.putNextEntry zip (ZipEntry. (encode-filename file-name)))
    (io/copy in zip)
    (.closeEntry zip))
  zip)

(defn open-and-append!
  "Calls and opens `content-thunk` and appends its contents to the
  `zip` output stream with the name `file-name`"
  [^ZipOutputStream zip file-name content-thunk]
  (with-open [in ^InputStream (content-thunk)]
    (append-stream! zip file-name in))
  ;; Flush after each attachment to ensure data flows into the output pipe
  (.flush zip)
  zip)

(defn temp-file-input-stream
  "File given as parameter will be deleted after the returned stream is closed."
  ^InputStream [^File file]
  {:pre [(instance? File file)]}
  (let [i (io/input-stream file)]
    (proxy [FilterInputStream] [i]
      (close []
        (let [^FilterInputStream this this]                 ; HACK just to give type hint to the magical `this`.
          (proxy-super close))
        (when (= (io/delete-file file :could-not) :could-not)
          (throw (ex-info "Could not delete temporary file: %s" (.getAbsolutePath file) {})))))))

(defn piped-zip-input-stream
  "Builds a zip input stream. The zip is built by running `content-fn`
  on the corresponding output stream in a future. Returns the input stream."
  ^InputStream [content-fn]
  (let [pos (PipedOutputStream.)
        ;; Use 16 MB pipe buffer
        is (PipedInputStream. pos 16777216)
        zip (ZipOutputStream. pos StandardCharsets/UTF_8)]
    (future
      ;; This runs in a separate thread so that the input stream can be returned immediately
      (try
        (content-fn zip)
        (.finish zip)
        (.flush zip)
        (catch Throwable t
          (throw (ex-info "Error occurred while generating ZIP output stream" {:msg (.getMessage t)} t)))
        (finally
          (.close zip)
          (.close pos))))
    is))

(defn- append-attachments-to-zip! [zip  attachments file-name-prefix]
  (doseq [{:keys [content file-name]} attachments]
    (when (and content file-name)
      (open-and-append! zip (str file-name-prefix "_" file-name) content))))

(comment

  (let [attachments [{:content (fn [] (io/input-stream "/home/ramblurr/downloads/Bela1(1).JPG"))
                      :file-name "Bela1(1).JPG"}
                     {:content (fn [] (io/input-stream "/home/ramblurr/downloads/Bela2(2).JPG"))
                      :file-name "Bela2(2).JPG"}]
        ;; t (temp-file-zip "attachments." ".zip.tmp")
        ]
    (io/copy
     (piped-zip-input-stream
      (fn [zip]
        (append-attachments-to-zip! zip  attachments  "instrument")))
     (io/output-stream "/home/ramblurr/downloads/test-zip-r/test-zip.zip"))

    #_(io/copy
       (temp-file-input-stream)
       (io/output-stream "/home/ramblurr/downloads/test-zip-r/test-zip.zip")))                                ;; rcf

  #_(let [out-f "/home/ramblurr/downloads/test-zip-r/test-zip.zip"
          zip (ZipOutputStream. (io/output-stream out-f))]
      (pack-folder! zip "/home/ramblurr/downloads/test-zip")
      (.finish zip)
      (.flush zip)
      (.close zip)
      out-f)

  ;;
  )
