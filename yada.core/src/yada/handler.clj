;; Copyright © 2014-2016, JUXT LTD.

(ns yada.handler
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [manifold.deferred :as d]
            [yada.error :as error]
            [yada.method :refer [perform-method]]
            [yada.context :as ctx]
            [yada.spec :refer [validate]]
            [yada.status :refer [status]]
            [yada.profile :as profile]))

(s/def :yada.handler/interceptor
  (s/with-gen fn? #(gen/return identity)))

(s/def :yada.handler/interceptor-chain
  (s/coll-of :yada.handler/interceptor))

(s/def :yada/handler
  (s/keys :req [:yada/resource
                :yada.handler/interceptor-chain
                :yada/profile]
          :opt [:yada.handler/error-interceptor-chain]))

(defn ^:interceptor terminate [ctx]
  (ctx/->ring-response ctx))

(defn wrap-interceptors [chain wrapper]
  (if wrapper (map wrapper chain) chain))

(defn apply-interceptors [ctx]
  ;; Have to validate the ctx here, it's the last chance.
  ;; Can't do this in prod
  (when (and (profile/validate-context? ctx))
    (validate ctx :yada/context "Context is not valid"))

  (let [chain (:yada.handler/interceptor-chain ctx)]
    (->
     (apply d/chain ctx (wrap-interceptors (concat chain [terminate]) (profile/interceptor-wrapper ctx)))
     (d/catch Exception
         (fn [e]
           (->
            (apply d/chain
                   (assoc ctx :yada/error e)
                   (or (:yada.handler/error-interceptor-chain ctx)
                       [error/handle-error terminate]))
            (d/catch Exception
                (fn [e] ;; TODO: Error in error chain, log the original error and politely return a 500
                  (println "Error in error handling")
                  e))))))))

(defrecord Handler []
  clojure.lang.IFn
  (invoke [this req]
    (apply-interceptors (ctx/new-context (into {:ring/request req} this)))))

(defn new-handler [model]
  (map->Handler model))

(defn accept-request [^Handler handler req]
  (.invoke handler req))
