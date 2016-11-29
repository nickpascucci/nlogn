(ns nlogn.core
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [environ.core :refer [env]]
            [nlogn.content :as ctnt]
            [nlogn.handler :as handler]
            [nlogn.database :as db]
            [org.httpkit.server :as server]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.stacktrace :as trace])
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

(defn start-server! [opts port db-user db-pass db-host db-port]
  (println "Running nlogn server on port" port)
  (db/init-db! db-user db-pass db-host db-port)
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
                 (:path (:options @server-handle))))

(def cli-options
  [["-p" "--port PORT" "The port to listen on."]
   ["-c" "--config PATH" "The path to the config file."]
   ["-C" "--enable-cache" "Enable caching of rendered posts."]
   [nil "--db-user USER" "The database user."]
   [nil "--db-pass PASSWORD" "The database password."]
   [nil "--db-host HOST" "The database host."]
   [nil "--db-port PORT" "The database port."]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)
        port (Integer. (or (:port options) (env :port) 5000))
        db-user (or (:db-user options) (env :db-user))
        db-pass (or (:db-pass options) (env :db-pass))
        db-host (or (:db-host options) (env :db-host))
        db-port (Integer. (or (:db-port options) (env :db-port) 5432))]
    (start-server! options port db-user db-pass db-host db-port)))
