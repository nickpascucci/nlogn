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
             (when (ctnt/has-post? post-path)
               (println "Rendering" post-path)
               {::data (ctnt/render-post post-path)
                ::id post-path}))
  :handle-not-found (ring-response
                     (resp/resource-response "404.html" {:root "public"}))
  :handle-ok ::data)

(defresource page-resource [path]
  :available-media-types ["text/html"]
  :allowed-methods [:get]
  :exists? (fn [_]  (when (ctnt/has-page? path)
                     {::data (ctnt/render-page path)}))
  :handle-not-found (ring-response
                     (resp/resource-response "404.html" {:root "public"}))
  :handle-ok ::data)

(defresource category-resource [category]
  :available-media-types ["text/html"]
  :allowed-methods [:get]
  :exists? (fn [_] (when-let [page (ctnt/render-category category)]
                    {::data page}))
  :handle-not-found (ring-response
                     (resp/resource-response "404.html" {:root "public"}))
  :handle-ok ::data)

(defresource archive-resource
  :available-media-types ["text/html"]
  :allowed-methods [:get]
  :exists? (fn [_] {::data (ctnt/render-archive)})
  :handle-not-found (ring-response
                     (resp/resource-response "404.html" {:root "public"}))
  :handle-ok ::data)

(defresource index-resource
  :available-media-types ["text/html"]
  :allowed-methods [:get]
  :exists? (fn [_] {::data (ctnt/render-index)})
  :handle-not-found (ring-response
                     (resp/resource-response "404.html" {:root "public"}))
  :handle-ok ::data)

(defresource feed-resource
  :available-media-types ["text/xml"]
  :allowed-methods [:get]
  :exists? (fn [_] {::data (ctnt/render-feed)})
  :handle-not-found (ring-response
                     (resp/resource-response "404.html" {:root "public"}))
  :handle-ok ::data)

(defn- make-blog-routes []
  (routes
   (GET "/atom.xml" [] feed-resource)
   (GET "/config/reload" [] config-resource)
   (GET "/blog/archives" [] archive-resource)
   (GET "/blog/categories/:category" [category] (category-resource category))
   (GET "/blog/*" {{path :*} :params} (post-resource path))
   (GET "/" [] index-resource)))

(defn- make-static-routes []
  (routes
   (route/files "/res/" {:root (get-in @ctnt/config [:settings :resource-path])})))

(defn- make-page-routes []
  (routes
   (GET "/*" {{path :*} :params} (page-resource (str "/" path)))))

(defn make-app []
  (-> (routes
       (make-blog-routes)
       (make-static-routes)
       (make-page-routes)
       (route/not-found (resp/resource-response "404.html" {:root "public"})))
      (wrap-json-response)
      (wrap-json-body {:keywords? true})
      (handler/api)))
