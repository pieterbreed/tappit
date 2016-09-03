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

(deftest ok-not-ok-no-diag
  (is (= (str/join "\n"
                   ["1..15"
                    "ok 1"
                    "ok 2 - everything is ok"
                    "ok 3 - never fails"
                    "ok 4 - is ten ten?"
                    "ok 5 - even ok is ok!"
                    "ok 6 - ok is not the null pointer"
                    "ok 7 - the Truth will set you ok"
                    "ok 8 - and nothing but the truth"
                    "not ok 9 - and we'll know if you lie to us"
                    "ok 10 - 10 is an integer"
                    "ok 11 - and this is an extra test"
                    "not ok 12 - zero is true # TODO be more like Ruby!"
                    "not ok 13 - none is true # SKIP not possible in this universe"
                    "# Looks like you planned 15 tests but ran 13."
                    ""])
         (with-out-str
           (with-tap!

             ;; plan 15 tests, only make 13
             (plan-for! 15)

             (ok! 1)
             (ok! 1 "everything is ok")
             (ok! 0 "never fails")

             (ok! (= 10 10) "is ten ten?")
             (ok! ok "even ok is ok!")
             (ok! (type ok) "ok is not nil. ok is not nothing.")
             (ok! true "the truth will set you ok.")
             (ok! (not false) "and nothing but the truth.")
             (ok! false "and we'll know if you lie to us")

             (ok! (integer? 10) "10 is an integer")
             (ok! (string? "10") "\"10\" is a string")

             (ok! 0 "zero is true" :todo "bonus, is a lot like ruby")
             (ok! nil "nil is true" :skip "not even possible in this universe"))))))
