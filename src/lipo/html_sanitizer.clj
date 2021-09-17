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

   ;; Allow styling
   (.addAttributes ":all" (s "style"))

   ;; Allow iframes (for some video embeds in content)
   (.addTags (s "iframe"))
   (.addAttributes "iframe" (s "src" "frameborder" "width" "height"
                               "allowfullscreen" "webkitallowfullscreen" "mozallowfullscreen"))))

(defn sanitize [input-html]
  (Jsoup/clean input-html safelist))
