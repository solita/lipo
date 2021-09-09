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
            [xtdb.api :as xt]
            [ripley.live.source :as source]
            [ripley.js :as js]))

(defn page-list [db current-page pages]
  (h/html
   [:ul.ml-4
    [::h/for [[path title] pages]
     [:li
      [::h/if (= path current-page)
       [:b title]
       [:a {:href path} title]]
      (page-list db current-page
                 (xt/q db '[:find ?p ?title
                              :where
                              [?p :content/title ?title]
                              [?p :content/parent path]
                              :in path]
                         path))]]]))

(defn- page-tree-item [{:keys [db go! initial-open content set-open! path here top-level?] :as opts} open?]
  (let [{id :xt/id
         :content/keys [title has-children?]} content
        class (str
               "p-3 block w-full mr-2 mb-2 inline-block overflow-ellipsis overflow-hidden"
               (when (= path here)
                 " bg-gray-200")
                (when top-level?
                  " font-bold"))]
    (h/html
     [:li.block
      [:div.flex.flex-row.items-center
       [::h/when has-children?
        [:div.inline-block.mr-2
         {:on-click #(set-open! (not open?))}
         [::h/if open?
          [:span.oi {:data-glyph "chevron-bottom"}]
          [:span.oi {:data-glyph "chevron-right"}]]]]
       [:a {:href path
            :on-click [#(go! path) js/prevent-default]
            :class class} title]]
      [::h/when open?
       [:ul.ml-8
        [::h/for [{child-path :content/path id :xt/id :as content}
                  (content-db/ls db (:xt/id content) :check-children? true)
                  :let [[open? set-open!] (source/use-state (contains? initial-open id))]]
         [::h/live open? (partial page-tree-item
                           (merge opts {:path (str path "/" child-path)
                                        :content content :set-open! set-open!
                                        :top-level? false}))]]]]])))

(defn- page-tree-live [go! db here root]
  (let [root-content (content-db/ls db root :check-children? true)
        ;; open a path to current-page
        current-page (content-db/content-id db here)
        initial-open (if current-page
                       (into #{current-page}
                             (content-db/parents-of db current-page))
                       #{})]
    (h/html
      [:ul.ml-4.page-list

       [::h/for [{id :xt/id path :content/path parent :content/parent :as content} root-content
                 :let [[open? set-open!] (source/use-state (contains? initial-open id))]]
        [::h/live open? (partial page-tree-item {:db db
                                                 :go! go!
                                                 :here here
                                                 :parent parent
                                                 :top-level? true
                                                 :path (str root "/" path)
                                                 :initial-open initial-open
                                                 :content content
                                                 :set-open! set-open!})]]])))


(defmethod p/render :page-tree [{:keys [db here go!]} {:keys [content-type path]}]
  (page-tree-live go! db here (or path "")))
