(ns lipo.format
  "Generic localized formatting of data for display."
  (:require [lipo.localization :refer [tr]]))


(defmulti display type)

(defmethod display java.util.Date [date]
  (.format (java.text.SimpleDateFormat. (tr [:date :date-time-pattern]))
           date))

(defmethod display :default [thing]
  (str thing))
