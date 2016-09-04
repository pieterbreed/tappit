(ns tappit.producer-test
  (:require [clojure.test :refer :all]
            [tappit.producer :refer :all]
            [clojure.string :as str]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as tcgen]
            [clojure.test.check.properties :as tcprop]
            [clojure.test.check.clojure-test :as tct]))

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


;; ----------------------------------------

(tct/defspec all-ok-statements-accounted-for-1
  1000
  (tcprop/for-all
   [x tcgen/s-pos-int]
   (let [output (with-out-str
                  (with-tap!
                    (dorun (for [x (range x)]
                             (ok! x)))))]
     (and
      (re-find (re-pattern (str "1.." x)) output)
      (as-> x $
        (for [i (range 1 x)]
          (re-pattern (str "ok " i)))
        (map #(re-find % output) $)
        (filter nil? $)
        (count $)
        (= 0 $))))))

(tct/defspec inline-diags-accounted-for-1
  1000
  (tcprop/for-all
   [x tcgen/s-pos-int]
   (let [output (with-out-str
                  (with-tap!
                    (dorun (for [x (range x)]
                             (do
                               (ok! x)
                               (diag! (str x)))))))]
     (and (re-find (re-pattern (str "1.." x)) output)
          (as-> x $
            (for [i (range 1 x)]
              (re-pattern (str "ok " i " # " (dec i))))
            (map #(re-find % output) $)
            (filter nil? $)
            (count $)
            (= 0 $))))))

(tct/defspec nextline-diags-accounted-for-todos-Allgood-1
  1000
  (tcprop/for-all
   [x tcgen/s-pos-int]
   (let [output (with-out-str
                  (with-tap!
                    (plan-for! x)
                    (dorun (for [x (range x)]
                             (do
                               (ok! x :todo (str "todo" x))
                               (diag! (str x)))))))]
     (as-> x $
       (for [i (range 1 x)]
         (list (re-pattern (str "[^#]+# TODO todo" (dec i)))
               (re-pattern (str "# " (dec i)))))
       (reduce concat (list) $)
       (concat $ (list (re-pattern (str "1.." x))
                       (re-pattern "All good")))
       (map #(re-find % output) $)
       (filter nil? $)
       (count $)
       (= 0 $)))))
