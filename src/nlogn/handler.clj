(ns nlogn.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [nlogn.content :as ctnt]
            [liberator.core :refer [resource defresource]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.util.response :as resp]))

(defresource post-resource [post-id]
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :exists? (fn [_]
             (when-let [p (ctnt/get-post post-id)]
               {::data p
                ::id post-id}))
  :handle-ok ::data)

(defroutes app-routes
  (GET "/posts/:post-id" [post-id] (post-resource post-id))
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found (resp/resource-response "404.html" {:root "public"})))

(def app (-> app-routes
             (wrap-json-response)
             (wrap-json-body {:keywords? true})
             (handler/api)))
