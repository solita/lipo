(ns lipo.content-model
  "Specs that define the content model."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(def root-page-id
  "The :xt/id value of the root page, all top level page will have this as :content/parent."
  "root")

(def content-types #{:news :memo :page})

(s/def :content/type content-types)

(def uri-path-pattern #"^(\/[\d\w\-_]+)+$")

(def path-pattern #"^[\d\w\-_]+$")

(s/def :content/path (s/and string? #(re-matches path-pattern %))) ; relative path in parent
(s/def :content/title (s/and string? #(not (str/blank? %))))
(s/def :content/body string?)
(s/def :content/excerpt string?) ; short excerpt text for content
(s/def :content/parent (s/or
                         :uuid
                         uuid?
                         :root                              ;; Only root page has the id of "root" others are UUID
                         #(= root-page-id %)))                          ; references :xt/id of other document


(s/def :content/keywords (s/coll-of string?))
(s/def :content/published inst?) ; publish date
(s/def :content/expires inst?) ; when content expires (not published any more)

(s/def :content/form-values (s/and
                              (s/keys
                                :req [:content/title :content/type]
                                :opt [:content/excerpt :content/body])
                              (fn [{:content/keys [path]
                                    id :xt/id}]
                                (or
                                  (= id root-page-id)
                                  (not (str/blank? path))))))
