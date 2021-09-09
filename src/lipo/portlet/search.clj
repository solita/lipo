(ns lipo.portlet.search
  "Search portlet that lists content matching user input.
  Uses the XTDB lucene module."
  (:require [lipo.db :as db]
            [lipo.portlet :as p]
            [lipo.content-db :as content-db]
            [lipo.localization :refer [tr tr!]]
            [ripley.html :as h]
            [ripley.js :as js]
            [ripley.live.source :as source :refer [c=]]
            [ripley.live.collection :as collection]
            [ripley.live.protocols :as lp]
            [clojure.string :as str]
            [re-html-template.core :refer [html]]))

(defn- result-view [{db :db go! :go! :as _ctx}
                    {id :xt/id
                     :content/keys [title excerpt]}]
  (let [link (content-db/path db id)
        excerpt (if (> (count excerpt)
                      100)
                  (str (subs excerpt 0 100) "...")
                  excerpt)]
    (h/html
     [:div
      [:a.search-result {:href link
                         :on-click [#(go! link) js/prevent-default]}
       [:strong.block title]
       [:span.text-sm excerpt]]])))

(defn- results-view [ctx results-source {:keys [searching? term results]}]
  (h/html
   [:div.search-results.fixed.bg-white.shadow-md
    [::h/if searching?
     [:svg.animate-spin.h-5.w-5.text-blue-500
      {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 24 24"}
      [:circle.opacity-25
       {:cx 12 :cy 12 :r 10 :stroke "currentColor" :stroke-width 4}]
      [:path.opacity-75
       {:fill "currentColor"
        :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]]
     [::h/if (empty? results)
      [::h/when (not (str/blank? term))
       [:div.no-results.p-3.w-full
        [:p.w-full (tr! [:search :no-results])]]]
      [:div
       (collection/live-collection {:container-element :div
                                    :key :xt/id
                                    :source results-source
                                    :render (partial result-view ctx)})]]]]))

(def search-priority-by-attribute
  {:content/title 1
   :content/excerpt 2
   :content/body 3
   :content/path 4})

(defn- search [db term set-state!]
  (db/q-async
   db
   '{:find [(pull ?e [:xt/id :content/title :content/excerpt]) ?s]
     :where [[(wildcard-text-search ?term) [[?e _ _ ?s]]]]
     :order-by [[?s :desc]]
     :in [?term]}
   term
   #(set-state! {:searching? false
                 :results (distinct (map first %))
                 :term term})))

(defmethod p/render :search [{db :db :as ctx} _]
  (let [[state-source set-state!] (source/use-state {:searching? false
                                                     :term ""
                                                     :results []})
        search! (fn [term]
                  (if (str/blank? term)
                    (set-state! {:searching? false
                                 :term term
                                 :results []})
                    (when (not= term
                            (:term (lp/current-value state-source)))
                      (set-state! {:searching? true
                                   :term term
                                   :results []})
                      (search db term set-state!))))
        results-source (c= (:results %state-source))
        placeholder (tr [:search :placeholder])]
    (html
     {:file "templates/search.html" :selector "body div"}
     :input#search
      {:set-attributes
       {:placeholder placeholder
        :on-input (js/js-debounced 500 search!
                    (js/input-value "search"))}}

     :button
     {:set-attributes {:on-click [(js/js search! (js/input-value "search"))
                                  "return false"]}}

     :div.results
     {:replace [::h/live state-source #(results-view ctx results-source %)]})))
