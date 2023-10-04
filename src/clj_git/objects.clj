(ns clj-git.objects
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.dev :as dev]
            [malli.experimental :as mx]))

(def example-header {:object-type :blob     ;; Required, value can be :blob, :tree or :commit
                     :payload-length 72     ;; Optional number of serialized bytes (inflated, loose object store)
                     })

(def example-object {:header {:object-type :tree, :payload-length 232},
                     :hash "cd2a3c78b4eda1567c99dc88eb447d1720018cb9",
                     :payload
                     [#:tree-entry{:permissions "100644",
                                   :name "README",
                                   :hash "fa83e85e64d361e50c2013acfced94ebb0b9430d"}
                      #:tree-entry{:permissions "100644",
                                   :name "README",
                                   :hash "476ab72e7034581d86f4bb0f705d9aabc8a7e5d1"}
                      #:tree-entry{:permissions "100644",
                                   :name "README_link",
                                   :hash "fa83e85e64d361e50c2013acfced94ebb0b9430d"}
                      #:tree-entry{:permissions "120000",
                                   :name "README_symlink",
                                   :hash "100b93820ade4c16225673b4ca62bb3ade63c313"}
                      #:tree-entry{:permissions "40000",
                                   :name "test",
                                   :hash "3fe8b9f491e172c8e36156008cdbce5a7c84384a"}
                      #:tree-entry{:permissions "120000",
                                   :name "test_symlink",
                                   :hash "30d74d258442c7c65512eafab474568dd706c430"}]} )

(def ObjectHeader
  [:map
   [:object-type keyword?]
   [:payload-length {:optional true} int?]])

(def ObjectPayloadBytes
  bytes?)

(def ObjectPayloadBlob
  any?)

(def TreeEntry
  [:map
   [:tree-entry/permissions string?]
   [:tree-entry/name string?]
   [:tree-entry/hash string?]])

(def Author
  [:map
   [:author/name string?]
   [:author/role string?]
   [:author/timestamp string?]
   [:author/timezone string?]])

(def ObjectPayloadCommit
  [:map
   [:commit/hash {:optional true} string?]
   [:commit/tree string?]
   [:commit/parents [:vector string?]]
   [:commit/author Author]
   [:commit/committer Author]])

(def ObjectPayloadTree
  [:map-of :string TreeEntry])

(def StorableObject
  [:map
   [:header ObjectHeader]
   [:payload [:or ObjectPayloadBlob ObjectPayloadTree ObjectPayloadCommit]]])

(def default-key-permissions "100644")

(defn make-blob [m]
  {:header {:object-type :blob}
   :payload m})

(defn make-tree []
  {:header {:object-type :tree}
   :payload {}})

(defn make-tree-entry-blob [key blob-id]
  {:tree-entry/permissions default-key-permissions
   :tree-entry/name key
   :tree-entry/hash blob-id})
