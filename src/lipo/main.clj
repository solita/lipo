(ns lipo.main
  (:require [org.httpkit.server :as server]
            [org.httpkit.client :as htclient]
            [compojure.core :refer [routes GET POST]]
            [compojure.route :as route]
            [crux.api :as crux]
            [ripley.html :as h]
            [ripley.live.context :as context]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]
            [clojure.java.io :as io]
            [ring.middleware.params :as params]
            [ring.middleware.session :as session]
            [co.deps.ring-etag-middleware :as ring-etag-middleware]
            [ring.middleware.not-modified :as not-modified]
            [cheshire.core :as cheshire]
            [ripley.live.source :as source]
            [lipo.content-db :as content-db]
            [lipo.attachments :as attachments]
            [lipo.template :as template]
            [clojure.core.async :as async :refer [go <! timeout]]
            [lipo.admin :as admin]
            [lipo.localization :as localization]
            [lipo.auth :as auth]

            ;; Require portlet implementations
            lipo.portlet.page-tree
            lipo.portlet.view
            lipo.portlet.breadcrumbs
            lipo.portlet.search
            lipo.portlet.news
            lipo.portlet.menu
            lipo.portlet.list
            lipo.portlet.user-menu))

(defonce server (atom nil))



(defn page-context [{:keys [request crux] :as ctx}]
  ;; FIXME: should this have a protocol or at least
  ;; well documented keys
  (let [[source set-msg!] (source/use-state nil)
        [here-source set-here!] (source/use-state (:uri request))]
    (merge ctx
           {:here (:uri request)
            :here-source here-source
            :db (crux/db crux)

            ;; Pages can use go! to send user's browser to a page
            :go! set-here!

            :flash-message-source source
            :set-flash-message! #(go
                                   (set-msg! %)
                                   (<! (timeout 3000))
                                   (set-msg! {}))})))

(defn- render-page [ctx]
  (let [{:keys [db here]} (page-context ctx)]
    (when (content-db/content-id db here)
      (h/render-response
       (localization/with-language-fn
         (or (get-in ctx [:request :session :language]) localization/*language*)
         (fn []
           (template/main-template (page-context ctx))))))))

(defn app-routes [{auth :auth :as ctx}]
  (routes
   (attachments/attachment-routes ctx)
   (context/connection-handler "/__ripley-live" :ping-interval 45)
   (GET "/_health" _req {:status 200 :body "ok"})
   (-> (route/resources "/")
       ring-etag-middleware/wrap-file-etag
       not-modified/wrap-not-modified)
   (admin/admin-routes ctx)

   ;; Last route, if no other route matches, this is a page path
   ;; either save it (when POST) or display it (when GET)
   (fn [{m :request-method user :user :as req}]
     (let [ctx (assoc ctx :request req)]
       (when (and (= m :get) ((:can-view? auth) user))
         (render-page (merge ctx
                             {:can-edit? ((:can-edit? auth) user)})))))

   ;; Fallback, debug log requests that were not handled
   (fn [req]
     (log/debug "Unhandled request:" req)
     nil)))

(defn load-config []
  (-> "config.edn" slurp read-string))

(def crux (atom nil))

(defn init-crux [old-crux config]
  (if old-crux
    (do
      (log/info "CRUX already started, not restarting it.")
      old-crux)
    (let [{:keys [postgresql lmdb-dir lucene-dir]} config]
      (crux/start-node
       (if postgresql
         {:crux.jdbc/connection-pool
          {:dialect {:crux/module 'crux.jdbc.psql/->dialect}
           :pool-opts {}
           :db-spec postgresql}
          :crux/tx-log
          {:crux/module 'crux.jdbc/->tx-log
           :connection-pool :crux.jdbc/connection-pool
           :poll-sleep-duration (java.time.Duration/ofSeconds 1)}
          :crux/document-store
          {:crux/module 'crux.jdbc/->document-store
           :connection-pool :crux.jdbc/connection-pool}

          :crux/index-store
          {:kv-store {:crux/module 'crux.lmdb/->kv-store
                      :db-dir (io/file lmdb-dir)}}
          :crux.lucene/lucene-store {:db-dir lucene-dir}}

         (do
           (log/warn "################################\n"
                     "No :postgresql in configuration, using IN-MEMORY persistence only!\n"
                     "################################")
           {}))))))

(defn init-server [old-server {:keys [port bind-address]
                               :or {port 3000
                                    bind-address "127.0.0.1"}
                               :as config}]
  (when old-server
    (old-server))
  (let [ctx (-> {:crux @crux}
                (attachments/configure-attachment-storage config)
                (auth/configure-auth config))
        server
        (server/run-server
         (-> (app-routes ctx)
             (auth/wrap-auth config)
             params/wrap-params
             session/wrap-session)
         {:port port
          :ip bind-address})]
    (log/info "http server start succeeded - listening on port" port)
    (log/info "http health check self test status:"
              (:status  @(htclient/get (str "http://localhost:" port "/_health"))))
    server))

(defn- merge-config [& config-maps]
  (apply merge-with
         (fn [a b]
           (if (and (map? a) (map? b))
             (merge-config a b)
             b))
         config-maps))

(defn start
  ([]
   (start nil))
  ([env-config-map]
   (let [config (merge-config (load-config) env-config-map)
         _ (log/info "pg connection map:" (assoc (:postgresql config) :password "<redacted>"))]
     (def *config config) ; debug: store full config for repl use
     (swap! crux init-crux config)
     (swap! server init-server config))))



(defn read-db-secrets-from-env
  "Read DB secrets from JSON string injected as env var.
  Contains fields: host, port, dbname, username, password, dbClusterIdentifier and engine."
  [env-var]
  (let [{:keys [host port dbname username password]}
        (cheshire/decode env-var  keyword)]
    {:host host :port port
     :user username :password password
     :dbname dbname}))

(defn load-env-config []
  {:postgresql (read-db-secrets-from-env (System/getenv "LIPODB_SECRET"))

   ;; Read S3 bucket name injected as env var
   :attachments-bucket (System/getenv "ATTACHMENTS_NAME")})

(defn main [& config-maps]
  (log/info "LIPO is starting up.")
  (try
    (log/set-level! :info)
    (log/merge-config!
     {:appenders
      {:spit (appenders/spit-appender {:fname "lipo.log"})}})
    (start (apply merge-config (load-env-config) config-maps))
    (catch Throwable t
      (log/error t "LIPO FAILED TO START")
      (System/exit 1))))
