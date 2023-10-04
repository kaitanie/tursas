(ns clj-git.storage.impl.loose-object
  "Implements object store using Git loose objects in the bare
  repository format. In the bare format there is only the Git object
  store. The files, or in the case of this packages, keys and values
  are not checked out.

  In the loose object format the repository objects are stored in
  files under the objects directory. The objects are first stored in a
  format that contains a header describing object type and content
  length, then a NULL byte and the object contents after that. The
  actual parsing part of the Git object formats is implemented in
  the clj-git.hash-utils namespace.

  The hashes are calculated using the serialized format of the Git
  objects. After hashing, the object contents are compressed using the
  deflate algorithm and stored under the objects directory in
  subdirectories whose names are the first two characters of the
  content hash and file names consist of the rest of the hash.

  The loose object format bare repository created using this storage
  engine is compatible with the Git command line tools and at least
  some history visualization tools. If the repository grows too large
  Git will automagically upgrade the format to use pack files which
  are not yet supported by this version of the engine."
  (:require [clj-git.storage.api :as storage-api]
            [clj-git.hash-utils :as hash-utils]
            [clj-git.objects :as objects]
            [clojure.string :as s]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import [java.util.zip InflaterInputStream DeflaterOutputStream]))

(def default-git-config
  (s/join "\n"
          ["[core]"
           "    repositoryformatversion = 0"
	   "    filemode = true"
	   "    bare = true"]))

(def default-git-head
  "ref: refs/heads/master")

(defn make-path [path-segments]
  (s/join (java.io.File/separator) path-segments))

(defmethod storage-api/get-ref-revision! :git-bare-lo-store [repo ref-type ref-name]
  (cond (= ref-type :heads) (let [ref-file-path (make-path [(:repository/path repo) "refs" (name ref-type) ref-name])
                                  ref-file (io/file ref-file-path)]
                              (if (.exists ref-file)
                                (let [commit-id (slurp ref-file)]
                                  (when (seq commit-id)
                                    commit-id))
                                (throw (ex-info "Reference not found"
                                                {:ref-type ref-type
                                                 :ref-name ref-name
                                                 :path ref-file-path}))))
        :else (throw (ex-info "Unknown ref-type"
                              {:ref-type ref-type}))))

(defmethod storage-api/update-ref-revision! :git-bare-lo-store [repo ref-type ref-name ref-value]
  (cond (= ref-type :heads) (let [ref-file-path (make-path [(:repository/path repo) "refs" (name ref-type) ref-name])
                                  ref-file (io/file ref-file-path)]
                              (if (.exists ref-file)
                                (spit ref-file ref-value)
                                (throw (ex-info "Reference not found"
                                                {:ref-type ref-type
                                                 :ref-name ref-name
                                                 :path ref-file-path}))))
        :else (throw (ex-info "Unknown ref-type"
                              {:ref-type ref-type}))))


(defn stream-input-bytes
  "Stream bytes from the input stream into a byte array."
  [input-stream]
  (let [byte-array-output-stream (java.io.ByteArrayOutputStream.)]
    (io/copy input-stream byte-array-output-stream)
    (.toByteArray byte-array-output-stream)))

(defn stream-output-bytes
  "Stream bytes from the input byte array into the output stream."
  [output-stream bytes]
  (let [byte-array-input-stream (java.io.ByteArrayInputStream. bytes)]
    (io/copy byte-array-input-stream output-stream)))

(defn inflate-file [file]
  (with-open [in (InflaterInputStream. (io/input-stream file))]
    (stream-input-bytes in)))

(defn deflate-file [file bytes]
  (with-open [out (DeflaterOutputStream. (io/output-stream file))]
    (stream-output-bytes out bytes)))

(defn make-file
  "Make a file or a directory, optionally files can be pre-filled with
  content."
  ([file directory? file-content]
   (when (not (.exists file))
     (io/make-parents file)
     (if directory?
       (.mkdir file)
       (spit file file-content))))
  ([file directory?]
   (make-file file directory? ""))
  ([file]
   (make-file file false "")))

(defmethod storage-api/initialize! :git-bare-lo-store [repo-config]
  (let [repository-path (:repository/path repo-config)
        object-store (io/file (make-path [repository-path "objects"]))
        heads (io/file (make-path [repository-path "refs" "heads"]))
        master (io/file (make-path [repository-path "refs" "heads" "master"]))
        config (io/file (make-path [repository-path "config"]))
        head (io/file (make-path [repository-path "HEAD"]))]
    (make-file object-store true)
    (make-file heads true)
    (make-file master false "")
    (make-file config false default-git-config)
    (make-file head false default-git-head)
    repo-config))

(defmethod storage-api/get-object! :git-bare-lo-store [repo object-id]
  (when (> (count object-id) 2)
    (let [prefix (subs object-id 0 2)
          filename (subs object-id 2)
          repository-path (:repository/path repo)
          object-path (make-path [repository-path "objects" prefix filename])
          object-file (io/file object-path)]
      (if (.exists object-file)
        (let [object (hash-utils/parse-object repo (inflate-file object-file))]
          (if (= (get-in object [:header :object-type]) :blob)
            (assoc object
                   :payload (edn/read-string (hash-utils/bytes->str (:payload object))))
            object))
        (throw (ex-info "Object not found"
                        {:object-id object-id}))))))

(defmethod storage-api/put-object! :git-bare-lo-store [repo object]
  (let [valid-object (objects/validate-object object)
        serialized-object (hash-utils/serialize-payload :git valid-object)
        hash (hash-utils/hash-it repo serialized-object)
        prefix (subs hash 0 2)
        file (subs hash 2)
        repository-path (:repository/path repo)
        filename (make-path [repository-path "objects" prefix file])]
    (io/make-parents (io/file filename))
    (deflate-file filename serialized-object)
    hash))
