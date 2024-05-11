(ns app.filestore.domain
  (:require
   [taoensso.nippy :as nippy]
   [medley.core :as m]
   [app.schemas :as s]
   [com.yetanalytics.squuid :as sq]
   [tick.core :as t]))

(def FileStoreEntity
  (s/schema
   [:map {:name :app.entity/filestore.file}
    [:filestore.file/file-id :uuid]
    [:filestore.file/atime ::s/inst]
    [:filestore.file/mtime ::s/inst]
    [:filestore.file/ctime ::s/inst]
    [:filestore.file/hash :string]
    [:filestore.file/size :int]
    [:filestore.file/mime-type {:optional true} :string]
    [:filestore.file/file-name :string]]))

(def ImageEntity
  (s/schema
   [:map {:name :app.entity/filestore.image}
    [:image/image-id :uuid]
    [:image/source-file s/DatomicRefOrTempid]
    [:image/width :int]
    [:image/height :int]
    [:image/filter-spec {:optional true} :string]
    #_[:image/renditions {:optional true} [:sequential ImageEntity]]]))

(defn txs-new-file [tempid file-name mime-type size hash]
  (assert hash "hash must be provided")
  (assert (string? hash) "hash must be a string")
  (assert size "size must be provided")
  (assert file-name "file-name must be provided")
  (let [stime (t/inst)]
    [(-> {:db/id tempid
          :filestore.file/file-id (sq/generate-squuid)
          :filestore.file/file-name file-name
          :filestore.file/atime stime
          :filestore.file/mtime stime
          :filestore.file/ctime stime
          :filestore.file/hash hash
          :filestore.file/size size}
         (m/assoc-some :filestore.file/mime-type mime-type))]))

(defn txs-new-image [tempid file-tempid width height]
  [{:db/id tempid
    :image/image-id (sq/generate-squuid)
    :image/source-file file-tempid
    :image/width width
    :image/height height}])

(defn encode-filter-spec [filter-spec]
  (when filter-spec
    (nippy/freeze filter-spec)))

(defn decode-filter-spec [filter-spec]
  (when filter-spec
    (nippy/thaw filter-spec)))

(defn txs-new-rendition [tempid {:keys [image-id] :as parent-image} file-tempid width height filter-spec]
  [[:db/add [:image/image-id image-id] :image/renditions tempid]
   (-> {:db/id tempid
        :image/image-id (sq/generate-squuid)
        :image/source-file file-tempid
        :image/width width
        :image/height height}
       (m/assoc-some :image/filter-spec (encode-filter-spec filter-spec)))])

(defn db->file [ent]
  ent)

(defn file->db [ent]
  ent)

(defn db->image [ent]
  (-> ent
      (m/update-existing :image/source-file db->file)
      (m/update-existing :image/filter-spec decode-filter-spec)
      (m/update-existing :image/renditions #(map db->image %))))

(defn image->db [ent]
  (-> ent
      (m/update-existing :image/source-file file->db)
      (m/update-existing :image/filter-spec encode-filter-spec)
      (m/update-existing :image/renditions #(map image->db %))))
