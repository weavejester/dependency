(defproject weavejester/dependency "1.0.0"
  :description "A data structure for representing dependency graphs"
  :url "https://github.com/weavejester/dependency"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0" :scope "provided"]]
  :aliases
  {"test-all" ["with-profile" "default:+1.8:+1.9:+1.10:+1.11:+1.12" "test"]}
  :profiles {:1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0"]]}
             :1.11 {:dependencies [[org.clojure/clojure "1.11.4"]]}
             :1.12 {:dependencies [[org.clojure/clojure "1.12.4"]]}})
