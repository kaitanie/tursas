(ns clj-git.hash-utils
  "Utility functions for hashing objects and parsing Git file
  formats. The hashes are calculated from data stored in the same
  format that Git uses because this allows us to produce the same
  hashes as Git."
  (:require [clojure.string :as s])
  (:import [org.apache.commons.codec.binary Hex]
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

(defmulti hash-it
  "Interface for hash implementations. Currently mainly the SHA1 case is
  supported."
  (fn [repository _bytes]
    (:repository/hash-implementation repository)))

(defmethod hash-it :sha1 [_repository bytes]
  (bytes->hex (DigestUtils/sha1 bytes)))

(defmethod hash-it :sha512 [_repository bytes]
  (bytes->hex (DigestUtils/sha512 bytes)))

(defmulti hash-length-bytes
  (fn [repository]
    (:repository/hash-implementation repository)))

(defmethod hash-length-bytes :sha1 [_repository]
  20)

(defmethod hash-length-bytes :sha512 [_repository]
  64)

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

(defn pop-tree-entry [hash-length bytes]
  (let [variable-length-block (byte-array (take-while not-null? bytes))
        remaining-entry-bytes (byte-array (rest (drop-while not-null? bytes)))
        hash-block (byte-array (take hash-length remaining-entry-bytes))
        rest-of-bytes (byte-array (drop hash-length remaining-entry-bytes))
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

(defn parse-object [repository bytes]
  (let [header-bytes (byte-array (take-while not-null? bytes))
        header (parse-header header-bytes)
        payload-bytes (byte-array (rest (drop-while not-null? bytes)))]
    (if (= (count payload-bytes) (:payload-length header))
      {:header header
       :hash (hash-it repository bytes)
       :payload (parse-payload header payload-bytes)}
      (throw (ex-info "Payload length does not match the length given in the object header."
                      {:payload-length (count payload-bytes)
                       :expected-length (:payload-length header)})))))
