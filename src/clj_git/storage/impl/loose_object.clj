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

(defmethod storage-api/initialize! :git-bare-lo-store [{:keys [repository-path] :as repo-config}]
  (let [object-store (str repository-path (java.io.File/separator) "objects")
        heads (str repository-path (java.io.File/separator) "refs" (java.io.File/separator) "heads")
        config (str repository-path (java.io.File/separator) "config")
        head (str repository-path (java.io.File/separator) "HEAD")]))

(defmethod storage-api/get-object! :git-bare-lo-store [repo object-id]
  )

(defmethod storage-api/put-object! :git-bare-lo-store [repo object]
  )
