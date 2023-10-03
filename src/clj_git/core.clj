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

(defn make-sha1-git-repo-config []
  {:repository/hash-implementation :sha1
   :repository/storage-engine :git-bare-lo-store
   :repository/path "/home/mael/tmp/t0repo"})

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

(defn get-commit-root-tree [repository branch-name]
  (let [commit-id (storage-api/get-ref-revision! repository :heads branch-name)]
    (if (and commit-id
             (seq commit-id))
      (let [commit (storage-api/get-object! repository commit-id)
            tree-id (get-in commit [:payload :commit/tree])]
        (storage-api/get-object! repository tree-id))
      (make-tree))))

(defn make-commit [author-name parent-commit-ids tree-id]
  {:header {:object-type :commit}
   :payload {:commit/author author-name
             :commit/committer author-name
             :commit/parents parent-commit-ids
             :commit/tree tree-id}})

(defn commit [repository branch-name key value]
  (let [tree (get-commit-root-tree repository branch-name)
        parent-commit-ids (if-let [commit-id (storage-api/get-ref-revision! repository :heads branch-name)]
                            [commit-id]
                            [])
        blob (make-blob value)
        blob-id (storage-api/put-object! repository blob)
        updated-tree (tree-assoc-blob tree key blob-id)
        tree-id (storage-api/put-object! repository updated-tree)
        commit (make-commit "test-author" parent-commit-ids tree-id)
        commit-id (storage-api/put-object! repository commit)]
    (storage-api/update-ref-revision! repository :heads branch-name commit-id)))

(defn get-value [repo branch-name key]
  (let [commit-id (storage-api/get-ref-revision! repo :heads branch-name)
        commit (storage-api/get-object! repo commit-id)
        commit-id (get-in commit [:payload :commit/tree])
        tree (storage-api/get-object! repo commit-id)
        tree-entry (get-in tree [:payload key])]
    (if tree-entry
      (-> (storage-api/get-object! repo (:tree-entry/hash tree-entry))
          :payload)
      (throw (ex-info "Key not found"
                      {:key key})))))

(defn get-keys [repo branch-name]
  (let [commit-id (storage-api/get-ref-revision! repo :heads branch-name)
        commit (storage-api/get-object! repo commit-id)
        commit-id (get-in commit [:payload :commit/tree])
        tree (storage-api/get-object! repo commit-id)
        tree-entries (get-in tree [:payload])]
    (keys tree-entries)))
