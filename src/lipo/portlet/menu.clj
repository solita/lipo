(ns lipo.portlet.menu
  "Portlet suitable for top level appbar menu.
  Lists all toplevel pages as links in alphabetical order."
  (:require [re-html-template.core :refer [html]]
            [lipo.portlet :as p]
            [lipo.content-db :as content-db]
            [lipo.db :as db]
            [ripley.js :as js]))

(defmacro link-transforms [go! title path]
  `[:a {:set-attributes
        {:href ~path
         :on-click [(partial ~go! ~path) js/prevent-default]}
        :replace-children ~title}])

(defn- menu-link [db go! here {:content/keys [title] :as content}]
  (let [path (content-db/path db content)]
    (if (= path here)
      (html {:file "templates/menu.html" :selector "a.menu-link-active"}
            (link-transforms go! title path))
      (html {:file "templates/menu.html" :selector "a.menu-link"}
            (link-transforms go! title path)))))

(defmethod p/render :menu [{:keys [db here go!]} _]
  (let [items (content-db/sort-content
               (content-db/ls db "/"))]
    (doseq [item items]
      (menu-link db go! here item))))
