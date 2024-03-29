(ns nlogn.core
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
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
                              :port nil}))

(defn wrap-logging [handler]
  (fn [req]
    (println (:request-method req) (:uri req))
    (handler req)))

(defn wrap-error-page [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500
            :headers {"Content-Type" "text/html"}
            :body (slurp (io/resource "500.html"))}))))

(defn start-server! [port opts]
  (println "running nlogn server on port" port)
  (ctnt/load-config! opts)
  (reset! server-handle
          {:server (server/run-server
                    (-> (handler/make-app)
                        (wrap-logging)
                        ((if (env :production)
                           wrap-error-page
                           trace/wrap-stacktrace))
                        ;; TODO: heroku config:add SESSION_SECRET=$RANDOM_16_CHARS
                        (site {:session {:store
                                         (cookie/cookie-store
                                          {:key (env :session-secret)})}}))
                    {:port port :join? false})
           :port port
           :options opts}))

(defn stop-server! []
  ((:server @server-handle)))

(defn restart-server! []
  (stop-server!)
  (start-server! (:port @server-handle)
                 (:options @server-handle)))

(def cli-options
  [["-p" "--port PORT" "The port to listen on."]
   ["-c" "--config PATH" "The path to the config file."]
   ["-C" "--enable-cache" "Enable caching of rendered posts."]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)
        port (Integer. (or (:port options) (env :port) 5000))]
    (start-server! port options)))
