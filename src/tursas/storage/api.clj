(ns tursas.storage.api
  "Storage API defined using multimethods. Various storage engines can
  be added by making suitable implementation of these multimethods. In
  this repository there are two implementations: an in-memory store
  using Clojure data structures and a Git bare repository using loose
  object format. The implementation can be found in the impl
  subdirectory.")

(defn- dispatch-by-storage-engine [repo-config]
  (:repository/storage-engine repo-config))

(defmulti get-ref-revision!
  "Get the revision pointed to by a ref (such as a branch)."
  (fn [repo _ref-type _ref-name]
    (dispatch-by-storage-engine repo)))

(defmulti update-ref-revision!
  "Update a ref to a new value."
  (fn [repo _ref-type _ref-name _ref-value]
    (dispatch-by-storage-engine repo)))

(defmulti initialize!
  "Initialize a new empty repository."
  dispatch-by-storage-engine)

(defmulti get-object!
  "Get object by object ID from the repository."
  (fn [repo _object-id]
    (dispatch-by-storage-engine repo)))

(defmulti put-object!
  "Put object into the repository and return the object hash."
  (fn [repo _object]
    (dispatch-by-storage-engine repo)))
