(ns lipo.template
  "Layout templates.

  A template is a regular HTML file that is processed to code.

  The template should have slots for portlets configured in pages:
    <div data-portlet-slot=\"left\"/>

  The common slots are: left, right, top, bottom, content.
  Any content in the portlet slot is replaced with the configured
  portlets for that slot.

  Template can also render any portlets it wants by including
  the portlet configuration as EDN like:
    <div data-portlet=\"{:portlet/type :search}\"/>
  "

  (:require [re-html-template.core :refer [html] :as tpl]
            [ripley.html :as h]
            [lipo.portlet :as p]))

(tpl/set-global-options!
 {:wrap-hiccup '(ripley.html/html %)})

;; Make strings behave as checks for attribute presence
(defmethod tpl/custom-match-element String
  [string elt]
  (let [attrs (when (map? (second elt)) (second elt))]
    (contains? attrs (keyword string))))

(defn flash-message [{:keys [variant message]}]
  (h/html
   [:span
    (when message
      (case variant
        :success
        (html {:file "templates/main-template.html"
               :selector "[data-tpl='flash-message-success']"}
              :div.alert-description {:replace-children message})

        :error
        (html {:file "templates/main-template.html"
               :selector "[data-tpl='flash-message-error']"}
              :div.alert-description {:replace-children message})

        :info
        (html {:file "templates/main-template.html"
               :selector "[data-tpl='flash-message-info']"}
              :div.alert-description {:replace-children message})

        :warning
        (html {:file "templates/main-template.html"
               :selector "[data-tpl='flash-message-warning']"}
              :div.alert-description {:replace-children message})))]))

(defn main-template [ctx portlets-by-slot]
  (html
   {:file "templates/main-template.html"
    :selector "html"}

   :head {:append-children
          (h/live-client-script "/__ripley-live")}

   :div#flash-message {:replace-children
                       [::h/live (:flash-message-source ctx) flash-message]}

   "data-portlet-slot"
   {:let-attrs {slot :data-portlet-slot}
    :replace-children
    (doseq [p (portlets-by-slot (keyword "portlets" slot))]
      (p/render ctx p))}

   "data-portlet"
   {:let-attrs {portlet :data-portlet}
    :replace-children (p/render ctx (binding [*read-eval* false]
                                      (read-string portlet)))}))
