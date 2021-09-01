(ns lipo.portlet
  "Interface and support code for defining portlets.

  Portlet is an independet piece of a page.

  Portlets have a type and are configured with EDN.
  The portlet definition takes a request context and the
  configuration as parameter and will render HTML markup.

  Portlets can use ripley live components as needed."
  (:require [ripley.html :as h]))


(defmulti title
  "Get portlet title, defaults to :portlet/title form the configuration."
  (fn [_ctx portlet-config] (:portlet/type portlet-config)))

(defmethod title :default [_ctx {t :portlet/title}] t)

(defmulti render
  "Render a portlet"
  (fn [_ctx {type :portlet/type}] type))

(defmethod render :default
  [_ctx {type :portlet/type :as config}]
  (let [conf (pr-str config)]
    (h/html
     [:div.border-2.border-black.m-4.p-4
      [:div.bg-red-100
       "ERROR, NO RENDER METHOD FOR PORTLET"
       type]
      [:div.font-mono conf]])))

(def default-portlets
  "Default view portlets. Used if no portlets are defined for a slot in path."
  {:portlets/top
   [{:portlet/type :breadcrumbs}]

   :portlets/left
   [{:portlet/type :page-tree}]

   :portlets/content
   [{:portlet/type :view :inline-edit? true}]})

(defn merge-default-portlets [portlets]
  (reduce
   (fn [portlets slot]
     (if (contains? portlets slot)
       portlets
       (assoc portlets slot (default-portlets slot))))
   portlets
   (keys default-portlets)))

(defn portlets-by-slot

  "Get portlet configuration by page, will merge inherited parent portlets
  as well.

  Returns a mapping from portlet slot (eg. :portlets/top) to vector
  of portlets to show in order. Each portlet is a map containing
  :portlet/type and keys for the portlet configuration.

  If there is no portlet configuration for a given slot, the defaults
  defined in [[default-portlets]] are used."
  [db page]

  ;; FIXME: traverse parents and merge
  (merge-default-portlets
   (select-keys page [:portlets/top
                      :portlets/left
                      :portlets/content
                      :portlets/right
                      :portlets/bottom])))
