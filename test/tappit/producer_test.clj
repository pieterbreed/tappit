(ns tappit.producer-test
  (:require [tappit.producer :refer :all]
            [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as tcgen]
            [clojure.test.check.properties :as tcprop]
            [clojure.test.check.clojure-test :as tct]))

;; ----------------------------------------
;; these are all high-level, client-api tests

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
                               (ok! x :diag (str x)))))))]
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

(tct/defspec ok-overloads-test
  100
  (tcprop/for-all
   [x tcgen/s-pos-int]
   (let [output (with-out-str
                  (with-tap!
                    (dorun (for [x (range x)]
                             (do
                               (ok! x (str "name" x))
                               (ok! x (str "name" x))
                               (ok! x (str "name" x) :diag (str "diag" x))
                               (ok! x (str "name" x) :skip (str "skip" x))
                               (ok! x (str "name" x) :todo (str "todo" x))
                               )))))]

     ;; example output (x = 2)
     ;; --------------
     ;; ok 1 - name0
     ;; ok 2 - name0
     ;; ok 3 - name0 # diag0
     ;; ok 4 - name0 # SKIP skip0
     ;; ok 5 - name0 # TODO todo0
     ;; ok 6 - name1
     ;; ok 7 - name1
     ;; ok 8 - name1 # diag1
     ;; ok 9 - name1 # SKIP skip1
     ;; ok 10 - name1 # TODO todo1
     ;; # 
     ;; # ----------------------------------------
     ;; 1..10
     ;; # All good

     ;; testing strategy
     ;; ----------------
     ;; we create a long list of functions (all taking 0 params).
     ;; they must all return true for this test to pass.
     ;; first we create a bunch of fns for each x,
     ;; then a few for the whole thing,
     ;; then we combine them (with concat) and test them all.
     (as-> x $
       
       ;; regex tests for each x
       (for [i (range x)]
         (list
          #(-> (re-pattern (str "ok \\d+ - name" x))
               (re-seq output)
               count
               (= 2))
          #(boolean (re-find
                     (re-pattern (str "ok \\d+ - name" x " # diag" x))
                     output))
          #(boolean (re-find
                     (re-pattern (str "ok \\d+ - name" x " # SKIP skip" x))
                     output))
          #(boolean (re-find
                     (re-pattern (str "ok \\d+ - name" x " # TODO todo" x))
                     output))))

       ;; "flatten"
       (reduce concat (list) $)

       ;; regex tests for the entire output
       (concat $ (list #(boolean (re-find
                                  (re-pattern "1\\.\\..")
                                  output))
                       #(boolean (re-find
                                  (re-pattern "All good")
                                  output))))

       ;; run all the tests, filter failures, count them up
       (map #(%) $)
       (filter nil? $)
       (count $)

       ;; assert there are none
       (= 0 $)))))

;; ----------------------------------------

(tct/defspec isa-overloads-test
  100
  (tcprop/for-all
   [x tcgen/s-pos-int]
   (let [output (with-out-str
                  (with-tap!
                    (dorun (for [x (range x)]
                             (do
                               (isa! x integer?)
                               (isa! x integer? (str "name" x))

                               (isa! x integer?
                                     (str "name" x))
                               (isa! x integer?
                                     :diag (str "diag" x))
                               (isa! x integer?
                                     :skip (str "skip" x))
                               (isa! x integer?
                                     :todo (str "todo" x))


                               (isa! x integer? (str "name" x)
                                     :diag (str "diag" x))
                               (isa! x integer? (str "name" x)
                                     :skip (str "skip" x))
                               (isa! x integer? (str "name" x)
                                     :todo (str "todo" x))

                               )))))]

     ;; example output (x = 2)
     ;; --------------
     ;; ok 1
     ;; ok 2 - name0
     ;; ok 3 - name0
     ;; ok 4 # diag0
     ;; ok 5 # SKIP skip0
     ;; ok 6 # TODO todo0
     ;; ok 7 - name0 # diag0
     ;; ok 8 - name0 # SKIP skip0
     ;; ok 9 - name0 # TODO todo0
     ;; ok 10
     ;; ok 11 - name1
     ;; ok 12 - name1
     ;; ok 13 # diag1
     ;; ok 14 # SKIP skip1
     ;; ok 15 # TODO todo1
     ;; ok 16 - name1 # diag1
     ;; ok 17 - name1 # SKIP skip1
     ;; ok 18 - name1 # TODO todo1
     ;; # 
     ;; # ----------------------------------------
     ;; 1..18
     ;; # All good

     ;; testing strategy
     ;; ----------------
     ;; we create a long list of functions (all taking 0 params).
     ;; they must all return true for this test to pass.
     ;; first we create a bunch of fns for each x,
     ;; then a few for the whole thing,
     ;; then we combine them (with concat) and test them all.
     (as-> x $
       
       ;; regex tests for each x
       (for [i (range x)]
         (list
          #(-> (re-pattern (str "ok \\d+ - name" x "\n"))
               (re-seq output)
               count
               (= 2))

          #(boolean (re-find
                     (re-pattern (str "ok \\d+ # diag" x))
                     output))
          #(boolean (re-find
                     (re-pattern (str "ok \\d+ # SKIP skip" x))
                     output))
          #(boolean (re-find
                     (re-pattern (str "ok \\d+ # TODO todo" x))
                     output))

          #(boolean (re-find
                     (re-pattern (str "ok \\d+ - name" x " # diag" x))
                     output))
          #(boolean (re-find
                     (re-pattern (str "ok \\d+ - name" x " # SKIP skip" x))
                     output))
          #(boolean (re-find
                     (re-pattern (str "ok \\d+ - name" x " # TODO todo" x))
                     output))

          ))

       ;; "flatten"
       (reduce concat (list) $)

       ;; regex tests for the entire output
       (concat $ (list #(boolean (re-find
                                  (re-pattern "1\\.\\..")
                                  output))
                       #(boolean (re-find
                                  (re-pattern "All good")
                                  output))))

       ;; run all the tests, filter failures, count them up
       (map #(%) $)
       (filter nil? $)
       (count $)

       ;; assert there are none
       (= 0 $)))))

