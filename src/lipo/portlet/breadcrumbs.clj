(ns lipo.portlet.breadcrumbs
  "Portlet that shows breadcrumbs to current content."
  (:require [lipo.content-db :as content-db]
            [lipo.db :as db]
            [lipo.portlet :as p]
            [ripley.html :as h]))

(defmethod p/render :breadcrumbs [{:keys [db here]} _]
  (when-let [id (content-db/content-id db here)]
    (let [segments (conj (content-db/parents-of db id) id)
          paths (into {}
                      (comp (map first)
                            (map (juxt :xt/id identity)))
                      (db/q db '{:find [(pull ?id [:xt/id :content/path :content/title])]
                                 :in [[?id ...]]}
                            segments))]

      (h/html
       [:div.breadcrumbs.flex
        [:a.breadcrumb-item {:href "/"}
         [:span.oi {:data-glyph "home"}]]
        [::h/for [s segments
                  :let [path (content-db/path db s)
                        {:content/keys [title]} (paths s)]]
         [:<>
          [:div.breadcrumb-separator
           [:span.oi.ml-1.mr-1 {:data-glyph "chevron-right"}]]
          [::h/if (= s id)
           [:span.breadcrumb-item title]
           [:a.breadcrumb-item {:href path} title]]]]]))))
