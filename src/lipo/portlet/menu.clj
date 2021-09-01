(ns lipo.portlet.menu
  "Portlet suitable for top level appbar menu.
  Lists all toplevel pages as links in alphabetical order."
  (:require [re-html-template.core :refer [html]]
            [lipo.portlet :as p]
            [lipo.content-db :as content-db]
            [lipo.db :as db]))

(defn- menu-link [db here {:content/keys [title] :as content}]
  (let [path (content-db/path db content)]
    (if (= path here)
      (html
       {:file "templates/menu.html"
        :selector "a.menu-link-active"}
       :a {:set-attributes
           {:href (content-db/path db content)}
           :replace-children title})
      (html
       {:file "templates/menu.html"
        :selector "a.menu-link"}

       :a {:set-attributes
           {:href (content-db/path db content)}
           :replace-children title}))))

(defmethod p/render :menu [{:keys [db here]} _]
  (let [items (->> (content-db/ls db "/")
                   (sort-by :content/title))]
    (doseq [item items]
      (menu-link db here item))))
