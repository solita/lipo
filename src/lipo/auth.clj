(ns lipo.auth
  "OAuth2 and JWT token user auth."
  (:require [ring.middleware.oauth2 :as oauth2]
            [ring.middleware.jwt :as jwt]
            [taoensso.timbre :as log]))

(defn- request-jwt-token [req]
  (some-> req
          :oauth2/access-tokens
          first val :id-token))

(defn- wrap-user-info [handler claims->user]
  (fn [{claims :claims :as req}]
    (let [user (when claims
                 (claims->user claims))]
      (handler (assoc req :user user)))))

(defn wrap-auth [handler {auth :auth :as _config}]
  (if (and (contains? auth :jwt)
           (contains? auth :oauth2))
    (-> handler
        (wrap-user-info (:claims->user auth))
        (jwt/wrap-jwt (merge (:jwt auth)
                             {:find-token-fn request-jwt-token}))
        (oauth2/wrap-oauth2 (:oauth2 auth)))
    (do
      (log/warn "No OAuth2 and JWT configuration.")
      handler)))
