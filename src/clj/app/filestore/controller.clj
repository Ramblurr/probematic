(ns app.filestore.controller
  (:require
   [app.filestore :as filestore]
   [app.util :as util]
   [app.queries :as q]
   [app.datomic :as d]
   [app.filestore.domain :as domain]))

"
We rarely want to add a file to the store without also adding some reference to it in another identity.
So here we provide functions to store the content and generate datoms for use in a transaction
"

#_(defn store-file!
    "Stores the file in the content store, returns :file-tempid and :tx-data datoms for the filestore entity.
  If the file is already stored, it will not be stored again, but the tx-data will still be returned.
  If the tx-data is not transacted, then the content might be garbage collected at some point."
    [{:keys [filestore] :as sys} file-name file-source mime-type]
    (let [file-block (filestore/put! filestore file-source)
          tempid (d/tempid)
          txs (domain/txs-new-file tempid file-name mime-type file-block)]
      {:file-tempid tempid
       :tx-data txs}))

(defn store-image!
  "Stores the image in the content store, returns :image-tempid, :file-tempid, and :tx-data datoms for the filestore and image entities.
  If the image is already stored, it will not be stored again, but the tx-data will still be returned.
  If the tx-data is not transacted, then the content might be garbage collected at some point.
  "
  [{:keys [filestore] :as req} {:keys [file-name data mime-type]}]
  (assert filestore "filestore service required")
  (assert file-name "file-name required")
  (let [{:keys [size hash width height] :as prepared} (filestore/prepare-image data)
        image-tempid (d/tempid)
        file-tempid (d/tempid)
        file-txs (domain/txs-new-file file-tempid file-name mime-type size hash)
        image-txs (domain/txs-new-image image-tempid file-tempid width height)]
    (filestore/put! filestore prepared)
    {:image-tempid image-tempid
     :file-tempid file-tempid
     :tx-data (concat file-txs image-txs)}))

(defn load-image [{:keys [db filestore] :as req} image-id]
  (let [{:image/keys [source-file]} (q/retrieve-image db (util/ensure-uuid! image-id))
        {:filestore.file/keys [size hash mime-type file-name mtime]} source-file
        stream (filestore/load filestore hash)]
    {:mime-type mime-type
     :file-name file-name
     :etag hash
     :last-modified mtime
     :size size
     :stream stream}))
