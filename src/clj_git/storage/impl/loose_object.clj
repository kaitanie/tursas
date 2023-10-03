(ns clj-git.storage.impl.loose-object
  (:require [clj-git.storage.api :as storage-api]
            [clojure.string :as s]
            [clojure.java.io :as io]))

(def default-git-config
  (s/join "\n"
          ["[core]"
           "    repositoryformatversion = 0"
	   "    filemode = true"
	   "    bare = true"]))

(defmethod storage-api/test-mm :git-loose-object [ctx value]
  "Loose object stuff called!")


(defmethod storage-api/get-ref-revision! :git-bare-lo-store [repo ref-type ref-name]
  )

(defmethod storage-api/update-ref-revision! :git-bare-lo-store [repo ref-type ref-name ref-value]
  )

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
        config (io/file (str repository-path (java.io.File/separator) "config"))
        head (io/file (str repository-path (java.io.File/separator) "HEAD"))]
    (make-file object-store true)
    (make-file heads false)
    (make-file config false default-git-config)
    (make-file head false)))

(defmethod storage-api/get-object! :git-bare-lo-store [repo object-id]
  )

(defmethod storage-api/put-object! :git-bare-lo-store [repo object]
  )
