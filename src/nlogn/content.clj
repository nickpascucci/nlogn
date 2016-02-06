(ns nlogn.content
  (:require [clj-time.core :as time]
            [clj-time.format :as tfmt]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [endophile.core :as epc]
            [endophile.hiccup :as eph]
            [hiccup.core :as hic]
            [net.cgrand.enlive-html :as enlive]))

(defonce config (atom {:path ""
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

(defn- file-exists? [path]
  (.exists (clojure.java.io/as-file path)))

(defn- post-date [post]
  "Try to parse the date from a post."
  (let [formats [
                 ;; These formats assume UTC:
                 "yyyy-MM-dd" ;; 2016-02-03
                 "yyyy-MM-dd HH:mm:ss" ;; 2016-02-03 13:37
                 ;; These formats let you specify time zones:
                 "yyyy-MM-dd HH:mm:ss Z" ;; 2016-02-03 13:37 -0500
                 "yyyy-MM-dd HH:mm:ss ZZ" ;; 2016-02-03 13:37 -05:00
                 "yyyy-MM-dd HH:mm:ss ZZZZ" ;; 2016-02-03 13:37 America/New_York
                 ]
        date-str (:date post)]
    (reduce (fn [d fmt]
              (if (nil? d)
                (try
                  (tfmt/parse (tfmt/formatter fmt) date-str)
                  (catch IllegalArgumentException e nil))
                d))
            nil
            formats)))

(defn- render-iso-date [date]
  (tfmt/unparse
   (tfmt/formatter "yyyy-MM-dd'T'HH:mm:ssZZ") date))

(defn- render-date [date]
  (tfmt/unparse
   (tfmt/formatter
    (get-in @config [:settings :date-format])) date))

(defn- render-year [date]
  (tfmt/unparse (tfmt/formatter "yyyy") date))

(defn- render-month [date]
  (tfmt/unparse (tfmt/formatter "MMMM") date))

(defn- compare-dates [a b]
  "Compare two posts and return 1 if a is more recent, -1 if b is more recent, or 0 if they were
  published at the same time."
  (let [a-date (post-date a)
        b-date (post-date b)]
    (if (time/before? a-date b-date) -1
        (if (time/after? a-date b-date) 1
            0))))

(defn- invert [comparator]
  "Reverse the order of a comparator."
  (fn [a b]
    (comparator b a)))

(defn- by-recency [posts]
  "Sort the given posts from most to least recent."
  (sort (invert compare-dates) posts))

(defn posts []
  "Get all of the posts, ordered from most recent to least recent."
  (by-recency (get-in @config [:settings :posts])))

(defn pages []
  (get-in @config [:settings :pages]))

(defn- gen-navi [pages]
  (into []
        (concat [:ul]
                (map (fn [p] [:li [:a {:href (:path p)}
                                  (:title p)]])
                     pages))))

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
                     [:h1] (enlive/content (:title post))
                     [:span.author] (enlive/content (:author post))
                     [:time] (enlive/content (render-date (post-date post)))
                     [:time.year] (enlive/content (render-year (post-date post)))
                     [:span#site-author] (enlive/content (get-in @config [:settings :site-author]))
                     [:div.post-body] (enlive/html-content (:rendered-body post))
                     [:div.navi] (enlive/html-content (hic/html (gen-navi (pages)))))))

(defn render-post [post]
  (let [post (assoc post :rendered-body (get-post-body post))
        template (post-template post)]
    (reduce str (template post))))

(defn get-page [path]
  (get-item-for-path (pages) path))

(defn- partition-by-recency [posts]
  (let [posts (reverse (by-recency posts))]
    (reduce (fn [years post]
              (let [date (post-date post)
                    year (render-year date)
                    month (render-month date)]
                (update-in years [year month] conj post))) {} posts)))

(defn- archive-items [posts]
  (let [posts-by-recency (partition-by-recency posts)]
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
                posts-by-recency))))

(defn- archive-template []
  (let [template-type (:template (get-page "/blog/archives"))
        template-path (get-in @config [:settings :templates template-type])]
    (enlive/template (io/reader template-path) [posts]
                     [:h1] (enlive/content "Archives")
                     [:time.year] (enlive/content (render-year (time/now)))
                     [:span#site-author] (enlive/content (get-in @config [:settings :site-author]))
                     [:div.navi] (enlive/html-content (hic/html (gen-navi (pages))))
                     [:div.archive-items]
                     (enlive/html-content (reduce str (map (fn [e] (hic/html e))
                                                           (archive-items posts)))))))

(defn render-archive []
  (reduce str ((archive-template) (posts))))

(defn- index-template [posts]
  (let [post (first (by-recency posts))
        rendered-post (assoc post :rendered-body (get-post-body post))
        template-type (:template (get-page "/"))
        template-path (get-in @config [:settings :templates template-type])]
    (enlive/template (io/reader template-path) []
                     [:h1] (enlive/content (:title rendered-post))
                     [:span.author] (enlive/content (:author rendered-post))
                     [:time] (enlive/content (render-date (post-date rendered-post)))
                     [:time.year] (enlive/content (render-year (post-date rendered-post)))
                     [:span#site-author] (enlive/content (get-in @config [:settings :site-author]))
                     [:div.post-body] (enlive/html-content (:rendered-body rendered-post))
                     [:div.navi] (enlive/html-content (hic/html (gen-navi (pages)))))))

(defn render-index []
  (reduce str ((index-template (posts)))))

(defn- entry [post]
  (let [post-url (str (get-in @config [:settings :site-url]) "/blog/" (:path post))]
    [:entry
     [:title (:title post)]
     [:updated (render-iso-date (post-date post))]
     [:author [:name (get-in @config [:settings :site-author])]]
     [:link {:href post-url}]
     [:id post-url]
     [:content {:type "html"} (get-post-body post)]]))

(defn atom-xml [posts]
  (xml/emit-str
   (xml/sexp-as-element
    [:feed {:xmlns "http://www.w3.org/2005/Atom"}
     [:id (get-in @config [:settings :site-url])]
     [:updated (-> posts first post-date render-iso-date)]
     [:title {:type "text"} (get-in @config [:settings :site-title])]
     [:link {:rel "self" :href (str (get-in @config [:settings :site-url]) "/atom.xml")}]
     [:author [:name (get-in @config [:settings :site-author])]]
     (map entry posts)])))

(defn generate-feed []
  (atom-xml (posts)))
