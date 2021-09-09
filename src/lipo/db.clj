(ns lipo.db
  "XTDB db utilities"
  (:require [xtdb.api :as xt]
            [taoensso.timbre :as log]
            [ripley.live.source :as source]
            [ripley.live.protocols :as lp]))

(def db xt/db)

(defn tx
  "Submit tx with the given tx operations. Waits until tx is processed by this node."
  [xtdb & tx-ops]
  (let [tx (xt/submit-tx xtdb (vec tx-ops))]
    (xt/await-tx xtdb tx)))

(defn q
  "Wrapper for [[xtdb.api/q]]."
  [db & args]
  (apply xt/q db args))

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
        (cb (apply xt/q db args))
        (catch Throwable t
          (log/warn t "Exception in async query"))))))

(defn q-source
  "Ripley source for a query. Updates automatically if new transactions."
  [xtdb & args]
  (let [q #(apply xt/q (xt/db xtdb) args)
        last-value (atom (q))
        xtdb-listener (atom nil)
        [source _listeners]
        (source/source-with-listeners #(deref last-value)
                                      #(-> xtdb-listener deref .close))]
    (reset! xtdb-listener
            (xt/listen xtdb {:xtdb.api/event-type :xtdb.api/indexed-tx}
                         (fn [& _]
                           (let [new-value (q)]
                             (reset! last-value new-value)
                             (lp/write! source new-value)))))
    source))

(defn entity [db id]
  (xt/entity db id))

(defn delete!
  "Convenience for deleting documents."
  [xtdb & ids]
  (apply tx xtdb
         (map (fn [id]
                [:xtdb.api/delete id])
              ids)))


(defonce tx-fns (atom {}))

(defn register-tx-fn!
  "Register a tx fn, meant to be called as side effect of loading a namespace."
  [name body]
  (swap! tx-fns assoc name body))

(defn init-tx-fns!
  "Initialize all tx fns. Submits new txs to create any missing functions."
  [xtdb]
  (let [tx-fns @tx-fns
        existing-fns (into {}
                           (q (xt/db xtdb)
                              '[:find ?id ?fn
                                :where [?id :xt/fn ?fn]
                                :in [?id ...]]
                              (keys tx-fns)))
        changed-or-new-fns
        (into {}
              (filter (fn [[name body]]
                        (not= body (get existing-fns name))))
              tx-fns)]
    (when (seq changed-or-new-fns)
      (apply tx xtdb
             (for [[name body] changed-or-new-fns]
               [:xtdb.api/put {:xt/id name
                               :xt/fn body}])))))
