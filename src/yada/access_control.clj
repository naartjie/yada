;; Copyright © 2015, JUXT LTD.

(ns yada.access-control
  (:require
   [byte-streams :as b]
   [manifold.deferred :as d]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [clojure.data.codec.base64 :as base64]
   [ring.middleware.basic-authentication :as ba]))

(defmulti authenticate-with-scheme
  "Multimethod that allows new schemes to be added."
  (fn [ctx {:keys [scheme]}] scheme))

(defmethod authenticate-with-scheme "Basic"
  [ctx {:keys [authenticator]}]
  (:basic-authentication
   (ba/basic-authentication-request
    (:request ctx)
    (fn [user password]
      (authenticator [user password])))))

;; A nil scheme is simply one that does not use any of the built-in
;; algorithms for IANA registered auth-schemes at
;; http://www.iana.org/assignments/http-authschemes. The authenticator
;; must therefore take the full context and do all the work to
;; authenticate the user from it.
(defmethod authenticate-with-scheme nil
  [ctx {:keys [authenticator]}]
  (when authenticator
    (authenticator ctx)))

(defmethod authenticate-with-scheme :default
  [ctx {:keys [authenticator]}]
  ;; Scheme is not recognised by this server, we must return nil (to
  ;; move to the next scheme). Arguably this is a 400.
  nil)

(defn not-authorized [realm schemes]
  ;; Otherwise, if no authorization header, send a
  ;; www-authenticate header back.
  (d/error-deferred
   (ex-info "" {:status 401
                :headers {"www-authenticate"
                          (apply str (interpose ", "
                                                (for [{:keys [scheme]} schemes]
                                                  (format "%s realm=\"%s\"" scheme realm))))}})))

(defn authenticate [ctx]
  ;; If [:access-control :allow-origin] exists at all, don't block an OPTIONS pre-flight request
  (if (and (= (:method ctx) :options)
           (-> ctx :handler :resource :cors :allow-origin))
    ctx                           ; let through without authentication

    (if-let [auth (get-in ctx [:handler :resource :authentication])]
      (if-let [realm (first (:realms auth))]
        ;; Only supports one realm currently, TODO: support multiple realms as per spec. 7235
        (let [[realm {:keys [schemes]}] realm]
          (-> ctx
              (assoc :user (some (partial authenticate-with-scheme ctx) schemes))
              (update-in [:response :headers]
                         merge {"www-authenticate"
                                (apply str (interpose ", "
                                                      (for [{:keys [scheme]} schemes]
                                                        (format "%s realm=\"%s\"" scheme realm))))})))
        ;; no realms
        ctx)
      ;; no auth      
      ctx

      ;; TODO: Establish authorizations from identity
      

      )))

(defn authorize
  "Given an authenticated user in the context, and the resource
  properties in :properites, check that the user is authorized to do
  what they are about to do."
  [ctx]
  ctx
  )

(defn call-fn-maybe [x ctx]
  (when x
    (if (fn? x) (x ctx) x)))

(defn to-header [v]
  (if (coll? v)
    (apply str (interpose ", " v))
    (str v)))

(defn access-control-headers [ctx]
  (if-let [origin (get-in ctx [:request :headers "origin"])]
    (let [cors (get-in ctx [:handler :resource :cors])
          ;; We can only report one origin, so let's work that out
          allow-origin (let [s (call-fn-maybe (:allow-origin cors) ctx)]
                         (cond
                           (= s "*") "*"
                           (string? s) s
                           ;; Allow function to return a set
                           (ifn? s) (or (s origin)
                                        (s "*"))))]
      
      (cond-> ctx
        allow-origin
        (assoc-in [:response :headers "access-control-allow-origin"] allow-origin)

        (:allow-credentials cors)
        (assoc-in [:response :headers "access-control-allow-credentials"]
                  (to-header (:allow-credentials cors)))

        (:expose-headers cors)
        (assoc-in [:response :headers "access-control-expose-headers"]
                  (to-header (call-fn-maybe (:expose-headers cors) ctx)))

        (:max-age cors)
        (assoc-in [:response :headers "access-control-max-age"]
                  (to-header (call-fn-maybe (:max-age cors) ctx)))

        (:allow-methods cors)
        (assoc-in [:response :headers "access-control-allow-methods"]
                  (to-header (map (comp str/upper-case name) (call-fn-maybe (:allow-methods cors) ctx))))

        (:allow-headers cors)
        (assoc-in [:response :headers "access-control-allow-headers"]
                  (to-header (call-fn-maybe (:allow-headers cors) ctx)))))

    ;; Otherwise
    ctx))