(ns app.secret-box
  (:import
   org.bouncycastle.crypto.generators.Argon2BytesGenerator
   org.bouncycastle.crypto.params.Argon2Parameters
   org.bouncycastle.crypto.params.Argon2Parameters$Builder)
  (:require
   [taoensso.nippy :as nippy]
   [buddy.core.crypto :as crypto]
   [buddy.core.codecs :as codecs]
   [buddy.core.nonce :as nonce]))

;; Deps
;;  buddy/buddy-core   {:mvn/version "1.10.413"}
;;  com.taoensso/nippy {:mvn/version "3.2.0"}

(def ^:no-doc ^:static
  +parameters+
  {:argon2id   {:memory     65536
                :iterations 2
                :salt-bytes 16
                :hash-bytes 32}
   :aes256-gcm {:iv-bytes 12}})

(defn- key-stretch-wth-argon2id  [weak-text-key salt]
  (let [memory      (get-in +parameters+ [:argon2id :memory]) ;; KiB
        iterations  (get-in +parameters+ [:argon2id :iterations])
        salt-bytes  (get-in +parameters+ [:argon2id :salt-bytes])
        hash-bytes  (get-in +parameters+ [:argon2id :hash-bytes])
        salt        (codecs/to-bytes (or salt (nonce/random-bytes salt-bytes)))
        parallelism 1
        params      (-> (Argon2Parameters$Builder. Argon2Parameters/ARGON2_id)
                        (.withSalt salt)
                        (.withMemoryAsKB memory)
                        (.withIterations iterations)
                        (.withParallelism parallelism)
                        (.build))
        generator   (Argon2BytesGenerator.)
        hash        (byte-array hash-bytes)]
    (.init generator params)
    (.generateBytes generator ^bytes (codecs/to-bytes weak-text-key) hash)
    {:alg         :argon2id
     :memory      memory
     :iterations  iterations
     :parallelism parallelism
     :secret-key  hash
     :salt        salt}))

(defn- bytes->b64 [bytes]
  (-> bytes
      (codecs/bytes->b64u)
      (codecs/bytes->str)))

(defn- b64->bytes [str]
  (-> str
      (codecs/str->bytes)
      (codecs/b64u->bytes)))

(defn encrypt-bytes
  [plaintext passphrase]
  (let [salt-bytes           (get-in +parameters+ [:argon2id :salt-bytes])
        iv-bytes             (get-in +parameters+ [:aes256-gcm :iv-bytes])
        salt                 (nonce/random-bytes salt-bytes)
        {:keys [secret-key]} (key-stretch-wth-argon2id passphrase (codecs/to-bytes salt))
        iv                   (nonce/random-bytes iv-bytes)
        encoded-plaintext    (nippy/freeze plaintext)
        ciphertext           (crypto/encrypt encoded-plaintext secret-key iv {:algorithm :aes256-gcm})]
    (byte-array (mapcat seq [salt iv ciphertext]))))

(defn encrypt
  [plaintext passphrase]
  (bytes->b64
   (encrypt-bytes plaintext passphrase)))

(defn decrypt [secret passphrase]
  (let [salt-bytes           (get-in +parameters+ [:argon2id :salt-bytes])
        iv-bytes             (get-in +parameters+ [:aes256-gcm :iv-bytes])
        decoded              (b64->bytes secret)
        salt                 (byte-array (take salt-bytes decoded))
        iv                   (byte-array (take iv-bytes (drop salt-bytes decoded)))
        ciphertext           (byte-array (drop (+ salt-bytes iv-bytes) decoded))
        {:keys [secret-key]} (key-stretch-wth-argon2id passphrase (codecs/to-bytes salt))]
    (-> ciphertext
        (crypto/decrypt  secret-key iv {:algorithm :aes256-gcm})
        (nippy/thaw))))

(comment
  ;; Usage

  (encrypt "hello world" "hunter2")
  ;; => "FWemUkExvsBiiP0YxO653XAp1GLRfz1-dToNOFUc7WKN7VBZVI8r4Qks85Y6mMWO0df_Mj1jbSjmtprXLg"
  ;;
  (-> "hello world"
      (encrypt "hunter2")
      (decrypt "hunter2"))
  ;; => "hello world"

;
  )
