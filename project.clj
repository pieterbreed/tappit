(defproject pieterbreed/tappit "0.9.7"
  :description "TAP Help for Clojure. Inspired by https://github.com/rjbs/tapsimple"
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha10"] 
                 [org.clojure/core.async "0.2.385"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]]}}
  :lein-release {:deploy-via :clojars})
