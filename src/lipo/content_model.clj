(ns lipo.content-model
  "Specs that define the content model."
  (:require [clojure.spec.alpha :as s]))

(def content-types #{:news :memo :page})

(s/def :content/type content-types)

(def path-pattern #"^[\d\w\-_]+$")

(s/def :content/path (s/and string? #(re-matches path-pattern %))) ; relative path in parent
(s/def :content/title string?)
(s/def :content/body string?)
(s/def :content/excerpt string?) ; short excerpt text for content
(s/def :content/parent string?) ; references :crux.db/id of other document
(s/def :content/keywords (s/coll-of string?))
