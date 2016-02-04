(ns nlogn.content
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [endophile.core :as epc]
            [endophile.hiccup :as eph]
            [hiccup.core :as hic]
            [net.cgrand.enlive-html :as enlive]))

(def config (atom {:path ""
                   :settings {:posts [{:path "test"
                                   :content "test/test.md"}]}}))

(defn load-config! [config-path]
  (reset! config {:path config-path
                :settings (edn/read-string (slurp config-path))}))

(defn reload-config! []
  (load-config! (:path @config)))

(enlive/deftemplate post-page "templates/post.html"
  [post]
  [:title] (enlive/content (:title post))
  [:h1] (enlive/content (:title post))
  [:span.author] (enlive/content (:author post))
  [:div.post-body] (enlive/html-content (:rendered-body post)))

(defn render-post [post]
  (let [post (assoc post :rendered-body (hic/html (get-post-body post)))]
    (reduce str (post-page post))))

(defn file-exists? [path]
  (.exists (clojure.java.io/as-file path)))

;; TODO alias everything into the content directory
(defn get-post-body [post]
  (when (file-exists? (:content post))
    (eph/to-hiccup (epc/mp (slurp (:content post))))))

(defn get-post-for-path [path]
  (let [posts (get-in @config [:settings :posts])
        post (first (filter (fn [p] (= (:path p) path)) posts))]
    (println "Posts:" posts)
    (println "Found post for path:" post)
    post))
