(ns nlogn.content
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(def config (atom {"test" "test/test.md"}))

(defn load-config [config-path]
  (set! config (edn/read (slurp config-path))))

;; TODO alias everything into the content directory
(defn get-post [post-id]
  (slurp (get @config post-id)))
