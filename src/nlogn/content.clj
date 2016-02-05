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
                              :pages []
                              :site-author "A brilliant writer"
                              :date-format "dd MMMM yyyy"
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

(defn- post-date [post]
  (tfmt/parse (tfmt/formatter "yyyy-MM-dd") (:date post)))

(defn- render-date [date]
  (tfmt/unparse
   (tfmt/formatter
    (get-in @config [:settings :date-format])) date))

(defn- render-year [date]
  (tfmt/unparse (tfmt/formatter "yyyy") date))

(defn- render-month [date]
  (tfmt/unparse (tfmt/formatter "MMMM") date))

(defn- compare-dates [a b]
  (let [a-date (post-date a)
        b-date (post-date b)]
    (if (time/before? a-date b-date) -1
        (if (time/after? a-date b-date) 1
            0))))

(defn- by-date [posts]
  (sort compare-dates posts))

(defn posts []
  (get-in @config [:settings :posts]))

(defn pages []
  (get-in @config [:settings :pages]))

(defn get-post-body [post]
  (when (file-exists? (:content post))
    (if (.endsWith (:content post) ".md")
      (hic/html (eph/to-hiccup (epc/mp (slurp (:content post)))))
      (slurp (:content post)))))

(defn get-item-for-path [items path]
  (first (filter (fn [p] (= (:path p) path)) items)))

(defn- post-template [post]
  (let [template-type (:template post)
        template-path (get-in @config [:settings :templates template-type])]
    (enlive/template (io/reader template-path) [post]
                     ;; [:title] (enlive/content (:title post))
                     ;; TODO publication date
                     [:h1] (enlive/content (:title post))
                     [:span.author] (enlive/content (:author post))
                     [:time] (enlive/content (render-date (post-date post)))
                     [:time.year] (enlive/content (render-year (post-date post)))
                     [:span#site-author] (enlive/content (get-in @config [:settings :site-author]))
                     [:div.post-body] (enlive/html-content (:rendered-body post)))))

(defn render-post [post]
  (let [post (assoc post :rendered-body (get-post-body post))
        template (post-template post)]
    (reduce str (template post))))

(defn get-page [path]
  (get-item-for-path (pages) path))

(defn- partition-by-date [posts]
  (let [posts (by-date posts)]
    (reduce (fn [years post]
              (let [date (post-date post)
                    year (render-year date)
                    month (render-month date)]
                (update-in years [year month] conj post))) {} posts)))

(defn- archive-items [posts]
  (let [posts-by-date (partition-by-date posts)]
    (apply concat
           (map (fn [[year months]]
                  (concat [[:div.cat-year year]]
                          (apply concat
                                 (map (fn [[month posts]]
                                        (concat [[:div.cat-month month]]
                                                (map (fn [post]
                                                       [:a {:class "title-listing"
                                                            :href (:path post)}
                                                        (:title post)])
                                                     posts)))
                                      months))))
                posts-by-date))))

(defn- archive-template []
  (let [template-type (:template (get-page "archive"))
        template-path (get-in @config [:settings :templates template-type])]
    (enlive/template (io/reader template-path) [posts]
                     ;; [:title] (enlive/content (:title post))
                     ;; TODO publication date
                     [:h1] (enlive/content "Archives")
                     [:time.year] (enlive/content (render-year (time/now)))
                     [:span#site-author] (enlive/content (get-in @config [:settings :site-author]))
                     [:div.archive-items] (enlive/html-content (reduce str (map (fn [e] (hic/html e))
                                                                         (archive-items posts)))))))

(defn render-archive []
  (reduce str ((archive-template) (posts))))






