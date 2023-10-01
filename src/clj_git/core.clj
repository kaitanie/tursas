(ns clj-git.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as s]
            [clj-git.storage.api :as storage-api]
            [clj-git.storage.impl.loose-object]
            [clj-git.storage.impl.in-memory-map]
            [clj-git.hash-utils :as hash-utils])
  (:import [java.util.zip GZIPInputStream GZIPOutputStream DeflaterInputStream InflaterInputStream]
           [org.apache.commons.codec.binary Hex]
           [org.apache.commons.codec.digest DigestUtils]))


(defmulti hash-object (fn [_repository object]
                        (:object-type object)))

(defmethod hash-object :blob [repository object]
  (cond (bytes? object) (hash-utils/hash-it repository object)
        (string? object) (hash-utils/hash-it repository (hash-utils/str->bytes object))
        :else            (hash-utils/hash-it repository (hash-utils/str->bytes (str object)))))

(defmethod hash-object :tree [repository object]
  )

(defmethod hash-object :commit [repository object])


(defn make-sha1-map-repo-config []
  {:repository/hash-implementation :sha1
   :repository/storage-engine :in-memory-map})

(defn make-blob [m]
  {:header {:object-type :blob}
   :payload m})

(def default-key-permissions "100644")

(defn make-tree [key-blob-ids]
  (let [entries-with-perms (map (fn [key-blob]
                                  {:tree-entry/permissions default-key-permissions
                                   :tree-entry/name (:key key-blob)
                                   :tree-entry/hash (:hash key-blob)})
                                key-blob-ids)]
    {:header {:object-type :tree}
     :payload entries-with-perms}))

(defn get-commit-root-tree [repository branch-name]
  (let [commit-id (storage-api/get-ref-revision! repository :heads branch-name)]
    (if commit-id
      (let [commit (storage-api/get-object! repository commit-id)
            tree-id (:commit/tree-id commit)]
        (storage-api/get-object! repository tree-id))
      nil)))

(defn make-tree []
  {:header {:object-type :tree}
   :payload {}})

(defn make-tree-entry-blob [key blob-id]
  {:tree-entry/permissions default-key-permissions
   :tree-entry/name key
   :tree-entry/hash blob-id})

(defn tree-assoc-blob [tree key blob-id]
  (let [tree-entry (make-tree-entry-blob key blob-id)]
    (update-in tree [:payload] assoc (:tree-entry/name tree-entry) tree-entry)))

(defn add-key-value [repository key value]
  (let [blob (make-blob value)
        blob-id (storage-api/put-object! repository blob)]))
