(ns lipo.html-sanitizer
  "Use OWASP HTML sanitizer to cleanup user input of potentially
  dangerous content."
  (:import (org.owasp.html HtmlPolicyBuilder
                           Sanitizers)))

(def policy (reduce #(.and %1 %2)
                    [Sanitizers/BLOCKS
                     Sanitizers/FORMATTING
                     Sanitizers/LINKS
                     Sanitizers/IMAGES
                     Sanitizers/STYLES
                     Sanitizers/TABLES]))

(defn sanitize [input-html]
  (.sanitize policy input-html))
