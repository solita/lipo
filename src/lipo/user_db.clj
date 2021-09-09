(ns lipo.user-db
  (:require [lipo.db :as db]))

(db/register-tx-fn!
 ::ensure-user
 '(fn [ctx current-user]
    (let [id (select-keys current-user [:user/id])
          existing-user (xtdb.api/entity (xtdb.api/db ctx) id)
          new-user (merge existing-user current-user {:xt/id id})]
      (if (and (some? existing-user)
               (= existing-user (assoc current-user :xt/id id)))
        []
        [[:xtdb.api/put new-user]]))))


(defn ensure-user-tx
  "Tx op to ensure a user exists."
  [user]
  {:pre [(some? (:user/id user))]}
  [:xtdb.api/fn ::ensure-user user])
