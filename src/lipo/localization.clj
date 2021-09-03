(ns lipo.localization
  "Message localization.
  The interface to translate messages is the `tr` function which takes a message
  and possible parameters.

  Translation functions use the `*language*` dynamic binding."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]))

(defonce loaded-languages (atom {}))

(def ^:dynamic *language* "The current language to use" :en)

(defn- load-language* [language]
  (binding [*read-eval* false]
    (-> (str "language/" (name language) ".edn")
        io/resource slurp read-string)))

(defn load-language!
  "Load the given language translation file, if it has not been loaded yet, and adds the language
  to the `loaded-languages` atom.

  Returns the language translations."
  [language]
  (if-let [translations (get @loaded-languages language)]
    translations
    (swap! loaded-languages assoc language
           (load-language* language))))

(defn translations
  "(Re)loads the given language translation file and returns the translations."
  [language]
  (swap! loaded-languages dissoc language)
  (load-language! language)
  (get @loaded-languages language))


(declare message)
(defmulti evaluate-list (fn [[operator & _] _] operator))

(defmethod evaluate-list :plural [[_ param-name zero one many] parameters]
  (let [count (get parameters param-name)]
    (cond
      (nil? count) (str "{{missing count parameter " param-name "}}")
      (zero? count) (message zero parameters)
      (= 1 count) (message one parameters)
      :else (message many parameters))))


(defmethod evaluate-list :default [[op & _] _]
  (str "{{unknown translation operation " op "}}"))

(defn- message-part [part parameters]
  (cond
    (keyword? part)
    (message-part (get parameters part) parameters)

    (list? part)
    (evaluate-list part parameters)

    :else
    (str part)))



(defn- message [message-definition parameters]
  (cond
    (string? message-definition)
    message-definition

    (list? message-definition)
    (evaluate-list message-definition parameters)

    :else
    (reduce (fn [acc part]
              (str acc (message-part part parameters)))
            ""
            message-definition)))

(defn tr
  "Returns translation for the given message.
  If `language` is provided, use that language, otherwise use the default.

  `message-path` is a vector of keywords path to the translation map.

  Optional `parameters` give values to replaceable parts in the message."
  ([message-path]
   (tr *language* message-path {}))
  ([message-path parameters]
   (tr *language* message-path parameters))
  ([language message-path parameters]
   (let [language (get @loaded-languages language)]
     (assert language (str "Language " language " has not been loaded."))
     (let [msg (message (get-in language message-path) parameters)]
       (if (not-empty msg)
         msg
         (str message-path))))))

(s/fdef tr-key
        :args (s/cat :path (s/coll-of keyword?))
        :ret fn?)

(defn tr-key
  "Returns a function that translates a keyword under the given `path`.
  This is useful for creating a formatting function for keyword enumerations.
  Multiple paths may be provided and they are tried in the given order. First
  path that has a translation for the requested key, is used."
  [& paths]
  (fn [key]
    (or
      (some #(let [message (tr (conj % key))]
               (when-not (str/blank? message)
                 message))
            paths)
      "")))

(defn tr-or
  "Utility for returning the first found translation path, or a fallback string (last parameter)"
  [& paths-and-fallback]
  (or (some #(let [result (tr %)]
               (when-not (str/blank? result)
                 result))
            (butlast paths-and-fallback))
      (last paths-and-fallback)))

(defn tr-tree
  ([tree-path]
   (tr-tree *language* tree-path))
  ([language tree-path]
   (get-in (get @loaded-languages language) tree-path)))

(defn tr-enum [kw-or-map-with-db-ident]
  (let [kw (if (keyword? kw-or-map-with-db-ident)
             kw-or-map-with-db-ident
             (:crux.db/id kw-or-map-with-db-ident))]
    (tr [:enum kw])))

(let [warn (memoize (fn [msg]
                      (log/warn "UNTRANSLATED MESSAGE: " msg)))]

  (defn tr-fixme
    "Indicate a message that hasn't been translated yet."
    ([msg]
     (warn msg)
     msg)
    ([msg parameters]
     (warn msg)
     (str msg " " (pr-str parameters)))))


(defmacro with-language [lang & body]
  `(binding [*language* ~lang]
     (load-language! *language* (fn [_# _#] ~@body))))

(defmacro with-language-fn
  "Return function that is bound to the given language."
  [lang fn-to-bind]
  `(let [the-fn# ~fn-to-bind]
     (fn [& args#]
       (binding [*language* ~lang]
         (apply the-fn# args#)))))
