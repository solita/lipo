(ns lipo.html-sanitizer
  "Use OWASP HTML sanitizer to cleanup user input of potentially
  dangerous content."
  (:import (org.owasp.html HtmlPolicyBuilder
                           Sanitizers))
  (:require [clojure.string :as str]))

(def policy (reduce #(.and %1 %2)
                    [Sanitizers/BLOCKS
                     Sanitizers/FORMATTING
                     Sanitizers/LINKS
                     Sanitizers/IMAGES
                     Sanitizers/STYLES
                     Sanitizers/TABLES]))

(defn- sanitize-with-policy [html]
  (.sanitize policy html))

(defn sanitize [input-html]
  (-> input-html
      sanitize-with-policy

      ;; Sanitizer is too strict, we DO want to allow {{...edn...}} in content
      ;; that is read in as maps.
      (str/replace "{<!-- -->{" "{{" )

      ;; also allow real doublequote
      (str/replace "&#34;" "\"")))
