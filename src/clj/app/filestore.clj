(ns app.filestore
  (:require
   [app.filestore.image :as im]
   [blocks.core :as block]
   [blocks.store.file :as blocks.store.file]
   [com.stuartsierra.component :as component]
   [multiformats.hash :as mhash]))

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

(defn put-sync!
  "Stores the file in the filestore.
  prepared-source is the result of calling prepare on the source."
  [filestore prepared-source]
  @(put! filestore prepared-source))

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

(defn prepare-image!
  "Strips metadata from the image (in place!) and returns a map with the hash, size, width, height, format, and mime-type."
  [file]
  (im/strip-metadata-in-place! {:input {:path file}})
  (let [{:keys [width height format mime-type] :as info} (im/identify-detailed file)
        {:keys [id size] :as block} (block/read! file)]
    {:hash (mhash/hex id)
     :block block
     :format format
     :mime-type mime-type
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
