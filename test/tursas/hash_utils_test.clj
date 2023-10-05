(ns tursas.hash-utils-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [tursas.hash-utils :as hash-utils]
            [tursas.objects :as objects]
            [tursas.storage.impl.loose-object :as git-store]))

(defonce repository {:repository/hash-implementation :sha1})

(deftest test-git-object-parsing
  (testing "Parse blob"
    (let [blob-file "test/data/blob-c616faf03af2fa58e38bf6a6438f4d4e683e8384"
          blob-data (git-store/inflate-file (io/file blob-file))
          blob (hash-utils/parse-object repository blob-data)
          expected-blob {:header {:object-type :blob, :payload-length 13},
                         :hash "c616faf03af2fa58e38bf6a6438f4d4e683e8384",
                         :payload (byte-array [72, 111, 105, 32, 109, 97, 97, 105, 108, 109, 97, 33, 10])}]
      (is (= (:header expected-blob) (:header blob)) "Correct blob header")
      (is (= (:hash expected-blob) (:hash blob)) "Hashes match")
      (is (= (hash-utils/bytes->str (:payload expected-blob))
             (hash-utils/bytes->str (:payload blob)))
          "Content matches")))
  (testing "Parse tree"
    (let [tree-file "test/data/tree-02e66200476c086a035445776bf7d062d85c8f59"
          tree-data (git-store/inflate-file (io/file tree-file))
          tree (hash-utils/parse-object repository tree-data)
          expected-tree {:header {:object-type :tree, :payload-length 88},
                         :hash "02e66200476c086a035445776bf7d062d85c8f59",
                         :payload {"README with spaces.txt" #:tree-entry{:permissions "100644",
                                                                         :name "README with spaces.txt",
                                                                         :hash "c616faf03af2fa58e38bf6a6438f4d4e683e8384"}
                                   "README.txt" #:tree-entry{:permissions "100644",
                                                             :name "README.txt",
                                                             :hash "c616faf03af2fa58e38bf6a6438f4d4e683e8384"}}}]
      (is (= expected-tree tree))))
  (testing "Parse commit without parents (initial commit)"
    (let [commit-file "test/data/initial-commit-fd307d636f824d27f996afd2bda812b10139170f"
          commit-data (git-store/inflate-file (io/file commit-file))
          commit (hash-utils/parse-object repository commit-data)
          expected-commit {:header {:object-type :commit, :payload-length 216},
                           :hash "fd307d636f824d27f996afd2bda812b10139170f",
                           :payload #:commit{:tree "02e66200476c086a035445776bf7d062d85c8f59",
                                             :parents '(),
                                             :author #:author{:role :author,
                                                              :name "Pekka Kaitaniemi <pekka.kaitaniemi@gmail.com>",
                                                              :timestamp "1696429465",
                                                              :timezone "+0300"},
                                             :committer #:author{:role :committer,
                                                                 :name "Pekka Kaitaniemi <pekka.kaitaniemi@gmail.com>",
                                                                 :timestamp "1696429465",
                                                                 :timezone "+0300"},
                                             :message "Initialize the repository"}}]
      (is (= expected-commit commit))))
  (testing "Parse commit with one parent"
    (let [commit-file "test/data/commit-22a4b1833ceaddf77f479844ac1624b1ded17134"
          commit-data (git-store/inflate-file (io/file commit-file))
          commit (hash-utils/parse-object repository commit-data)
          expected-commit {:header {:object-type :commit, :payload-length 262},
                           :hash "22a4b1833ceaddf77f479844ac1624b1ded17134",
                           :payload #:commit{:tree "7e9bf5223d78cca8f3404ef9ef5dd5a4d139a14c",
                                             :parents '("fd307d636f824d27f996afd2bda812b10139170f"),
                                             :author #:author{:role :author,
                                                              :name "Pekka Kaitaniemi <pekka.kaitaniemi@gmail.com>",
                                                              :timestamp "1696437923",
                                                              :timezone "+0300"},
                                             :committer #:author{:role :committer,
                                                                 :name "Pekka Kaitaniemi <pekka.kaitaniemi@gmail.com>",
                                                                 :timestamp "1696437923",
                                                                 :timezone "+0300"},
                                             :message "Update in master branch"}}]
      (is (= expected-commit commit))))
  (testing "Parse merge commit"
    (let [commit-file "test/data/merge-commit-3c30d3c34ae6ccde38a54bf4f95eca9de613c221"
          commit-data (git-store/inflate-file (io/file commit-file))
          commit (hash-utils/parse-object repository commit-data)
          expected-commit {:header {:object-type :commit, :payload-length 316},
                           :hash "3c30d3c34ae6ccde38a54bf4f95eca9de613c221",
                           :payload #:commit{:tree "19bcd5a1e5a0dc0bebf12411c2cf672c0eb48451",
                                             :parents '("22a4b1833ceaddf77f479844ac1624b1ded17134"
                                                        "b6dac9fb1067de6ff1a8a940ef8696e0ed9db292"),
                                             :author #:author{:role :author,
                                                              :name "Pekka Kaitaniemi <pekka.kaitaniemi@gmail.com>",
                                                              :timestamp "1696437948",
                                                              :timezone "+0300"},
                                             :committer #:author{:role :committer,
                                                                 :name "Pekka Kaitaniemi <pekka.kaitaniemi@gmail.com>",
                                                                 :timestamp "1696437948",
                                                                 :timezone "+0300"},
                                             :message "Merge branch 'another-branch'"}}]
      (is (= expected-commit commit)))))

(deftest test-git-object-parsing-serialization
  (testing "Serialize/deserialize blobs"
    (let [blob (objects/make-blob {:name "foobar" :number 42})
          serialized-blob (hash-utils/serialize-payload :git blob)
          deserialized-blob (hash-utils/parse-object {:repository/hash-implementation :sha1} serialized-blob)]
      (is blob deserialized-blob)))
  (testing "Serialize/deserialize trees"
    (let [tree-entry (objects/make-tree-entry-blob "foo" "3c30d3c34ae6ccde38a54bf4f95eca9de613c221")
          tree (update-in (objects/make-tree) [:payload] assoc "foo" tree-entry)
          serialized-tree (hash-utils/serialize-payload :git tree)
          deserialized-tree (hash-utils/parse-object {:repository/hash-implementation :sha1} serialized-tree)]
      (is tree deserialized-tree)))
  (testing "Serialize/deserialize commits"
    (let [commit (objects/make-commit "pk <>" "Updateing stuff" "+0300" '() "3c30d3c34ae6ccde38a54bf4f95eca9de613c221")
          serialized-commit (hash-utils/serialize-payload :git commit)
          deserialized-commit (hash-utils/parse-object {:repository/hash-implementation :sha1} serialized-commit)]
      (is commit deserialized-commit))))
