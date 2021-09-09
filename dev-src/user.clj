(ns user
  (:require lipo.main
            [xtdb.api :as xt]
            [lipo.db :as db]))

(defn db []
  (xt/db @lipo.main/xtdb))

(defn tx [& ops]
  (apply db/tx @lipo.main/xtdb ops))

(defn q [& args]
  (apply xt/q (db) args))

(defn xtdb []
  @lipo.main/xtdb)

(def start lipo.main/start)
