(ns lipo.portlet.news
  "News portlet lists the latest content in a path (defaults to here)."
  (:require [lipo.portlet :as p]
            [ripley.html :as h]
            [lipo.db :as db]
            [lipo.content-db :as content-db]
            [lipo.content-model :as content-model]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]))

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

(comment
  '{:find [?anc ?parent]
    :where [(ancestor ?content ?anc)
            [?anc :content/parent ?parent]]
    :in [?content]
    :rules [[(ancestor ?c ?a)
             [(== ?c ?a)]]
            [(ancestor ?c ?a)
             [?c :content/parent ?a]]
            [(ancestor ?c ?a)
             [?c :content/parent ?p]
             (ancestor ?p ?a)]]})

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
     [:<>
      [:p "Virheellinen portlet määritelmä: "
       (h/dyn!
         (str params))]
      [:p "Esimerkki oikeasta määritelmästä: "
       (h/dyn! (pr-str {:portlet/type :news :types [:memo :news] :max-items 40 :path "/polku/toinen"}))]
      [:p "Validit tyypit: "
       (h/dyn! (pr-str content-model/content-types))]]]))

(defmethod p/render :news [{:keys [here db] :as ctx}
                           {:keys [types path max-items]
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
                      '{:find [(pull ?item [:crux.db/id
                                            :content/title
                                            :content/excerpt
                                            :content/parent]) ?created]
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
                    ;; FIXME: check rule that  path is ancestor of item
                    types entity-of-path))]
      (h/html
        [::h/for [item items]
         (render-item ctx item)]))))
