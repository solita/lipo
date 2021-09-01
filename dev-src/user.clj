(ns user
  (:require lipo.main
            [crux.api :as crux]
            [lipo.db :as db]))

(defn db []
  (crux/db @lipo.main/crux))

(defn tx [& ops]
  (apply db/tx @lipo.main/crux ops))

(defn q [& args]
  (apply crux/q (db) args))

(defn crux []
  @lipo.main/crux)

(def start lipo.main/start)
