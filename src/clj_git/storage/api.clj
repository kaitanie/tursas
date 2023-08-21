(ns clj-git.storage.api)


(defmulti test-mm (fn [repo-config _value]
                    (:storage-engine repo-config)))

(defn dispatch-by-storage-engine [repo-config]
  (:repository/storage-engine repo-config))

(defmulti initialize! dispatch-by-storage-engine)

(defmulti get-object! (fn [repo _object-id]
                        (dispatch-by-storage-engine repo)))

(defmulti put-object! (fn [repo _object]
                        (dispatch-by-storage-engine repo)))

(defmulti commit! (fn [repo branch-name [objects]]))
