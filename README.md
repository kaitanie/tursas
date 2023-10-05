# Tursas a versioned key value store with extensible storage system

This repository contains a simple versioned key value store library
implemented in Clojure. The library is loosely modeled after
[Irmin](https://irmin.org), a distributed database built on the same
principles as Git implemented in OCaml.

## Features

Core high level functions for interacting with the
repository. Currently we can:

1. Initialize a repository using different storage engines
2. Commit new values and updates to existing ones
3. Get the most recent value by key
4. List all keys

Future developments might include: removing values from the most
recent revision, history listing, branching and merging. Another
limitation of the current implementation is that all blobs (values)
are in the root tree. If support for trees that contain trees is
added, we can support hierarchical keys.

## Usage

A full example how to use the library can be found in the
[`tursas.example`
namespace](https://github.com/kaitanie/tursas/blob/master/src/tursas/example.clj).

```clojure
;; Commit a new value to the repository
(tursas/commit repo "master" "foo" {:a 1 :b 2})

;; Get value of a key from the repository
(tursas/get-value repo "master" "foo")
;; Returns {:a 1 :b 2}

;; List all keys
(tursas/get-keys repo "master")
;; Returns ("foo")
```

An example screenshot of history accumulated in a Git loose object
repository after committing a few updates: ![An example screenshot of
the Git loose object repository history.](./tursas.png)

## Extensible storage

Storage API is defined using multimethods in namespace
[tursas.storage.api](https://github.com/kaitanie/tursas/blob/master/src/tursas/storage/api.clj).
Various storage engines can be added by making suitable implementation
of the multimethods in this API namespace. In this repository there are two
implementations: an in-memory store using Clojure data structures and
a Git bare repository using loose object format. The implementation
can be found in the impl subdirectory.

### In memory store

This namespace contains an in-memory implementation of the repository
storage engine. It uses an STM ref that contains the mutable object
store. The store contains a map for Git objects and another map
structure for storing refs (such as branches).

This storage implementation uses the same Git object serialization
based hashing as the Git loose object store so their object hashes
should be identical. The values themselves are stored as plain old
Clojure data structures without serializing them to byte arrays. This
allows the repository structure to be inspected a bit more easily.

### Git loose object store

Implements object store using Git loose objects in the bare repository
format. In the bare format there is only the Git object store. The
files, or in the case of this packages, keys and values are not
checked out.

In the loose object format the repository objects are stored in files
under the objects directory. The objects are first stored in a format
that contains a header describing object type and content length, then
a NULL byte and the object contents after that. The actual parsing
part of the Git object formats is implemented in the tursas.hash-utils
namespace.

The hashes are calculated using the serialized format of the Git
objects. After hashing, the object contents are compressed using the
deflate algorithm and stored under the objects directory in
subdirectories whose names are the first two characters of the content
hash and file names consist of the rest of the hash.

The loose object format bare repository created using this storage
engine is compatible with the Git command line tools and at least some
history visualization tools. If the repository grows too large Git
will automagically upgrade the format to use pack files which are not
yet supported by this version of the engine.
