(ns clj-git.storage.impl.loose-object
  (:require [clj-git.storage.api :as storage-api]))

(defmethod storage-api/test-mm :git-loose-object [ctx value]
  "Loose object stuff called!")
