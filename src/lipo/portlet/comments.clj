(ns lipo.portlet.comments
  "Comments portlet for content."
  (:require [lipo.portlet :as p]
            [ripley.html :as h]
            [lipo.content-db :as content-db]
            [ripley.live.collection :refer [live-collection]]
            [lipo.db :as db]
            [ripley.live.source :as source]
            [lipo.format :as format]
            [lipo.portlet.user-menu :as portlet.user-menu]
            [ripley.js :as js]
            [lipo.localization :refer [tr tr!]]
            [clojure.string :as str]))

(defn- comments-source [node id]
  (db/q-source node
               '{:find [(pull ?c [* {:comment/author [:user/email :user/given-name :user/family-name]}]) ?ts]
                 :where [[?c :comment/on id]
                         [?c :comment/timestamp ?ts]]
                 :in [id]
                 :order-by [[?ts :asc]]}
               id))

(defn render-comment [{:comment/keys [author text timestamp]}]
  (let [name (str (:user/given-name author) " " (:user/family-name author))]
    (h/html
     [:div.relative.grid.grid-cols-1.gap-4.p-4.mb-8.border.rounded-lg.bg-white.shadow-lg
      [:div.relative.flex.gap-4
       [:img.relative.rounded-lg.-top-8.-mb-4.bg-white.border.h-20.w-20
        {:src (portlet.user-menu/gravatar-url 78 author)
         :alt ""
         :loading "lazy"}]

       [:div.flex.flex-col.w-full
        [:div.flex.flex-row.justify-between
         [:p.relative.text-xl.whitespace-nowrap.truncate.overflow-hidden
          name]
         [:a.text-gray-500.text-xl {:href ""} [:i.fa-solid.fa-trash]]]

        [:p.text-gray-400.text-sm (format/render timestamp)]]]

      [:p.-mt-4.text-gray-500 text]])))

(defn comment-form [node user id]
  (h/html
   [:div.flex.items-center.justify-center.shadow-lg.mb-4.max-w-lg
    [:form.w-full.max-w-xl.bg-white.rounded-lg.px-4.pt-2 {:name "newcomment"}
     [:div.flex.flex-wrap.-mx-3.mb-6
      [:h2.px-4.pt-3.pb-2.text-gray-800.text-lg (tr! [:comments :add-new])]
      [:div.w-full.md:w-full.px-3.mb-2.mt-2
       [:textarea.bg-gray-100.rounded.border.border-gray-400.leading-normal.resize-none.w-full.h-20.py-2.px-3.font-medium.placeholder-gray-700.focus:outline-none.focus:bg-white
        {:id "comment-text"
         :placeholder (tr [:comments :placeholder])
         :name "text"}]]
      [:div.w-full.md:w-full.flex.items-start.md:w-full.px-3
       [:div.-mr-1
        [:button.bg-white.text-gray-700.font-medium.py-1.px-4.border.border-gray-400.rounded-lg.tracking-wide.mr-1.hover:bg-gray-100
         {:on-click
          [(js/js #(when-not (str/blank? %)
                     (db/put node
                             {:xt/id {:comment (java.util.UUID/randomUUID)}
                              :comment/author {:user/id (:user/id user)}
                              :comment/text %
                              :comment/timestamp (java.util.Date.)
                              :comment/on id}))
                  (js/input-value "comment-text"))
           "document.forms.newcomment.reset(); return false;"]}
         (tr! [:comments :post-comment])]]]]]]))

(defmethod p/render :comments [{:keys [xtdb db here user] :as ctx} _]
  (let [id (content-db/content-id db here)
        comments (comments-source xtdb id)]
    (h/html
     [:div.flex.flex-col
      [:div.divider.divider-vertical]
      [::h/live (source/computed count comments)
       (fn [comment-count]
         (let [msg (case comment-count
                     0 (tr [:comments :no-comments])
                     1 (tr [:comments :one-comment])
                     (tr [:comments :many-comments] {:count comment-count}))]
           (h/html
            [:div msg])))]

      [:div.py-4
       (live-collection
        {:source (source/computed #(mapv first %) comments)
         :key :xt/id
         :render render-comment})

       (comment-form xtdb user id)]])))
