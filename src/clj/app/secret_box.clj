(ns app.secret-box
  (:require
   [buddy.core.codecs :as codecs]
   [taoensso.nippy :as nippy]
   [com.yetanalytics.squuid :as sq]))

(defn bytes->b64u
  "Convert byte array into a URL safe base64 encoded string"
  [bytes]
  (-> bytes
      (codecs/bytes->b64u)
      (codecs/bytes->str)))

(defn b64u->bytes
  "Convert a URL safe base64 encoded string into a byte array" [str]
  (-> str
      (codecs/str->bytes)
      (codecs/b64u->bytes)))

(defn encrypt-bytes
  "Warning: this uses the :cached password form from nippy. Be sure you know what that means.
  source: http://ptaoussanis.github.io/nippy/taoensso.nippy.encryption.html#var-aes128-gcm-encryptor"
  [plaintext password]
  (nippy/freeze plaintext {:password [:cached password]}))

(defn encrypt
  "Warning: this uses the :cached password form from nippy. Be sure you know what that means.
  source: http://ptaoussanis.github.io/nippy/taoensso.nippy.encryption.html#var-aes128-gcm-encryptor"
  [plaintext password]
  (bytes->b64u
   (encrypt-bytes plaintext password)))

(defn decrypt-bytes [ciphertext password]
  (nippy/thaw ciphertext {:password [:cached password]}))

(defn decrypt [ciphertext password]
  (decrypt-bytes (b64u->bytes ciphertext) password))

(comment
  ;; Usage

  (encrypt "hello world" "hunter2")
  ;; => "TlBZDokS_z1NfL6Z4T45dipuFQ28AC4nJ_JtnWqPtA3FrBJy2gby2bBCSiT9"

  (decrypt "TlBZDokS_z1NfL6Z4T45dipuFQ28AC4nJ_JtnWqPtA3FrBJy2gby2bBCSiT9" "hunter2")
  ;; => "hello world"

  (encrypt {:member-id "7a0affc7-9436-4667-b57a-f622e8fb82e4"
            :uuid (sq/generate-squuid)} "hunter2")
  (-> {:member-id "7a0affc7-9436-4667-b57a-f622e8fb82e4"
       :uuid (sq/generate-squuid)}
      (encrypt "hunter2")
      (decrypt "hunter2")) ;; rcf
  )