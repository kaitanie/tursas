(ns clj-git.hash-utils
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as s])
  (:import [java.util.zip GZIPInputStream GZIPOutputStream DeflaterInputStream InflaterInputStream]
           [org.apache.commons.codec.binary Hex]
           [org.apache.commons.codec.digest DigestUtils]))

(defn hash-it-old [bytes]
  (DigestUtils/sha1 bytes))

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

(defn stream-bytes
  "Stream bytes from the input stream into a byte array."
  [input-stream]
  (let [byte-array-output-stream (java.io.ByteArrayOutputStream.)]
    (io/copy input-stream byte-array-output-stream)
    (.toByteArray byte-array-output-stream)))

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

(defn parse-header [header-bytes]
  (let [header (bytes->str header-bytes)
        [object-type object-length] (s/split header #" ")]
    {:object-type (keyword object-type)
     :payload-length (Long/parseLong object-length)}))

(defmulti parse-payload (fn [header _payload-bytes]
                          (:object-type header)))

(defmethod parse-payload :blob [_header payload-bytes]
  payload-bytes)

(defn pop-tree-entry [bytes]
  (let [variable-length-block (byte-array (take-while not-null? bytes))
        remaining-entry-bytes (byte-array (rest (drop-while not-null? bytes)))
        hash-block (byte-array (take 20 remaining-entry-bytes))
        rest-of-bytes (byte-array (drop 20 remaining-entry-bytes))
        [permissions & entry-name] (s/split (bytes->str variable-length-block) #" ")]
    {:first-entry {:tree-entry/permissions permissions
                   :tree-entry/name (s/join " " entry-name)
                   :tree-entry/hash (bytes->hex hash-block)}
     :rest rest-of-bytes}))

(defmethod parse-payload :tree [_header payload-bytes]
  (loop [remaining-bytes payload-bytes
         tree-entries {}]
    (if (empty? remaining-bytes)
      tree-entries
      (let [parse-result (pop-tree-entry remaining-bytes)
            tree-entry (:first-entry parse-result)]
        (recur (:rest parse-result) (assoc tree-entries (:tree-entry/name tree-entry) tree-entry))))))

(defn parse-author-line
  "Drop the first heading of the author "
  [author-line]
  (let [[role & author-rest] (s/split author-line #" ")
        [timezone timestamp & author-name-email-fields] (reverse author-rest)
        author-name-email (s/join " " (reverse author-name-email-fields))]
    {:author/role (keyword role)
     :author/name (s/join "" author-name-email)
     :author/timestamp timestamp
     :author/timezone timezone}))

(defn drop-line-prefix
  "Drops the line prefix separated with space from the rest of the line."
  [line]
  (when line
    (apply str (rest (drop-while (fn [char]
                                   (not (s/blank? (str char))))
                                 line)))))

(defmethod parse-payload :commit [_header payload-bytes]
  (let [get-all-fields-fn (fn [starts-with lines]
                            (filter (fn [line]
                                      (s/starts-with? line starts-with))
                                    lines))
        get-field-fn (fn [starts-with lines]
                       (first (get-all-fields-fn starts-with lines)))
        ;; get-field-fn (fn [starts-with lines]
        ;;                (first (filter (fn [line]
        ;;                                 (s/starts-with? line starts-with))
        ;;                               lines)))
        commit-str (bytes->str payload-bytes)
        commit-lines (s/split commit-str #"\n")
        tree-line (get-field-fn "tree" commit-lines)
        parent-lines (get-all-fields-fn "parent" commit-lines)
        author-line (get-field-fn "author" commit-lines)
        committer-line (get-field-fn "committer" commit-lines)
        message-lines (rest (drop-while (fn [line]
                                          (not (s/blank? line)))
                                        commit-lines))
        tree (drop-line-prefix tree-line)
        parents (map drop-line-prefix parent-lines)]
    {:commit/tree tree
     :commit/parents parents
     :commit/author (parse-author-line author-line)
     :commit/committer (parse-author-line committer-line)
     :commit/message (s/join "\n" message-lines)}))

(defmulti serialize-payload (fn [format object]
                              [format (get-in object [:header :object-type])]))

(defn author-line-to-string [author-line]
  (str (:author/name author-line) " " (:author/timestamp author-line) " " (:author/timezone author-line)))

(defn header-to-string [header]
  (let [object-type (name (:object-type header))
        object-length (:payload-length header)]
    (s/join " " [object-type (str object-length)])))

(defmethod serialize-payload [:git :blob] [_format object]
  (let [payload (:payload object)
        payload-bytes (if (bytes? payload)
                        payload
                        (str->bytes (str payload)))
        header (assoc (:header object)
                      :payload-length (count payload-bytes))
        header-str (header-to-string header)
        header-bytes (str->bytes header-str)]
    (byte-array (concat header-bytes (byte-array [0]) payload-bytes))))

(defmethod serialize-payload [:git :commit] [_format object]
  (let [payload (:payload object)
        parents (map (fn [parent-id]
                       (str "parent " parent-id))
                     (:commit/parents payload))
        commit-fields (filter (fn [elem]
                                (not (nil? elem)))
                              (concat
                               [(str "tree " (:commit/tree payload))]
                               parents
                               [(str "author " (author-line-to-string (:commit/author payload)))
                                (str "committer " (author-line-to-string (:commit/committer payload)))
                                ""
                                (:commit/message payload)
                                ""]))
        payload-str (s/join "\n" commit-fields)
        payload-bytes (str->bytes payload-str)
        payload-length (count payload-bytes)
        header (assoc (:header object)
                      :payload-length payload-length)
        header-bytes (str->bytes (header-to-string header))]
    (byte-array (concat header-bytes [0] payload-bytes))))

(defn tree-entry->str [entry]
  (let [variable-length-str (str (:tree-entry/permissions entry)
                                 " "
                                 (:tree-entry/name entry))
        variable-length-bytes (str->bytes variable-length-str)
        hash-bytes (hex->bytes (:tree-entry/hash entry))]
    (byte-array (concat variable-length-bytes
                        (byte-array [0])
                        hash-bytes))))

(defmethod serialize-payload [:git :tree] [_format object]
  (let [entries (sort-by :tree-entry/name (vals (:payload object)))
        tree-entry-rows (map tree-entry->str entries)
        tree-entry-bytes (byte-array (apply concat tree-entry-rows))
        header (assoc (:header object)
                      :payload-length (count tree-entry-bytes))
        header-bytes (str->bytes (header-to-string header))]
    (byte-array (concat header-bytes (byte-array [0]) tree-entry-bytes))))

(defn parse-object [bytes]
  (let [header-bytes (byte-array (take-while not-null? bytes))
        header (parse-header header-bytes)
        payload-bytes (byte-array (rest (drop-while not-null? bytes)))]
    (if (= (count payload-bytes) (:payload-length header))
      {:header header
       :hash (bytes->hex (hash-it-old bytes))
       :payload (parse-payload header payload-bytes)}
      (throw (ex-info "Payload length does not match the length given in the object header."
                      {:payload-length (count payload-bytes)
                       :expected-length (:payload-length header)})))))

(defn print-chars [bytes]
  (apply str (map :code (interpret-chars bytes))))

(def test-file "/home/mael/tmp/git/.git/objects/c6/16faf03af2fa58e38bf6a6438f4d4e683e8384")

(def test-tree "/home/mael/tmp/git/.git/objects/0f/30f1dd43dc05c68f5d0852332d51b0d3d0a93b")

(def test-commit "/home/mael/tmp/git/.git/objects/18/ead1127c804802bf61e3899a19b4744258726e")

(def test-commit2 "/home/mael/tmp/git/.git/objects/ac/9f899d42d92be285507197610334b2b05c445d")

(def laptop-test-commit1 "/home/mael/tmp/git/.git/objects/67/3f850c4ef897f72ab834071cdeb0a634249e65")

(def laptop-test-commit2 "/home/mael/tmp/git/.git/objects/29/a7fbdf082606a368a18dbb907d625ffa2eba8f")

(def laptop-test-file1 "/home/mael/tmp/git/.git/objects/fa/83e85e64d361e50c2013acfced94ebb0b9430d")

(def laptop-test-file2 "/home/mael/tmp/git/.git/objects/3b/888aa3cf2fa7907f155d5a808030a6d84f927b")

(def laptop-test-commit2-tree "/home/mael/tmp/git/.git/objects/e6/d1df1b93c2e77b1ff03e26f1ec714463584e20")

(def laptop-test-commit3-tree "/home/mael/tmp/git/.git/objects/8d/c5aec3f799aaab453059df779856478feb1d1b")

(def laptop-test-commit4-tree "/home/mael/tmp/git/.git/objects/cd/2a3c78b4eda1567c99dc88eb447d1720018cb9/")


;; High level interface

;; Functionality

;; - Initialize repository

;; - Store (add/update) key value pair

;; - List keys

;; - Get value by key

;; Low lever interface

;; Operate on blobs (values), trees (collection of keys) and commits (for representing history)

;; Operations:

;; - Initialize new repo

;; - Get blob by key (path)

;; - Commit collection of blobs (add/update) into a single commit

;; - Get revision history of the entire repo (allows for filtering based on key)


(defmulti hash-it (fn [repository bytes]
                    (:repository/hash-implementation repository)))

(defmethod hash-it :sha1 [_repository bytes]
  (bytes->hex (DigestUtils/sha1 bytes)))

(defmethod hash-it :sha512 [_repository bytes]
  (bytes->hex (DigestUtils/sha512 bytes)))
