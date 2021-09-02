(ns lipo.portlet.view
  "Portlet that simply and optionally edits a content page by id or path.

  Can show other portlets that are defined in the content.

  In content, portlets can be defined using with EDN map surrounded by brackets.


  Example:
  {{:portlet/type :some-portlet :some \"option\"}}

  Will replace that with the rendered portlet."
  (:require [lipo.portlet :as p]
            [lipo.content-db :as content-db]
            [ripley.html :as h]
            [crux.api :as crux]
            [ripley.live.source :as source]
            [lipo.content-model :as content-model]
            [cheshire.core :as cheshire]
            [ripley.js :as js]
            [clojure.string :as str]
            [lipo.db :as db]
            [taoensso.timbre :as log]))


(def editor-config
  {:toolbar
   {:items [:heading
            :|
            :bold
            :italic
            :link
            :bulletedList
            :numberedList
            :|
            :outdent
            :indent
            :|
            :imageUpload
            :blockQuote
            :insertTable
            :mediaEmbed
            :undo
            :redo
            :horizontalLine
	    :findAndReplace
	    :fontBackgroundColor
	    :fontColor
	    :fontFamily
	    :htmlEmbed
            ]}
   :language :fi
   :image {:toolbar [:imageTextAlternative
                     :imageStyle:inline
                     :imageStyle:block
                     :imageStyle:side
                     :linkImage]}
   :table {:contentToolbar [:tableColumn
                            :tableRow
                            :mergeTableCells]}
   :simpleUpload
   {:uploadUrl "/_upload"
    :withCredentials true}
   })

(defn text-field
  ([name label] (text-field name label ""))
  ([name label value]
   (h/html
    [:div.my-2
     [:label {:class "inline-block w-1/4" :for name} label]
     [:input {:class "w-3/4" :id name :name name :value value}]])))

(defn- editor-form
  "Create an editor form for updating an existing document or creating a new one."
  [{path :crux.db/id
    :content/keys [parent title body type]} save!]
  (h/html
   [:span
    [:form.editor
     [::h/when (not path)
      (text-field "path" "Polku:")]

     [:div.my-2
      [:label {:class "inline-block w-1/4" :for "type"} "Tyyppi:"]
      [:select {:class "w-3/4" :name "type"}
       [:option {:value ""} " "] ; no type
       [::h/for [t content-model/content-types
                 :let [value (name t)
                       ;; translate this
                       label (name t)
                       selected? (= t type)]]
        [:option {:value value :selected selected?} label]]]]

     (text-field "title" "Otsikko:" title)
     [:input#newbody {:name "body" :type "hidden"}]
     [:div#content-body]
     [:button.rounded.bg-green-400.m-1.p-1.text-white
      {:type "submit"
       :on-click ["document.getElementById('newbody').value = window.E.getData()"
                  (js/js save! (js/form-values "form.editor")) js/prevent-default]}
      "Tallenna"]

     [:script
      (h/out!
       "ClassicEditor.create(document.querySelector('#content-body'), "
       (cheshire/encode editor-config)
       ").then( (e) => { "
       (when body (str "e.setData(" (cheshire/encode body) "); "))
       "window.E = e;"
       "});")]]]))

(defn- body-with-portlets
  "Read portlet definitions from body, returns sequence of
  strings and portlet definition maps."
  [body-html-str]
  (if-let [start (str/index-of body-html-str "{{")]
    (let [before (subs body-html-str 0 start)
          after (subs body-html-str (inc start))
          end (str/index-of after "}}")]
      (if end
        (let [definition (subs after 0 (inc end))
              after (subs after (+ 2 end))]
          ;; Found portlet definition, try to read it
          (concat
           (list before
                 (try (binding [*read-eval* false]
                        (read-string definition))
                      (catch Throwable t
                        (log/debug t "Unparseable portlet definition:" definition)
                        "[ERROR: unparseable portlet definition.]")))
           (body-with-portlets after)))
        ;; No end found, return as is
        (list body-html-str)))

    ;; No portlet definition start found, return as is
    (list body-html-str)))

(defn- render-body-with-portlets [ctx body]
  (doseq [part (body-with-portlets body)]
    (if (string? part)
      ;; FIXME: body *must* be well formed HTML here
      (h/out! part)
      (p/render ctx part))))

(defn- view-or-edit-content [{:keys [crux set-flash-message!] :as ctx} path can-edit? set-editing! editing?]
  (let [db (crux/db crux)
        id (content-db/content-id db path)
        {:content/keys [title body] :as content}
        (crux/entity db id)]
    (def *c content)
    (h/html
     [:div.content-view.ck-content
      [::h/when (and can-edit? (not editing?))
       [:button.bg-gray-300.rounded.p-1 {:on-click #(set-editing! true)}
        "Muokkaa"]]
      [::h/if editing?
       (editor-form
        content
        (fn [{:keys [title type body]}]
          ;; PENDING: Should merge as a db function
          (db/tx crux
                 [:crux.tx/put
                  (merge content
                         {:content/title title
                          :content/body body}
                         (when-not (str/blank? type)
                           {:content/type (keyword type)}))])
          (set-flash-message! {:variant :success :message "Sisältö tallennettu."})
          (set-editing! false)))
       [:<>
        [:h3 title]
        (when body
          (render-body-with-portlets ctx body))]]])))

(defmethod p/render :view [ctx {:keys [path inline-edit?]}]
  (let [path (or path (:here ctx))
        can-edit? (and inline-edit?
                       ;; FIXME: check permission here
                       true)
        [editing-source set-editing!] (source/use-state false)]
    (h/html
     [:span
      [::h/live editing-source
       (partial view-or-edit-content ctx path can-edit? set-editing!)]])))
