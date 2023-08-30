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

(defn make-tree [key-blob-ids]
  (let [entries-with-perms (map (fn [key-blob]
                                  {:tree-entry/permissions "100644"
                                   :tree-entry/name (:key key-blob)
                                   :tree-entry/hash (:hash key-blob)})
                                key-blob-ids)]
    {:header {:object-type :tree}
     :payload entries-with-perms}))
