(ns clj-git.core
  (:require [clojure.java.io :as io])
  (:import [java.util.zip GZIPInputStream GZIPOutputStream DeflaterInputStream InflaterInputStream]
           [org.apache.commons.codec.binary Hex]
           [org.apache.commons.codec.digest DigestUtils]))

(defn str->bytes
  "Convert string to byte array."
  ([^String s]
   (str->bytes s "UTF-8"))
  ([^String s, ^String encoding]
   (.getBytes s encoding)))

(defn bytes->str
  "Convert byte array to String."
  ([^bytes data]
   (bytes->str data "UTF-8"))
  ([^bytes data, ^String encoding]
   (String. data encoding)))

(defn bytes->hex
  "Convert a byte array to hex encoded string."
  [^bytes data]
  (Hex/encodeHexString data))

(defn hex->bytes
  "Convert hexadecimal encoded string to bytes array."
  [^String data]
  (Hex/decodeHex (.toCharArray data)))

(defn stream-bytes [is]
  (let [baos (java.io.ByteArrayOutputStream.)]
    (io/copy is baos)
    (.toByteArray baos)))

(defn test-deflater-input [file]
  (with-open [in (java.util.zip.DeflaterInputStream. (clojure.java.io/input-stream file))]
    (stream-bytes in)))

(defn test-inflater-input [file]
  (with-open [in (InflaterInputStream. (clojure.java.io/input-stream file))]
    (stream-bytes in)))

(defn interpret-chars [bytes]
  (map (fn [code]
         {:code code
          :char (char code)})
       bytes))

(defn not-separator? [separator-byte byte]
  (not= separator-byte byte))

(defn not-null? [byte]
  (not-separator? 0 byte))

(defn not-space? [byte]
  (not-separator? 32 byte))

(defn pop-header [bytes]
  (let [header-bytes (byte-array (take-while not-null? bytes))
        payload-bytes (byte-array (rest (drop-while not-null? bytes)))]
    {:header header-bytes
     :payload payload-bytes}))

(defn print-chars [bytes]
  (apply str (map :code (interpret-chars bytes))))

(defn hash-it [bytes]
  (DigestUtils/sha1 bytes))

(def test-file "/home/mael/tmp/git/.git/objects/c6/16faf03af2fa58e38bf6a6438f4d4e683e8384")

(def test-tree "/home/mael/tmp/git/.git/objects/0f/30f1dd43dc05c68f5d0852332d51b0d3d0a93b")

(def test-commit "/home/mael/tmp/git/.git/objects/18/ead1127c804802bf61e3899a19b4744258726e")
