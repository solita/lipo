(ns lipo.meta-model
  "Meta fields that can be used in any document."
  (:require [clojure.spec.alpha :as s])
  (:import (java.util Date)))

;; When was this document saved written (time at node when it submitted the tx)
(s/def :meta/created inst?)

;; Who saved this document (references user :crux.db/id)
(s/def :meta/creator string?)

;; When the document was last edited
(s/def :meta/modified inst?)

;; Who
(s/def :meta/modifier string?)


(defn modification-meta
  [user]
  {:meta/modifier user
   :meta/modified (Date.)})

(defn creation-meta
  [user]
  {:meta/creator user
   :meta/created (Date.)})

(defn merge-creation-meta
  "Add meta fields of creation to document."
  [user doc]
  (merge
   doc
   {:meta/creator user
    :meta/created (Date.)}))

(defn merge-modification-meta
  "Add meta fields of modification to a document"
  [user doc]
  (merge
    doc
    {:meta/modifier user
     :meta/modified (Date.)}))
