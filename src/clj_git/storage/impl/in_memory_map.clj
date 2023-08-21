(ns clj-git.storage.impl.in-memory-map
  (:require [clj-git.storage.api :as storage-api]
            [clj-git.hash-utils :as hash-utils]))

(defmethod storage-api/test-mm :in-memory-map [ctx value]
  "In memory map implementation called.")

(defmethod storage-api/initialize! :in-memory-map [repo-config]
  (assoc repo-config
         :repository {:objects (ref {})
                      :branches [{"master" nil}]}))

(defmethod storage-api/get-object! :in-memory-map [repo object-id]
  (let [objects @(get-in repo [:repository :objects])]
    (if-let [result (get objects object-id)]
      result
      (throw (ex-info "Object not found in repository"
                      {:object-id object-id})))))

(defmethod storage-api/put-object! :in-memory-map [repo object]
  (let [hash (hash-utils/hash-it repo (hash-utils/serialize-payload :git object))
        objects-ref (get-in repo [:repository :objects])
        object-payload-with-header (select-keys object [:header :payload])]
    (dosync (alter objects-ref assoc hash object-payload-with-header))
    hash))
