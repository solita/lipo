(ns lipo.portlet.page-tree
  "Page tree portlet.
  Shows a hierarchical structure of pages.

  Configuration:
  :content-type  optional content type (default shows all)
  :path          path to start showing from (default is the root)
  "
  (:require [lipo.portlet :as p]
            [lipo.content-db :as content-db]
            [ripley.html :as h]
            [crux.api :as crux]
            [ripley.live.source :as source]))

(defn page-list [db current-page pages]
  (h/html
   [:ul.ml-4
    [::h/for [[path title] pages]
     [:li
      [::h/if (= path current-page)
       [:b title]
       [:a {:href path} title]]
      (page-list db current-page
                 (crux/q db '[:find ?p ?title
                              :where
                              [?p :content/title ?title]
                              [?p :content/parent path]
                              :in path]
                         path))]]]))

(defn- page-tree-item [{:keys [db initial-open content set-open! path here] :as opts} open?]
  (let [{id :crux.db/id
         :content/keys [title has-children?]} content
        class (str
               "p-3 block w-full ml-2 mr-2"
               (when (= path here)
                 " bg-gray-200"))]
    (h/html
     [:li.block
      [:div.flex.flex-row.items-center
       [::h/when has-children?
        [:div.inline-block.w-4
         {:on-click #(set-open! (not open?))}
         [::h/if open?
          [:span.oi {:data-glyph "chevron-bottom"}]
          [:span.oi {:data-glyph "chevron-right"}]]]]
       [:a {:href path :class class} title]]
      [::h/when open?
       [:ul.ml-4
        [::h/for [{child-path :content/path id :crux.db/id :as content}
                  (content-db/ls db (:crux.db/id content) :check-children? true)
                  :let [[open? set-open!] (source/use-state (contains? initial-open id))]]
         [::h/live open? (partial page-tree-item
                                  (merge opts {:path (str path "/" child-path)
                                               :content content :set-open! set-open!}))]]]]])))

(defn- page-tree-live [db here root]
  (let [root-content (content-db/ls db root :check-children? true)
        ;; open a path to current-page
        current-page (content-db/content-id db here)
        initial-open (if current-page
                       (into #{current-page}
                             (content-db/parents-of db current-page))
                       #{})]
    (h/html
     [:ul.ml-4

      [::h/for [{id :crux.db/id path :content/path :as content} root-content
                :let [[open? set-open!] (source/use-state (contains? initial-open id))]]
       [::h/live open? (partial page-tree-item {:db db
                                                :here here
                                                :path (str root "/" path)
                                                :initial-open initial-open
                                                :content content
                                                :set-open! set-open!})]]])))


(defmethod p/render :page-tree [{:keys [db here]} {:keys [content-type path]}]
  (page-tree-live db here (or path "")))
