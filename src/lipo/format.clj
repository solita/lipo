(ns lipo.format
  "Generic localized formatting of data for display."
  (:require [lipo.localization :refer [tr]]
            [ripley.html :as h]))


(defmulti display type)

(defmethod display java.util.Date [date]
  (.format (java.text.SimpleDateFormat. (tr [:date :date-time-pattern]))
           date))

(defmethod display :default [thing]
  (str thing))

(defn render [x]
  (let [str (display x)]
    (h/html str)))
