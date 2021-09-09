(ns lipo.attachments
  "Upload and download of attachments (images and files).
  Bytes are stored to S3, metadata to XTDB"
  (:require [xtdb.api :as xt]
            [compojure.core :refer [POST GET routes]]
            [cheshire.core :as cheshire]
            [ring.middleware.multipart-params :as multipart-params]
            [clojure.java.io :as io]
            [cognitect.aws.client.api :as aws]
            [taoensso.timbre :as log]
            [lipo.db :as db]
            [lipo.meta-model :as meta-model])
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
  (get-object [this id])
  (delete-object [this id]))

(defrecord S3AttachmentStorage [bucket]
  AttachmentStorage
  (put-object [_ id body]
    (with-open [in (io/input-stream body)]
      (invoke :PutObject {:Bucket bucket
                          :Key (str id)
                          :Body in})))
  (get-object [_ id]
    (:Body (invoke :GetObject {:Bucket bucket
                               :Key (str id)})))
  (delete-object [_ id]
    (invoke :DeleteObject {:Bucket bucket
                           :Key (str id)})))

(defrecord LocalAttachmentStorage [path]
  AttachmentStorage
  (put-object [_ id body]
    (io/copy body (io/file (str path id))))
  (get-object [_ id]
    (java.io.ByteArrayInputStream.
     (with-open [out (java.io.ByteArrayOutputStream.)]
       (io/copy (io/file path (str id)) out)
       (.toByteArray out))))
  (delete-object [_ id]
    (io/delete-file (io/file path (str id)))))


(defn- upload [{storage ::storage :keys [xtdb request]}]
  (def *req request)
  (if-let [upload (get-in request [:multipart-params "upload"])]
    (let [id (UUID/randomUUID)]
      (put-object storage id (:tempfile upload))
      (db/tx xtdb
             [:xtdb.api/put
              (merge
               {:xt/id id
                :file/name (:filename upload)
                :file/size (:size upload)
                :file/content-type (:content-type upload)}
               (meta-model/creation-meta "FIXME:user")
               (when-let [content-id (get-in request [:multipart-params "content-id"])]
                 {:file/content (java.util.UUID/fromString content-id)}))])
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (cheshire/encode
              {:url (str "/_img?id=" id)})})

    ;; Didn't get a valid multipart upload request
    {:status 400
     :headers {"Content-Type" "application/json"}
     :body (cheshire/encode
            {:error {:message "No file to upload"}})}))

(defn- download [{storage ::storage :keys [xtdb request]}]
  (let [id (some-> request :params (get "id") UUID/fromString)
        {:file/keys [content-type]} (when id
                                      (xt/entity (xt/db xtdb) id))]
    (if (and id content-type)
      {:status 200
       :headers {"Content-Type" content-type}
       :body (get-object storage id)}
      {:status 404
       :body "Not found"})))

(defn configure-attachment-storage
  "Create an attachments storage "
  [ctx {attachments-bucket :attachments-bucket :as _config}]
  (merge
   ctx
   {::storage
    (if attachments-bucket
      (do
        (log/info "Using S3 storage, bucket: " attachments-bucket)
        (->S3AttachmentStorage attachments-bucket))
      (do
        (log/info "Using local file system attachment storage.")
        (->LocalAttachmentStorage "resources/public/")))}))

(defn attachment-routes
  "Return route handlers for attachment upload/download."
  [ctx]
  (multipart-params/wrap-multipart-params
   (routes
    (POST "/_upload" req
          (upload (assoc ctx :request req)))
    (GET "/_attachment" req
         (download (assoc ctx :request req)))
    ;; Support image as well
    (GET "/_img" req
         (download (assoc ctx :request req))))))


(defn delete!
  "Delete file metadata from XTDB and the file bytes from storage."
  [{storage ::storage xtdb :xtdb} id]
  {:pre [(uuid? id)]}
  (db/tx xtdb [:xtdb.api/delete id])
  (delete-object storage id)
  :ok)
