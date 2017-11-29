(defproject nlogn "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [clj-time "0.11.0"]
                 [compojure "1.4.0"]
                 [endophile "0.1.2"]
                 [enlive "1.1.6"]
                 [environ "0.2.1"]
                 [hiccup "1.0.5"]
                 [http-kit "2.1.16"]
                 [javax.servlet/servlet-api "2.5"]
                 [liberator "0.10.0"]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.cli "0.3.3"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-devel "1.1.0"]
                 [ring/ring-json "0.2.0"]
                 [speclj "2.9.1"]
                 ]
  :main ^:skip-aot nlogn.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
