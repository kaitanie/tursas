(ns tursas.core
  "Core high level functions for interacting with the repository.

  Example usages can be found in the file example.clj."
  (:require [tursas.storage.api :as storage-api]
            [tursas.storage.impl.loose-object]
            [tursas.storage.impl.in-memory-map]
            [tursas.objects :as objects]))

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

(defn tree-assoc-blob [tree key blob-id]
  (let [tree-entry (objects/make-tree-entry-blob key blob-id)]
    (update-in tree [:payload] assoc (:tree-entry/name tree-entry) tree-entry)))

(defn get-commit-root-tree [repository branch-name]
  (let [commit-id (storage-api/get-ref-revision! repository :heads branch-name)]
    (if (and commit-id
             (seq commit-id))
      (let [commit (storage-api/get-object! repository commit-id)
            tree-id (get-in commit [:payload :commit/tree])]
        (storage-api/get-object! repository tree-id))
      (objects/make-tree))))

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
        commit (objects/make-commit "test-author <>" message (:repository/timezone repository) parent-commit-ids tree-id)
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
