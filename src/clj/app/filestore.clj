(ns app.filestore
  (:import
   [java.io ByteArrayInputStream ByteArrayOutputStream])
  (:require
   [mikera.image.core :as imgz]
   [multiformats.hash :as mhash]
   [blocks.core :as block]
   [blocks.store.file :as blocks.store.file]
   [com.stuartsierra.component :as component]))

(defn start! [{:keys [store-path]}]
  (-> store-path
      (blocks.store.file/file-block-store)
      (component/start)))

(defn halt! [store]
  (component/stop store))

(defn put!
  "Stores the file in the filestore.
  prepared-source is the result of calling prepare on the source."
  [filestore prepared-source]
  (block/put! filestore (:block prepared-source)))

(defn get-block [filestore hash]
  @(block/get filestore hash))

(defn as-input-stream ^java.io.InputStream [b]
  (block/open b))

(defn load
  "Loads the block from the filestore and returns an input stream."
  [filestore hash]
  (as-input-stream (get-block filestore (mhash/parse hash))))

(defn prepare
  "Prepare the source for storage in the filestore.
  source may be an InputStream, Reader, File, byte[], char[], or String."
  [source]
  (let [{:keys [id size] :as block} (block/read! source)]
    {:hash (mhash/hex id)
     :block block
     :size size}))

(defn prepare-image [source]
  (let [img (imgz/load-image source)
        {:keys [id size] :as block} (block/read! source)
        width (imgz/width img)
        height (imgz/height img)]
    {:hash (mhash/hex id)
     :block block
     :size size
     :width width
     :height height}))

(comment
  (def store (start! {:store-path "/home/ramblurr/src/sno/probematic/data.filestore"}))
  (def hello (block/read! "hello world"))
  (str (:id hello))

  @(block/put! store hello)
  @(block/stat store (:id hello))
  (def hello2 (first (block/list-seq store)))

  ;; TODO how to load string hash
  @(block/get store (str (:id hello)))

  (mhash/hex (:id hello))
  (mhash/parse (mhash/hex (:id hello)))

  (slurp (block/open hello))

;;
  )
