(defproject slack2md "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-RC2"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-http "3.7.0"]
                 [environ "1.1.0"]
                 [clj-time "0.14.2"]]
  :main ^:skip-aot slack2md.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
