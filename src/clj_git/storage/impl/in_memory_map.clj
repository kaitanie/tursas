(ns clj-git.storage.impl.in-memory-map
  "This namespace contains an in-memory implementation of the repository
  storage engine. It uses an STM ref that contains the mutable object
  store. The store contains a map for Git objects and another map
  structure for storing refs (such as branches).

  This storage implementation uses the same Git object serialization
  based hashing as the Git loose object store so the object hashes
  should be identical. The values themselves are stored as plain old
  Clojure data structures without serializing them to byte
  arrays. This allows the repository structure to be inspected a bit
  more easily."
  (:require [clj-git.storage.api :as storage-api]
            [clj-git.objects :as objects]
            [clj-git.hash-utils :as hash-utils]))

(defn make-repository []
  {:objects {}
   :refs {:heads {"master" nil}
          :tags {}}})

(defn add-object [repository hash object]
  (update-in repository [:objects] assoc hash object))

(defn update-ref [repository ref-type ref-name ref-value]
  (update-in repository [:refs ref-type] assoc ref-name ref-value))

(defn get-object [repository hash]
  (get-in repository [:objects hash]))

(defn update-branch-ref [repository branch-name hash]
  (if (get-object repository hash)
    (update-in repository [:refs :heads] assoc branch-name hash)
    (throw (ex-info "Hash not found in object store"
                    {:hash hash}))))

(defmethod storage-api/initialize! :in-memory-map [repo-config]
  (assoc repo-config
         :repository (ref (make-repository))))

(defmethod storage-api/get-object! :in-memory-map [repo object-id]
  (let [repository @(get-in repo [:repository])]
    (if-let [result (get-object repository object-id)]
      result
      (throw (ex-info "Object not found in repository"
                      {:object-id object-id})))))

(defmethod storage-api/put-object! :in-memory-map [repo object]
  (let [hash (hash-utils/hash-it repo (hash-utils/serialize-payload :git object))
        repository-ref (get-in repo [:repository])
        object-payload-with-header (objects/validate-object (select-keys object [:header :payload]))]
    (dosync
     (alter repository-ref add-object hash object-payload-with-header))
    hash))

(defmethod storage-api/get-ref-revision! :in-memory-map [repo ref-type ref-name]
  (let [repository-ref (get-in repo [:repository])]
    (get-in @repository-ref [:refs ref-type ref-name])))

(defmethod storage-api/update-ref-revision! :in-memory-map [repo ref-type ref-name ref-value]
  (let [repository-ref (get-in repo [:repository])]
    (cond (= ref-type :heads) (dosync
                               (alter repository-ref update-branch-ref ref-name ref-value))
          :else (throw (ex-info "Unknown ref-type" {:ref-type ref-type :ref-name ref-name})))))
