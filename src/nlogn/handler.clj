(ns nlogn.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [nlogn.content :as ctnt]
            [liberator.core :refer [resource defresource]]
            [liberator.representation :refer [ring-response]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.util.response :as resp]))

(defresource config-resource
  :available-media-types ["text/html"]
  :allowed-methods [:get]
  :exists? (fn [_]
             (println "Config reload requested")
             (when-let [c (ctnt/reload-config!)]
               {::data (str c)}))
  :handle-ok ::data)

(defresource post-resource [post-path]
  :available-media-types ["text/html"]
  :allowed-methods [:get]
  :exists? (fn [_]
             (println "Checking for" post-path)
             (if-let [post (ctnt/get-post-for-path post-path)]
               {::data (ctnt/render-post post)
                ::id post-path}))
  :handle-not-found (ring-response
                     (resp/resource-response "404.html" {:root "public"}))
  :handle-ok ::data)

(defn make-routes []
  (routes
   (GET "/config/reload" [] config-resource)
   (GET "/blog/*" {{path :*} :params} (post-resource path))
   (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
   (route/files "/" {:root (get-in @ctnt/config [:settings :resource-path])})
   (route/resources "/")
   (route/not-found (resp/resource-response "404.html" {:root "public"}))))

(defn make-app []
  (-> (make-routes)
      (wrap-json-response)
      (wrap-json-body {:keywords? true})
      (handler/api)))
