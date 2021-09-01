(ns lipo.image-upload
  "Upload and download of images.
  Bytes are stored to S3, metadata to CRUX"
  (:require [crux.api :as crux]
            [compojure.core :refer [POST GET routes]]
            [cheshire.core :as cheshire]
            [ring.middleware.multipart-params :as multipart-params]
            [clojure.java.io :as io]
            [ring.util.io :as ring-io]
            [cognitect.aws.client.api :as aws]
            [taoensso.timbre :as log]
            [lipo.db :as db])
  (:import (java.util UUID)))

(def ^:private s3-client (delay (aws/client {:api :s3})))

(defn- invoke [op request]
  (let [response (aws/invoke @s3-client {:op op :request request})]
    (if (:cognitect.anomalies/category response)
      (throw (ex-info "Error in S3 invocation"
                      {:op op
                       :request request
                       :response response}))
      response)))

(defprotocol AttachmentStorage
  (put-object [this id body])
  (get-object [this id]))

(defrecord S3AttachmentStorage [bucket]
  AttachmentStorage
  (put-object [_ id body]
    (with-open [in (io/input-stream body)]
      (invoke :PutObject {:Bucket bucket
                          :Key (str id)
                          :Body in})))
  (get-object [_ id]
    (:Body (invoke :GetObject {:Bucket bucket
                               :Key (str id)}))))

(defrecord LocalAttachmentStorage [path]
  AttachmentStorage
  (put-object [_ id body]
    (io/copy body (io/file (str path id))))
  (get-object [_ id]
    (java.io.ByteArrayInputStream.
     (with-open [out (java.io.ByteArrayOutputStream.)]
       (io/copy (str path id) out)
       (.toByteArray out)))))


(defn upload [{:keys [crux request storage]}]
  (if-let [upload (get-in request [:multipart-params "upload"])]
    (let [id (UUID/randomUUID)]
      (put-object storage id (:tempfile upload))
      (db/tx crux
             [:crux.tx/put
              {:crux.db/id id
               :file/name (:filename upload)
               :file/content-type (:content-type upload)
               ;; FIXME meta fields
               }])
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (cheshire/encode
              {:url (str "/_img?id=" id)})})

    ;; Didn't get a valid multipart upload request
    {:status 400
     :headers {"Content-Type" "application/json"}
     :body (cheshire/encode
            {:error {:message "No file to upload"}})}))

(defn download [{:keys [crux request storage]}]
  (let [id (some-> request :params (get "id") UUID/fromString)
        {:file/keys [content-type]} (when id
                                      (crux/entity (crux/db crux) id))]
    (if (and id content-type)
      {:status 200
       :headers {"Content-Type" content-type}
       :body (get-object storage id)}
      {:status 404
       :body "Not found"})))

(defn image-routes [ctx {attachments-bucket :attachments-bucket}]
  (let [storage (if attachments-bucket
                  (do
                    (log/info "Using S3 storage, bucket: " attachments-bucket)
                    (->S3AttachmentStorage attachments-bucket))
                  (do
                    (log/info "Using local file system attachment storage.")
                    (->LocalAttachmentStorage "resources/public/")))
        ctx (assoc ctx :storage storage)]
    (multipart-params/wrap-multipart-params
     (routes
      (POST "/_upload" req
            (upload (assoc ctx :request req)))
      (GET "/_img" req
           (download (assoc ctx :request req)))))))
