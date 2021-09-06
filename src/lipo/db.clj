(ns lipo.db
  "CRUX db utilities"
  (:require [crux.api :as crux]
            [taoensso.timbre :as log]
            [ripley.live.source :as source]
            [ripley.live.protocols :as lp]))

(def db crux/db)

(defn tx
  "Submit tx with the given tx operations. Waits until tx is processed by this node."
  [crux & tx-ops]
  (let [tx (crux/submit-tx crux (vec tx-ops))]
    (crux/await-tx crux tx)))

(defn q
  "Wrapper for [[crux.api/q]]."
  [db & args]
  (apply crux/q db args))

(defn q-async
  "Run query asynchronously in another thread and call
  function with the results.

  The arguments are the same as in [[q]] but the last argument
  must be the callback function.

  Returns a future that returns the result of the callback."
  [db & args-and-cb]
  (let [cb (last args-and-cb)
        args (butlast args-and-cb)]
    (future
      (try
        (cb (apply crux/q db args))
        (catch Throwable t
          (log/warn t "Exception in async query"))))))

(defn q-source
  "Ripley source for a query. Updates automatically if new transactions."
  [crux & args]
  (let [q #(apply crux/q (crux/db crux) args)
        last-value (atom (q))
        crux-listener (atom nil)
        [source _listeners]
        (source/source-with-listeners #(deref last-value)
                                      #(-> crux-listener deref .close))]
    (reset! crux-listener
            (crux/listen crux {:crux/event-type :crux/indexed-tx}
                         (fn [& _]
                           (let [new-value (q)]
                             (reset! last-value new-value)
                             (lp/write! source new-value)))))
    source))

(defn entity [db id]
  (crux/entity db id))

(defn delete!
  "Convenience for deleting documents."
  [crux & ids]
  (apply tx crux
         (map (fn [id]
                [:crux.tx/delete id])
              ids)))
