(ns lipo.meta-model
  "Meta fields that can be used in any document."
  (:require [clojure.spec.alpha :as s])
  (:import (java.util Date)))

;; When was this document saved written (time at node when it submitted the tx)
(s/def :meta/created inst?)

;; Who saved this document (references user :xt/id)
(s/def :meta/creator string?)

;; When the document was last edited
(s/def :meta/modified inst?)

;; Who
(s/def :meta/modifier string?)

(defn user-id-ref
  "Return reference to user that is saved.
  User :xt/id values are maps of {:user/id \"someuser\"}."
  [user]
  {:pre [(some? (:user/id user))]}
  (select-keys user [:user/id]))

(defn modification-meta
  [user]
  {:meta/modifier (user-id-ref user)
   :meta/modified (Date.)})

(defn creation-meta
  [user]
  {:meta/creator (user-id-ref user)
   :meta/created (Date.)})

(defn merge-creation-meta
  "Add meta fields of creation to document."
  [user doc]
  (merge
   doc
   {:meta/creator (user-id-ref user)
    :meta/created (Date.)}))

(defn merge-modification-meta
  "Add meta fields of modification to a document"
  [user doc]
  (merge
    doc
    {:meta/modifier (user-id-ref user)
     :meta/modified (Date.)}))