;; ----------------------------------------

(tct/defspec =-overloads-test
  100
  (tcprop/for-all
   [x tcgen/s-pos-int]
   (let [output (with-out-str
                  (with-tap!
                    (dorun (for [x (range x)]
                             (do
                               (=! x x)
                               (=! x x (str "name" x))

                               (=! x x
                                   (str "name" x))
                               (=! x x
                                   :diag (str "diag" x))
                               (=! x x
                                   :skip (str "skip" x))
                               (=! x x
                                   :todo (str "todo" x))

                               (=! x x (str "name" x)
                                   :diag (str "diag" x))
                               (=! x x (str "name" x)
                                   :skip (str "skip" x))
                               (=! x x (str "name" x)
                                   :todo (str "todo" x))

                               )))))]

     ;; example output (x = 2)
     ;; --------------
     ;; ok 1
     ;; ok 2 - name0
     ;; ok 3 - name0
     ;; ok 4 # diag0
     ;; ok 5 # SKIP skip0
     ;; ok 6 # TODO todo0
     ;; ok 7 - name0 # diag0
     ;; ok 8 - name0 # SKIP skip0
     ;; ok 9 - name0 # TODO todo0
     ;; ok 10
     ;; ok 11 - name1
     ;; ok 12 - name1
     ;; ok 13 # diag1
     ;; ok 14 # SKIP skip1
     ;; ok 15 # TODO todo1
     ;; ok 16 - name1 # diag1
     ;; ok 17 - name1 # SKIP skip1
     ;; ok 18 - name1 # TODO todo1
     ;; # 
     ;; # ----------------------------------------
     ;; 1..18
     ;; # All good

     ;; testing strategy
     ;; ----------------
     ;; we create a long list of functions (all taking 0 params).
     ;; they must all return true for this test to pass.
     ;; first we create a bunch of fns for each x,
     ;; then a few for the whole thing,
     ;; then we combine them (with concat) and test them all.
     (as-> x $
       
       ;; regex tests for each x
       (for [i (range x)]
         (list
          #(-> (re-pattern (str "ok \\d+ - name" x "\n"))
               (re-seq output)
               count
               (= 2))

          #(boolean (re-find
                     (re-pattern (str "ok \\d+ # diag" x))
                     output))
          #(boolean (re-find
                     (re-pattern (str "ok \\d+ # SKIP skip" x))
                     output))
          #(boolean (re-find
                     (re-pattern (str "ok \\d+ # TODO todo" x))
                     output))

          #(boolean (re-find
                     (re-pattern (str "ok \\d+ - name" x " # diag" x))
                     output))
          #(boolean (re-find
                     (re-pattern (str "ok \\d+ - name" x " # SKIP skip" x))
                     output))
          #(boolean (re-find
                     (re-pattern (str "ok \\d+ - name" x " # TODO todo" x))
                     output))

          ))

       ;; "flatten"
       (reduce concat (list) $)

       ;; regex tests for the entire output
       (concat $ (list #(boolean (re-find
                                  (re-pattern "1\\.\\..")
                                  output))
                       #(boolean (re-find
                                  (re-pattern "All good")
                                  output))))

       ;; run all the tests, filter failures, count them up
       (map #(%) $)
       (filter nil? $)
       (count $)

       ;; assert there are none
       (= 0 $)))))
