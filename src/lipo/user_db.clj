(ns lipo.user-db
  (:require [lipo.db :as db]))

(db/register-tx-fn!
 ::ensure-user
 '(fn [ctx current-user]
    (let [id (select-keys current-user [:user/id])
          existing-user (crux.api/entity (crux.api/db ctx) id)
          new-user (merge existing-user current-user {:crux.db/id id})]
      (if (and (some? existing-user)
               (= existing-user (assoc current-user :crux.db/id id)))
        []
        [[:crux.tx/put new-user]]))))
