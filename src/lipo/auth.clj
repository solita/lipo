(ns lipo.auth
  "OAuth2 and JWT token user auth."
  (:require [ring.middleware.oauth2 :as oauth2]
            [ring.middleware.token :as token]
            [taoensso.timbre :as log]))

(defn- request-jwt-token [req]
  (some-> req
          :oauth2/access-tokens
          first val :id-token))

(defn- wrap-set-session-user-info [handler {:keys [issuers claims->user]}]
  (fn [req]
    (if-let [user (get-in req [:session ::user])]
      ;; User in session already, use it
      (handler (assoc req :user user))

      ;; No user, check if we have id token to parse and set user based on that
      (if-let [token (request-jwt-token req)]
        ;; Have token, set user based on that and set it to session in response
        (let [alg-opts (->> token token/decode-issuer (get issuers))
              claims (when alg-opts
                       (token/decode token alg-opts))
              user (when claims
                     (claims->user claims))
              response (handler (assoc req :user user))]
          (assoc-in response [:session ::user] user))

        ;; No user or token, continue without user
        (handler req)))))

(defn- wrap-force-request-scheme
  "Force request scheme (eg. when behind balancer that terminates SSL)"
  [handler force-request-scheme]
  (if force-request-scheme
    (fn [req]
      (handler (assoc req :scheme force-request-scheme)))
    handler))

(defn configure-auth
  "Add auth configuration to context"
  [ctx {auth :auth :as config}]
  (assoc ctx :auth
         {:can-view? (:can-view? auth (constantly true)) ; by default anyone can view
          :can-edit? (:can-edit? auth some?) ; by default authenticated user can edit
          :login-url (some-> config :auth :oauth2 vals first :launch-uri)}))

(defn wrap-auth [handler {auth :auth :as _config}]
  (if (and (contains? auth :jwt)
           (contains? auth :oauth2))
    (-> handler
        (wrap-set-session-user-info
         (merge (:jwt auth)
                {:claims->user (or (:claims->user auth) identity)}))
        (oauth2/wrap-oauth2 (:oauth2 auth))
        (wrap-force-request-scheme (:force-request-scheme auth)))
    (do
      (log/warn "No OAuth2 and JWT configuration.")
      handler)))
