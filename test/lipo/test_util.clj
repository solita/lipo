(ns lipo.test-util
  (:require  [clojure.test :as t :refer [is]]
             [xtdb.api :as xt]))

(def ^:dynamic *node* nil)

(defn in-memory-db-fixture
  [tests]
  (binding [*node* (xt/start-node {})]
    (tests)))

(defn wait-for
  "Wait for condition to occur in given timeout.
  Useful for testing that sources update.

  Timeout defaults to 1 second."
  ([func message] (wait-for 1000 func message))
  ([timeout func message]
   (let [initial (System/currentTimeMillis)]
     (loop [v (func)]
       (if v
         (is v message)
         (if (> (System/currentTimeMillis) (+ initial timeout))
           (is false (str "Timed out awaiting: " message))
           (do
             (Thread/sleep 10)
             (recur (func)))))))))
