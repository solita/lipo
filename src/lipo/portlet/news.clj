(ns lipo.portlet.news
  "News portlet lists the latest content in a path (defaults to here)."
  (:require [lipo.portlet :as p]
            [ripley.html :as h]
            [lipo.db :as db]
            [lipo.content-db :as content-db]
            [lipo.content-model :as content-model]
            [clojure.set :as set]
            [lipo.localization :refer [tr!]]
            [lipo.format :as format]))

(defn- render-item [{:keys [db] :as ctx}
                    {:content/keys [title excerpt]
                     created :meta/created
                     id :xt/id
                     :as content}]
  (h/html
    [:div.news-item.border-b-2.border-gray-500.mb-4
     [:a.text-xl {:href (content-db/path db content)}
      title]
     [:span.text-xs.text-gray-600.inline-block.ml-2
      (h/dyn! (format/display created))]
     [:p.excerpt excerpt]]))

(defn valid-params?
  [{:keys [types path max-items]}]
  ;; check that type is collection of keywords
  (and
    (or (nil? types) (set/subset? (set types) content-model/content-types))
    (or (nil? max-items) (int? max-items))
    (or (nil? path) (re-matches content-model/uri-path-pattern path))))

(defn news-portlet-invalid-params
  [params]
  (h/html
    [:div.border-2.border-red-500.p-5
     [:p (tr! [:errors :news-portlet-error])
      (h/dyn!
        (str params))]]))

(defmethod p/render :news [{:keys [db] :as ctx}
                           {:keys [types title path max-items]
                            :or {types content-model/content-types
                                 max-items 20
                                 path content-model/root-page-id}
                            :as params}]
  (if-not (valid-params? params)
    (news-portlet-invalid-params params)
    (let [entity-of-path (content-db/content-id db path)
          items (map first
                  (db/q db
                    (merge
                      {:limit max-items}
                      '{:find [(pull ?item [:xt/id
                                            :content/title
                                            :content/excerpt
                                            :content/parent
                                            :meta/created]) ?created]
                        :where
                        [[?item :content/type ?types]
                         [?item :meta/created ?created]
                         (ancestor ?item ?ancestor)]
                        :rules [[(ancestor ?c ?a)
                                 [?c :content/parent ?a]]
                                [(ancestor ?c ?a)
                                 [?c :content/parent ?p]
                                 (ancestor ?p ?a)]]
                        :order-by [[?created :desc]]
                        :in [[?types ...] ?ancestor]})
                    types entity-of-path))]
      (h/html
        [:div
         [::h/when title
          [:h2.mb-4 title]]
         [::h/for [item items]
          (render-item ctx item)]]))))
