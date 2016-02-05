(ns nlogn.content
  (:require [clj-time.core :as time]
            [clj-time.format :as tfmt]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [endophile.core :as epc]
            [endophile.hiccup :as eph]
            [hiccup.core :as hic]
            [net.cgrand.enlive-html :as enlive]))

(def config (atom {:path ""
                   :settings {:posts []
                              :resource-path ""
                              :templates {}}}))

(defn load-config! [config-path]
  (let [settings (edn/read-string (slurp config-path))]
    (println "Loaded new settings" settings)
    (reset! config {:path config-path
                    :settings settings})))

(defn reload-config! []
  (load-config! (:path @config)))

(defn file-exists? [path]
  (.exists (clojure.java.io/as-file path)))

(defn get-post-body [post]
  (when (file-exists? (:content post))
    (if (.endsWith (:content post) ".md")
      (hic/html (eph/to-hiccup (epc/mp (slurp (:content post)))))
      (slurp (:content post)))))

(defn get-post-for-path [path]
  (let [posts (get-in @config [:settings :posts])
        post (first (filter (fn [p] (= (:path p) path)) posts))]
    (println "Posts:" posts)
    (println "Found post for path:" post)
    post))

(defn- render-date [date]
  ;; TODO Pull format into the config
  (tfmt/unparse (tfmt/formatter "dd MMMM yyyy")
                (tfmt/parse (tfmt/formatter "yyyy-mm-dd") date)))

(defn- render-year [date]
  (tfmt/unparse (tfmt/formatter "yyyy")
                (tfmt/parse (tfmt/formatter "yyyy-mm-dd") date)))

(defn post-template [post]
  (let [template-type (:template post)
        template-path (get-in @config [:settings :templates template-type])]
    (enlive/template (io/reader template-path) [post]
                     ;; [:title] (enlive/content (:title post))
                     ;; TODO publication date
                     [:h1] (enlive/content (:title post))
                     [:span.author] (enlive/content (:author post))
                     [:time] (enlive/content (render-date (:date post)))
                     [:time.year] (enlive/content (render-year (:date post)))
                     [:span#site-author] (enlive/content (get-in @config [:settings :site-author]))
                     [:div.post-body] (enlive/html-content (:rendered-body post)))))

(defn render-post [post]
  (let [post (assoc post :rendered-body (get-post-body post))
        template (post-template post)]
    (reduce str (template post))))
