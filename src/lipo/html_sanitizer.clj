(ns lipo.html-sanitizer
  "Use Jsoup to cleanup user input of potentially dangerous content."
  (:import (org.jsoup Jsoup)
           (org.jsoup.safety Safelist)))


(defn- s [& strings]
  (into-array String strings))

(def ^:private safelist
  (->
   ;; Allow text and structural HTML
   (Safelist/relaxed)

   ;; Preserve relative links within the system (images, attachments or other pages)
   (.preserveRelativeLinks true)

   ;; Allow styling
   (.addAttributes ":all" (s "style" "class"))

   ;; Allow iframes (for some video embeds in content)
   (.addTags (s "iframe"))
   (.addAttributes "iframe" (s "src" "frameborder" "width" "height"
                               "allowfullscreen" "webkitallowfullscreen" "mozallowfullscreen"))))

(defn sanitize [input-html]
  ;; NOTE: must provide base URI for relative links to work, even though it
  ;; isn't used.
  (Jsoup/clean input-html "http://__BASEURI__" safelist))
