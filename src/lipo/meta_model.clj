(ns lipo.meta-model
  "Meta fields that can be used in any document."
  (:require [clojure.spec.alpha :as s])
  (:import (java.util Date)))

;; When was this document saved written (time at node when it submitted the tx)
(s/def :meta/at inst?)

;; Why saved this document (references user :crux.db/id)
(s/def :meta/by string?)

(defn merge-meta
  "Add meta fields to document."
  [user doc]
  (merge
   doc
   {:meta/by user
    :meta/at (Date.)}))
