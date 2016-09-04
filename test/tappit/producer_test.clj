(ns tappit.producer-test
  (:require [clojure.test :refer :all]
            [tappit.producer :refer :all]
            [clojure.string :as str]))

(deftest diag-test
  (let [t (atom {})
        out (with-out-str 
              (->diag! t "testing"))]
    (is (re-find #"# testing"
                 out))))
;; (diag-test)

(deftest no-name-ok-test
  (let [t (atom {})
        out (with-out-str
              (->ok! t 1))]
    (is (re-find #"ok 1"
                 out))))
;; (no-name-ok-test)

(deftest named-ok-test
  (let [t (atom {})]
    (let [out (with-out-str
                (->ok! t 1 "everything is ok"))]
      (is (re-find #"ok 1 - everything is ok" out)))
    (let [out (with-out-str
                (->ok! t 1 "still ok"))]
      (is (re-find #"ok 2 - still ok"
                   out)))))
;; (named-ok-test)

(deftest no-name-not-ok-test
  (let [t (atom {})]
    (let [out (with-out-str
                (->ok! t false "always fails"))]
      (is (re-find #"not ok 1 - always fails"
                   out)))))
;; (no-name-not-ok-test)


