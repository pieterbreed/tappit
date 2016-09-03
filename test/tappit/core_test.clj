(ns tappit.core-test
  (:require [clojure.test :refer :all]
            [tappit.core :as tap]))

(deftest ok1-example
  (let [output 
        (with-out-str
          (tap/with)
          )]))

