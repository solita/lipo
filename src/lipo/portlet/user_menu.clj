(ns lipo.portlet.user-menu
  "User menu portlet. Shows currently logged in user, or link to login."
  (:require [lipo.portlet :as p]
            [re-html-template.core :refer [html]]
            [clojure.string :as str]))

(defn- md5 [s]
  (str/join
   (map #(format "%02x" %)
        (.digest
         (doto (java.security.MessageDigest/getInstance "MD5")
           (.update (.getBytes s "UTF-8")))))))

(defmethod p/render :user-menu [{{user :user} :request :as ctx} _]
  (def *u user)
  (if (seq user)
    (let [hash (or (some->> user :user/email str/lower-case md5) "")
          gravatar-url (format "https://www.gravatar.com/avatar/%s?size=32&d=mp" hash)
          initials (str/upper-case
                    (str (some-> user :user/given-name first)
                         (some-> user :user/family-name first)))]
      (html {:file "templates/user-menu.html" :selector "div.logged-in"}
            :img.gravatar {:set-attributes {:src gravatar-url}}
            :span.user-initials {:replace-children initials}))
    (let [login-url (get-in ctx [:auth :login-url])]
      (html {:file "templates/user-menu.html" :selector "div.unauthenticated"}
            :a {:set-attributes {:href login-url}}))))
