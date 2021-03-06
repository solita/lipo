(ns lipo.portlet.list
  "List content under a path.

  Configure multiple paths, like:

  {:portlet/type :list
   :paths [\"/some/path\"
           \"/some/other-path\"]}

  Will show blocks with title of the path and links
  to all contents under it."
  (:require [re-html-template.core :refer [html]]
            [lipo.portlet :as p]
            [lipo.content-db :as content-db]
            [lipo.db :as db]
            [ripley.html :as h]
            [ripley.js :as js]))

(defmethod p/render :list [{:keys [db go!]} {paths :paths}]
  (let [path-id (into {}
                      (map (juxt identity
                                 (partial content-db/content-id db)))
                      paths)
        titles (into {}
                     (db/q db '{:find [?p ?title]
                                :where [[?p :content/title ?title]]
                                :in [[?p ...]]}
                           (map path-id paths)))
        content (into {}
                      (map (juxt identity
                                 (partial content-db/ls db)))
                      (map path-id paths))
        content-path (fn [parent-path {:content/keys [path]}]
                       (str parent-path "/" path))]
    (html
     {:file "templates/list.html"
      :selector "div.list-portlet"}

     :div.list-path
     {:for {:item path :items paths}}

     :h3 {:replace-children (some-> path path-id titles h/dyn!)}

     :a {:for {:item {id :xt/id title :content/title :as c}
               :items (some-> path path-id content)}
         :set-attributes {:href (or (content-path path c) "")
                          :on-click [(partial go! (content-path path c))
                                     js/prevent-default]}
         :replace-children title})))
