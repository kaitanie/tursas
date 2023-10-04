(ns clj-git.core
  "Core high level functions for interacting with the repository.

  Example usages can be found in the file example.clj."
  (:require [clj-git.storage.api :as storage-api]
            [clj-git.storage.impl.loose-object]
            [clj-git.storage.impl.in-memory-map]))

(defn make-blob [m]
  {:header {:object-type :blob}
   :payload m})

(def default-key-permissions "100644")

;; (defn make-tree [key-blob-ids]
;;   (let [entries-with-perms (map (fn [key-blob]
;;                                   {:tree-entry/permissions default-key-permissions
;;                                    :tree-entry/name (:key key-blob)
;;                                    :tree-entry/hash (:hash key-blob)})
;;                                 key-blob-ids)]
;;     {:header {:object-type :tree}
;;      :payload entries-with-perms}))

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

(defn make-author [role name timestamp timezone]
  {:author/role role
   :author/name name
   :author/timestamp timestamp
   :author/timezone timezone})

(defn make-commit [author-name message timezone parent-commit-ids tree-id]
  (let [timestamp (str (long (/ (System/currentTimeMillis) 1000.0)))
        author (make-author :author author-name timestamp timezone)
        committer (make-author :committer author-name timestamp timezone)]
    {:header {:object-type :commit}
     :payload {:commit/author author
               :commit/committer committer
               :commit/parents parent-commit-ids
               :commit/message message
               :commit/tree tree-id}}))

(defn commit [repository branch-name key value]
  (let [tree (get-commit-root-tree repository branch-name)
        parent-commit-ids (if-let [commit-id (storage-api/get-ref-revision! repository :heads branch-name)]
                            [commit-id]
                            [])
        blob (make-blob value)
        blob-id (storage-api/put-object! repository blob)
        updated-tree (tree-assoc-blob tree key blob-id)
        tree-id (storage-api/put-object! repository updated-tree)
        message (str "Update " key)
        commit (make-commit "test-author <>" message (:repository/timezone repository) parent-commit-ids tree-id)
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
