(ns lipo.portlet.user-menu
  "User menu portlet. Shows currently logged in user, or link to login."
  (:require [lipo.portlet :as p]
            [re-html-template.core :refer [html]]
            [ripley.html :as h]))

(defmethod p/render :user-menu [{{user :user} :request :as ctx} _]
  (def *u user)
  (if-let [user-name (when (seq user)
                       (str (:user/given-name user) " " (:user/family-name user)))]
    (html {:file "templates/user-menu.html" :selector "div.logged-in"}
          :span.user-name {:replace-children user-name})
    (let [login-url (get-in ctx [:auth :login-url] "FOOBAR")]
      (html {:file "templates/user-menu.html" :selector "div.unauthenticated"}
            :a {:set-attributes {:href login-url}}))))
