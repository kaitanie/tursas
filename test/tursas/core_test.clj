(ns tursas.core-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [tursas.core :as tursas]
            [tursas.storage.api :as storage-api]))

(def repo-config {:repository/hash-implementation :sha1
                  :repository/storage-engine :in-memory-map
                  :repository/timezone "+0300"})

(deftest test-in-memory-repository
  (let [repo (storage-api/initialize! repo-config)]
    (testing "Insert key foo"
      (tursas/commit repo "master" "foo" 42)
      (is (= 42 (tursas/get-value repo "master" "foo"))))
    (testing "Insert key bar"
      (tursas/commit repo "master" "bar" {:a 1 :b 2})
      (is (= {:a 1 :b 2} (tursas/get-value repo "master" "bar"))))
    (testing "Update value of key foo"
      (tursas/commit repo "master" "foo" {})
      (is (= {} (tursas/get-value repo "master" "foo"))))
    (testing "Check all keys"
      (is (= '("foo" "bar") (tursas/get-keys repo "master"))))))
