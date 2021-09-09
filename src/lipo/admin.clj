(ns lipo.admin

  "Low level admin endpoint to PUT and GET documents from the store.
  This makes it easy to seed a new environment by just sending new
  documents to it via HTTP.

  Only enabled if LIPO_ADMIN_TOKEN environment variable is specified
  (non blank). The requests must have Authorization with bearer token
  that matches the admin token."

  (:require [compojure.core :refer [GET PUT routes]]
            [lipo.db :as db]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [xtdb.api :as xt]
            [clojure.pprint :as pprint]))

(defn- with-id [req token func]
  (if (not= (str "Bearer " token)
            (get-in req [:headers "authorization"]))
    {:status 404 :body "Not found"}
    (if-let [id (try (some-> req :route-params :doc java.util.UUID/fromString)
                     (catch IllegalArgumentException _iae
                       nil))]
      (func id)
      {:status 400
       :body "Illegal doc id"})))

(defn admin-routes [{xtdb :xtdb :as ctx}]
  (let [token (System/getenv "LIPO_ADMIN_TOKEN")]
    (if (str/blank? token)
      (do
        (log/info "No LIPO_ADMIN_TOKEN, not starting admin routes.")
        (constantly nil))

      (do
        (log/info "Starting admin routes.")
        (routes
         (PUT "/_put/:doc" req
              (with-id req token
                #(if-let [payload (try (binding [*read-eval* false]
                                         (some-> req :body slurp read-string))
                                       (catch Throwable t
                                         (log/warn t "Unable to parse body of document to PUT")
                                         nil))]
                   (db/tx xtdb [:xtdb.api/put (merge payload {:xt/id %})])
                   {:status 400
                    :body "Unable to parse body of document to PUT"})))

         (GET "/_get/:doc" req
              (pprint/pprint req)
              (with-id req token
                (fn [id]
                  {:status 200
                   :headers {"Content-Type" "text/edn"}
                   :body (with-out-str
                           (pprint/pprint (db/entity (xt/db xtdb) id)))}))))))))
