(ns tursas.core
  "Core high level functions for interacting with the
  repository. Currently we can:
  1. Initialize a repository
  2. Commit new values and updates to existing ones
  3. Get the most recent value by key
  4. List all keys

  Future developments might include: removing values from the most
  recent revision, history listing, branching and merging. Another
  limitation of the current implementation is that all blobs (values)
  are in the root tree. If support for trees that contain trees is
  added, we can support hierarchical keys.

  Example usages can be found in the file example.clj."
  (:require [tursas.storage.api :as storage-api]
            [tursas.storage.impl.loose-object]
            [tursas.storage.impl.in-memory-map]
            [tursas.objects :as objects]))

(defn tree-assoc-blob
  "Add a new blob to an existing tree and return the modified tree."
  [tree key blob-id]
  (let [tree-entry (objects/make-tree-entry-blob key blob-id)]
    (update-in tree [:payload] assoc (:tree-entry/name tree-entry) tree-entry)))

(defn get-commit-root-tree
  "Get the root tree of the commit."
  [repository branch-name]
  (let [commit-id (storage-api/get-ref-revision! repository :heads branch-name)]
    (if (and commit-id
             (seq commit-id))
      (let [commit (storage-api/get-object! repository commit-id)
            tree-id (get-in commit [:payload :commit/tree])]
        (storage-api/get-object! repository tree-id))
      (objects/make-tree))))

(defn commit
  "Commit a new key value pair or an update to an existing value into
  the repository. This adds to the branch a new commit that refers to
  the previous commit as its parent."
  [repository branch-name key value]
  (let [tree (get-commit-root-tree repository branch-name)
        parent-commit-ids (if-let [commit-id (storage-api/get-ref-revision! repository :heads branch-name)]
                            [commit-id]
                            [])
        blob (objects/make-blob value)
        blob-id (storage-api/put-object! repository blob)
        updated-tree (tree-assoc-blob tree key blob-id)
        tree-id (storage-api/put-object! repository updated-tree)
        message (str "Update " key)
        commit (objects/make-commit "test-author <>" message (:repository/timezone repository) parent-commit-ids tree-id)
        commit-id (storage-api/put-object! repository commit)]
    (storage-api/update-ref-revision! repository :heads branch-name commit-id)))

(defn get-value
  "Get the value of the key from the repository. Currently it only
  fetches the most recent version of the value."
  [repo branch-name key]
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

(defn get-keys
  "Lists all keys found in the branch."
  [repo branch-name]
  (let [commit-id (storage-api/get-ref-revision! repo :heads branch-name)
        commit (storage-api/get-object! repo commit-id)
        commit-id (get-in commit [:payload :commit/tree])
        tree (storage-api/get-object! repo commit-id)
        tree-entries (get-in tree [:payload])]
    (keys tree-entries)))
