(ns nlogn.core
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [clojure.java.io :as io]
            [compojure.handler :refer [site]]
            [org.httpkit.server :as server]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.stacktrace :as trace]
            [nlogn.handler :as handler]
            [environ.core :refer [env]]
            [compojure.route :as route])
  (:gen-class))

(defonce server-handle (atom {:server nil
                              :port nil}))

(defn wrap-error-page [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500
            :headers {"Content-Type" "text/html"}
            :body (slurp (io/resource "500.html"))}))))

(defn start-server! [port]
  (println "running nlogn server on port" port)
  (swap! server-handle assoc :server
         (server/run-server
          (-> #'handler/app
              ((if (env :production)
                 wrap-error-page
                 trace/wrap-stacktrace))
              ;; TODO: heroku config:add SESSION_SECRET=$RANDOM_16_CHARS
              (site {:session {:store
                               (cookie/cookie-store
                                {:key (env :session-secret)})}}))
          {:port port :join? false})
         :port port))

(defn stop-server! []
  ((:server @server-handle)))

(defn restart-server! []
  (stop-server!)
  (start-server! (:port @server-handle)))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (start-server! port)))
