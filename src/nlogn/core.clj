(ns nlogn.core
  (:require [clojure.java.io :as io]
            [compojure.handler :refer [site]]
            [org.httpkit.server :as server]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.stacktrace :as trace]
            [nlogn.handler :as handler]
            [nlogn.content :as ctnt]
            [environ.core :refer [env]]
            [compojure.route :as route])
  (:gen-class))

(defonce server-handle (atom {:server nil
                              :port nil
                              :cfg-path nil}))

(defn wrap-error-page [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500
            :headers {"Content-Type" "text/html"}
            :body (slurp (io/resource "500.html"))}))))

(defn start-server! [port cfg-path]
  (println "running nlogn server on port" port)
  (ctnt/load-config! cfg-path)
  (swap! server-handle assoc :server
         (server/run-server
          (-> (handler/make-app)
              ((if (env :production)
                 wrap-error-page
                 trace/wrap-stacktrace))
              ;; TODO: heroku config:add SESSION_SECRET=$RANDOM_16_CHARS
              (site {:session {:store
                               (cookie/cookie-store
                                {:key (env :session-secret)})}}))
          {:port port :join? false})
         :port port
         :cfg-path cfg-path))

(defn stop-server! []
  ((:server @server-handle)))

(defn restart-server! []
  (stop-server!)
  (start-server! (:port @server-handle)
                 (:cfg-path @server-handle)))

(defn -main [& [port cfg-path]]
  (let [port (Integer. (or port (env :port) 5000))]
    (start-server! port cfg-path)))
