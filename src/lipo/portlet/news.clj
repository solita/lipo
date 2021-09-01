(ns lipo.portlet.news
  "News portlet lists the latest content in a path (defaults to here)."
  (:require [lipo.portlet :as p]
            [ripley.html :as h]
            [lipo.db :as db]))

(defn- render-item [_ctx {:content/keys [title excerpt]
                          at :meta/at
                          id :crux.db/id}]
  (let [date-time (str at)]
    (h/html
     [:div.news-item.border-b-2.border-black.mb-4
      [:a {:href id ;; FIXME: change that path != id
           }
       title]
      [:div.at date-time]
      [:div.excerpt excerpt]])))

(defmethod p/render :news [{:keys [here db] :as ctx}
                           {:keys [type path max-items]
                            :or {type :news
                                 max-items 20}}]
  (let [items (map first
                   (db/q db
                         (merge
                          {:limit max-items}
                          '{:find [(pull ?item [:crux.db/id
                                                :content/title
                                                :content/excerpt
                                                :meta/at]) ?at]
                            :where [[?item :content/type ?type]
                                    [?item :meta/at ?at]]
                            :order-by [[?at :desc]]
                            :in [?type]})
                         ;; FIXME: check rule that  path is ancestor of item
                         type max-items))]
    (h/html
     [::h/for [item items]
      (render-item ctx item)])))
