(ns clj-git.example
  "This file demonstrates simple usages of this library. The first
  example uses in-memory storage and the second one uses Git loose
  object store.

  Both examples should print the following output:
  Value of key foo =  {:a 1, :b 2}
  Value of key foo after update =  {:x 1.0, :y 2.0}
  All keys after updates =  (bar foo)"
  (:require [clj-git.core :as tursas]
            [clj-git.storage.api :as storage-api]))

(defn make-sha1-map-repo-config []
  {:repository/hash-implementation :sha1
   :repository/storage-engine :in-memory-map
   :repository/timezone "+0300"})

(defn make-sha1-git-repo-config [repository-path]
  {:repository/hash-implementation :sha1
   :repository/storage-engine :git-bare-lo-store
   :repository/timezone "+0300"
   :repository/path repository-path})

(defn example-in-memory []
  (let [repo-config (make-sha1-map-repo-config)
        repo (storage-api/initialize! repo-config)]
    ;; Add key "foo" with value {:a 1 :b 2}
    (tursas/commit repo "master" "foo" {:a 1 :b 2})
    (println "Value of key foo = " (tursas/get-value repo "master" "foo"))
    (tursas/commit repo "master" "bar" {:name "thing" :value 42})
    ;; Update key "foo" with value {:x 1.0 :y 2.0}
    (tursas/commit repo "master" "foo" {:x 1.0 :y 2.0})
    (println "Value of key foo after update = " (tursas/get-value repo "master" "foo"))
    (println "All keys after updates = " (tursas/get-keys repo "master"))))

(defn example-loose-object-store [path]
  (let [repo-config (make-sha1-git-repo-config path)
        repo (storage-api/initialize! repo-config)]
    ;; Add key "foo" with value {:a 1 :b 2}
    (tursas/commit repo "master" "foo" {:a 1 :b 2})
    (println "Value of key foo = " (tursas/get-value repo "master" "foo"))
    (tursas/commit repo "master" "bar" {:name "thing" :value 42})
    ;; Update key "foo" with value {:x 1.0 :y 2.0}
    (tursas/commit repo "master" "foo" {:x 1.0 :y 2.0})
    (println "Value of key foo after update = " (tursas/get-value repo "master" "foo"))
    (println "All keys after updates = " (tursas/get-keys repo "master"))))
