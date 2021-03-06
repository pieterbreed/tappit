(defproject pieterbreed/tappit "0.9.9-SNAPSHOT"
  :description "TAP Help for Clojure. Inspired by https://github.com/rjbs/tapsimple"
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha10"]
                 [me.raynes/conch "0.8.0"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]]}}
  :lein-release {:deploy-via :clojars})
