(ns clj-git.storage.impl.loose-object
  (:require [clj-git.storage.api :as storage-api]
            [clj-git.hash-utils :as hash-utils]
            [clojure.string :as s]
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

(defmethod storage-api/test-mm :git-loose-object [ctx value]
  "Loose object stuff called!")

(defn make-path [path-segments]
  (s/join (java.io.File/separator) path-segments))

(defmethod storage-api/get-ref-revision! :git-bare-lo-store [repo ref-type ref-name]
  (cond (= ref-type :heads) (let [ref-file-path (make-path [(:repository/path repo) "refs" (name ref-type) ref-name])
                                  ref-file (io/file ref-file-path)]
                              (if (.exists ref-file)
                                (let [commit-id (slurp ref-file)]
                                  (if (seq commit-id)
                                    commit-id
                                    nil))
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
  "Stream bytes from the input stream into a byte array."
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
        object-store (io/file (str repository-path (java.io.File/separator) "objects"))
        heads (io/file (str repository-path (java.io.File/separator) "refs" (java.io.File/separator) "heads"))
        master (io/file (make-path [repository-path "refs" "heads" "master"]))
        config (io/file (str repository-path (java.io.File/separator) "config"))
        head (io/file (str repository-path (java.io.File/separator) "HEAD"))]
    (make-file object-store true)
    (make-file heads true)
    (make-file master false "")
    (make-file config false default-git-config)
    (make-file head false default-git-head)
    repo-config))

(defmethod storage-api/get-object! :git-bare-lo-store [repo object-id]
  (if (> (count object-id) 2)
    (let [prefix (subs object-id 0 2)
          filename (subs object-id 2)
          repository-path (:repository/path repo)
          object-path (str repository-path (java.io.File/separator) "objects" (java.io.File/separator) prefix (java.io.File/separator) filename)
          object-file (io/file object-path)]
      (if (.exists object-file)
        (hash-utils/parse-object repo (inflate-file object-file))
        (throw (ex-info "Object not found"
                        {:object-id object-id}))))
    nil))

(defmethod storage-api/put-object! :git-bare-lo-store [repo object]
  (let [serialized-object (hash-utils/serialize-payload :git object)
        hash (hash-utils/hash-it repo serialized-object)
        prefix (subs hash 0 2)
        file (subs hash 2)
        file-bytes (hash-utils/serialize-payload :git object)
        repository-path (:repository/path repo)
        filename (str repository-path (java.io.File/separator) "objects" (java.io.File/separator) prefix (java.io.File/separator) file)]
    (io/make-parents (io/file filename))
    (deflate-file filename file-bytes)
    hash))
