(ns nlogn.content
  (:require [clj-time.core :as time]
            [clj-time.format :as tfmt]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
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

(defn- by-tag [posts]
  (reduce (fn [tags post]
            (reduce (fn [tags tag]
                      (assoc tags tag (conj (get tags tag) post)))
                    tags (:tags post)))
          {} posts))

(defn posts []
  "Get all of the posts, ordered from most recent to least recent."
  (by-recency (get-in @config [:settings :posts])))

(defn pages []
  (get-in @config [:settings :pages]))

(defn special-page? [page]
  (contains? [:archive :index] (:content page)))

(defn- get-template [name]
  (println "Getting template for" name)
  (io/reader (get-in @config [:settings :templates name])))

(defn- gen-navi [pages]
  (into []
        (concat [:ul]
                (map (fn [p] [:li [:a {:href (:path p)}
                                  (:title p)]])
                     pages))))

(defn- post-link [post]
  (str "/blog/" (:path post)))

(defn- get-body [item]
  (when (file-exists? (:content item))
    (if (.endsWith (:content item) ".md")
      (hic/html (eph/to-hiccup (epc/mp (slurp (:content item)))))
      (slurp (:content item)))))

(defn- get-item-for-path [items path]
  (println "Searching for item to path" path)
  (first (filter (fn [p] (= (:path p) path)) items)))

(defn- tag-items [post]
  (into [] (map (fn [tag] [:a {:href (str "/blog/categories/" tag)} tag])
                (:tags post))))

(defn- htmlize [e] (str (hic/html e)))

(defn- gen-comments [post]
  [:div#disqus_thread
   [:script
    (str/replace
     "var disqus_config = function () {
this.page.url = \"PAGE_URL\";
this.page.identifier = \"PAGE_IDENTIFIER\";
};
(function() {
var d = document, s = d.createElement('script');
s.src = '//technomerit.disqus.com/embed.js';
s.setAttribute('data-timestamp', +new Date());
(d.head || d.body).appendChild(s);
})();" #"PAGE_URL|PAGE_IDENTIFIER" (str (get-in @config [:settings :site-url]) (:path post)))]
   [:noscript [:i "Please enable JavaScript to view the comments."]]])

(defn- gen-pagination [post prev next]
  (apply str (map htmlize
                  [(if (nil? prev)
                     ""
                     [:a.older {:href (post-link prev)} "Older"])
                   (str (+ 1 (.indexOf (posts) post))
                        " of "
                        (count (posts)))
                   (if (nil? next)
                     ""
                     [:a.newer {:href (post-link next)} "Newer"])])))

(defn- post-template [post prev next]
  (let [template (get-template (:template post))]
    (enlive/template template []
                     [:div.navi] (enlive/html-content (hic/html (gen-navi (pages))))
                     [:h1] (enlive/content (:title post))
                     [:span.author] (enlive/content (:author post))
                     [:time] (enlive/content (render-date (post-date post)))
                     [:time.year] (enlive/content (render-year (post-date post)))
                     [:span#site-author] (enlive/content (get-in @config [:settings :site-author]))
                     [:div.post-body] (enlive/html-content (get-body post))
                     [:div.comments] (enlive/html-content (hic/html (gen-comments post)))
                     [:div.pagination] (enlive/html-content (gen-pagination post prev next))
                     [:span.tags] (enlive/html-content
                                   (str/join ", " (map htmlize (tag-items post)))))))

(defn- get-page [path]
  (get-item-for-path (pages) path))

(defn- page-template [page]
  (println "Rendering page" page)
  (let [template (get-template (:template page))]
    (enlive/template template []
                     [:div.navi] (enlive/html-content (hic/html (gen-navi (pages))))
                     [:h1] (enlive/content (:title page))
                     [:time.year] (enlive/content (render-year (time/now)))
                     [:span#site-author] (enlive/content (get-in @config [:settings :site-author]))
                     [:div.page-body] (enlive/html-content (get-body page)))))

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
                                                            :href (str "/blog/" (:path post))}
                                                        (:title post)])
                                                     posts)))
                                      months))))
                posts-by-recency))))

(defn- dated-page-template [page-title]
  (let [template (get-template (:template (get-page "/blog/archives")))]
    (enlive/template template [posts]
                     [:h1] (enlive/content page-title)
                     [:time.year] (enlive/content (render-year (time/now)))
                     [:span#site-author] (enlive/content (get-in @config [:settings :site-author]))
                     [:div.navi] (enlive/html-content (hic/html (gen-navi (pages))))
                     [:div.archive-items]
                     (enlive/html-content (reduce str (map (fn [e] (hic/html e))
                                                           (archive-items posts)))))))

(defn- index-template [posts]
  (let [post (first (by-recency posts))
        prev (second (by-recency posts))]
    (post-template post prev nil)))

(defn- atom-entry [post]
  (let [post-url (str (get-in @config [:settings :site-url]) "/blog/" (:path post))]
    [:entry
     [:title (:title post)]
     [:updated (render-iso-date (post-date post))]
     [:author [:name (get-in @config [:settings :site-author])]]
     [:link {:href post-url}]
     [:id post-url]
     [:content {:type "html"} (get-body post)]]))

(defn atom-xml [posts]
  (xml/emit-str
   (xml/sexp-as-element
    [:feed {:xmlns "http://www.w3.org/2005/Atom"}
     [:id (get-in @config [:settings :site-url])]
     [:updated (-> posts first post-date render-iso-date)]
     [:title {:type "text"} (get-in @config [:settings :site-title])]
     [:link {:rel "self" :href (str (get-in @config [:settings :site-url]) "/atom.xml")}]
     [:author [:name (get-in @config [:settings :site-author])]]
     (map atom-entry posts)])))

(defn render-feed []
  (atom-xml (posts)))

(defn render-post
  ([path] (let [posts (posts)
                post (get-item-for-path posts path)
                index (.indexOf posts post)
                prev (when (< index (- (count posts) 1))
                       (nth posts (+ index 1)))
                next (when (> index 0)
                       (nth posts (- index 1)))]
            (render-post post prev next)))
  ([post prev next]
   (let [template (post-template post prev next)]
     (reduce str (template)))))

(defn render-page [path]
  (reduce str ((page-template (get-item-for-path (pages) path)))))

(defn render-index []
  (reduce str ((index-template (posts)))))

(defn render-category [category]
  (reduce str ((dated-page-template (str "Category: " category))
               (get (by-tag (posts)) category))))

(defn render-archive []
  (reduce str ((dated-page-template "Archive") (posts))))
