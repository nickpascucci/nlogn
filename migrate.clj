;; Migration script to generate nlogn configs for posts using Octopress-style YAML metadata.

(require 'leiningen.exec)

(leiningen.exec/deps '[[clj-yaml "0.4.0"]
                       [org.clojure/tools.cli "0.3.3"]])

(require '[clj-yaml.core :as yaml]
         '[clojure.string :as string]
         '[clojure.tools.cli :as cli]
         '[clojure.java.io :as io]
         '[clojure.pprint :as pp])

(def cli-options
  [["-d" "--directory DIRECTORY" "The directory containing the posts."]])

(defn trim-dir [dir]
  (if (.endsWith dir "/")
    (.substring dir 0 (- (.length dir) 1))
    dir))

(defn get-posts [post-dir]
  (let [posts (filter (fn [f] (or (.endsWith f ".markdown") (.endsWith f ".md")))
                      (map #(str post-dir "/" (.getName %))
                           (file-seq (io/file post-dir))))]
    (println "Found posts:" posts)
    posts))

(defn parse-post-metadata [post-text]
  (let [metadata (first
                  (filter (comp not string/blank?)
                          (string/split post-text #"---")))]
    (yaml/parse-string metadata)))

(defn trim-extension [path]
  (string/replace path #"\.md|\.markdown" ""))

(defn octo-path [path]
  (let [[year month day & filename] (string/split path #"-")
        title (trim-extension (string/join "-" filename))]
    (str year "/" month "/" day "/" title)))

(defn octo->nlogn [prefix metadata path]
  (let [{:keys [layout title date categories published]} metadata]
    {
     :template layout
     :title title
     :date date
     :published published
     :tags (into [] categories)
     :content path
     :path (octo-path (string/replace-first path (str prefix "/") ""))
     }))

(let [{:keys [options arguments errors summary]}
      (cli/parse-opts *command-line-args* cli-options)
      post-dir (trim-dir (:directory options))]
  (println "Generating config for posts in" post-dir)
  (pp/pprint
   (into []
         (map (fn [post] (octo->nlogn post-dir (parse-post-metadata (slurp post)) post))
              (get-posts post-dir)))))
