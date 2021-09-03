(ns lipo.content-db
  "Queries and transactions for content pages."
  (:require [crux.api :as crux]
            [clojure.string :as str]
            [lipo.content-model :as content-model]
            [lipo.db :as db]
            [lipo.meta-model :as meta-model]))

(def ^:private root-path #{nil "" "/"})

(defn content-id? [id]
  (or (= content-model/root-page-id id)
      (uuid? id)))

(defn content-ref [content-id-or-entity]
  {:pre [(or
          (content-id? content-id-or-entity)
          (and (map? content-id-or-entity)
               (content-id? (:crux.db/id content-id-or-entity))))]}
  (if (content-id? content-id-or-entity)
    content-id-or-entity
    (:crux.db/id content-id-or-entity)))

(defn paths-with-children
  "Given a sequence of paths return a set of those paths that have children."
  [db paths]
  (into #{}
        (map first)
        (crux/q db '[:find ?parent ?c :where [?c :content/parent ?parent]
                     :in [?parent ...]] paths)))

(declare content-id)

(defn ls
  "List content under path.

  Options:
  :type   if specified (keyword), only lists content of the given
          type
  :check-children?
          if true (default false), check each returned item if they
          have children and include it as :content/has-children?
  "
  [db path-or-parent & {:keys [type check-children?]
                        :or {check-children? false}}]
  (let [parent (cond
                 (contains? root-path path-or-parent)
                 content-model/root-page-id

                 (uuid? path-or-parent)
                 path-or-parent

                 :else
                 (content-id db path-or-parent))
        parent-where '[?c :content/parent ?parent]
        type-where (when type
                     '[?c :content/type ?type])
        results
        (->>
         (crux/q db
                 {:find '[(pull ?c [:crux.db/id
                                    :content/path :content/title
                                    :content/parent :content/type])]
                  :where (filterv
                          some?
                          [parent-where
                           type-where
                           '[?c :content/title _]])
                  :in '[?parent ?type]}
                 parent type)
         (mapv first)
         (sort-by :content/title))]

    (if check-children?
      (let [children? (paths-with-children db (map :crux.db/id results))]
        (for [r results]
          (assoc r :content/has-children? (contains? children? (:crux.db/id r)))))
      results)))


(defn has-children?
  "Check if given path has children. Returns boolean."
  [db path]
  (some?
   (ffirst
    (crux/q db '[:find ?c :limit 1 :where [?c :content/parent ?parent]
                 :in ?parent] path))))



(defn parents-of
  "Return ids of all parents for given path. Not including root."
  [db content]
  (loop [parents (list)
         here content]
    (if-let [parent (:content/parent (crux/pull db [:content/parent] here))]
      (recur (cons parent parents) parent)
      (vec parents))))

(defn content-id
  "Resolve id for content.
  If passed an id, it is returned as is.
  If passed a path string, the content is queried.

  If passed the root path, returns nil."
  [db id-or-path]
  (cond
    (content-id? id-or-path)
    id-or-path

    (contains? root-path id-or-path)
    content-model/root-page-id

    (and (string? id-or-path)
         (str/starts-with? id-or-path "/"))
    (let [[_ & components] (str/split id-or-path #"/")
          content-sym (zipmap components
                              (map #(symbol (str "c" %)) (range)))
          path-sym (zipmap components
                           (map #(symbol (str "p" %)) (range)))
          parent-child (partition 2 1 components)
          query {:find [(content-sym (last components))]
                 :in (vec (map path-sym components))
                 :where (into
                         [`[~(content-sym (first components)) :content/path
                            ~(path-sym (first components))]]
                         (mapcat
                          (fn [[parent child]]
                            (let [psym (content-sym parent)
                                  csym (content-sym child)
                                  path (path-sym child)]
                              `[[~csym :content/parent ~psym]
                                [~csym :content/path ~path]]))
                          parent-child))}]
      (ffirst (apply crux/q db
                     query
                     components)))

    :else
    (throw (ex-info "Resolving content-id requires a path (string starting with \"/\") or an id (uuid)"
                    {:id-or-path id-or-path}))))

(defn path
  "Generate path to content. Content must be a content ref."
  [db content]
  (let [id (content-ref content)
        segments (conj (parents-of db id) id)
        paths (into {}
                    (crux/q db
                            '{:find [?id ?path]
                              :where [[?id :content/path ?path]]
                              :in [[?id ...]]}
                            segments))]
    (str/join "/" (map paths segments))))


(defn page-entity
  "Pull page entity by id or path."
  [db content-path-or-id]
  (crux/entity db (content-id db content-path-or-id)))


(defn save! [{:keys [crux set-flash-message! go! user] :as ctx}
             set-edit-state!
             sub-page?
             new-content?
             content
             {:keys [title type body path]}]
  ;; PENDING figure out what the model for the user is
  (let [meta (if new-content?
               (meta-model/creation-meta user)
               (meta-model/modification-meta user))]
    (def content* content)
    ;; PENDING: Should merge as a db function
    (db/tx crux
      [:crux.tx/put
       (merge content
         {:content/title title
          :content/body body}
         meta
         (when-not (str/blank? path)
           {:content/path path})
         (when-not (str/blank? type)
           {:content/type (keyword type)}))]))
  (if sub-page?
    ;; Go to the newly created sub page
    (go! (path (crux/db crux) (:crux.db/id content)))

    ;; Set flash message and set new edit state
    (do
      (set-flash-message! {:variant :success :message "Sisältö tallennettu."})
      (set-edit-state! {:editing? false}))))

(defn delete! [{:keys [crux go!] :as ctx}
                {parent :content/parent
                 id :crux.db/id}]
  (let [parent-path (path (crux/db crux) parent)]
    (db/delete! crux id)
    (go! parent-path)))
