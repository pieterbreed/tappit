(ns tappit.reducer-test
  (:require [tappit.reducer :refer :all]
            [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.spec :as s]
            [clojure.spec.gen :as sgen]
            [clojure.spec.test :as stest]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as tcgen]
            [clojure.test.check.properties :as tcprop]
            [clojure.test.check.clojure-test :as tct]))

(deftest -type-of-first-test
  (is (= :ok1 (-type-of-first {:type :ok1} 1 2 3 4)))
  (is (= :ok2 (-type-of-first {:a 1
                               :b 2
                               :type :ok2} :a :b :c :d))))

;; ----------------------------------------

(defn specced-fn-passes-check
  "Utility function to check that tests pass"
  [& fn-symbol]
  (as-> fn-symbol $
    (stest/check $)
    (stest/summarize-results $)
    (= (:total $)
       (:check-passed $))))

(deftest lines-generators-tests
  (is (specced-fn-passes-check 'tappit.reducer/diag))
  (is (specced-fn-passes-check 'tappit.reducer/bail))
  (is (specced-fn-passes-check 'tappit.reducer/plan)))



;; ----------------------------------------

(defn create-test-tap-reducer [n]
  "Creates a combinator reducer that has n ::tap-reducer-combinator-tests's combined in it. These are dumb reducers, not caring what actual message they receive, just counting them as they go by."
  (apply make-tap-reducer-combinator
         (for [_ (range n)]
           {:type ::tap-reducer-combinator-tests
            :events 0
            :cleaned-up 0})))

(defmethod tap-reducer ::tap-reducer-combinator-tests
  [current _]
  (update-in current [:events] inc))

(defmethod tap-reducer-cleanup ::tap-reducer-combinator-tests
  [current]
  (update-in current [:cleaned-up] inc))

(tct/defspec combinator-reducer-calls-everything-it-should
  1000
  (tcprop/for-all
   [vint (tcgen/vector tcgen/int)]
   (let [n (count vint)

         ;; send n fake messages to n fake reducers
         ;; (through the combinator)
         ;; and then do a cleanup
         r (as-> (create-test-tap-reducer n) $
             (reduce tap-reducer $ vint)
             (tap-reducer-cleanup $))]

     ;; when used as a reducer (on a list of test reducers... lol)
     ;; Tests that:
     ;; - all n messages were passed to it
     ;; - and that cleanup was called once
     (letfn [(failure-counter
               [current-failed next]
               (if (and (= n (:events next))
                        (= 1 (:cleaned-up next)))
                 current-failed
                 (inc current-failed)))]
       (= 0 (reduce failure-counter 0 (:reducers r)))))))

;; ----------------------------------------

(defn test-for-n-occurences-of-regex-in-string
  "Tests whether txt contains exactly n repititions of the regex. 

  (You _probably_ want to use the 2-argument overload.)"
  ([matcher counter dontcallthisoverload]
   (if (re-find matcher) (recur matcher (inc counter) nil)
       counter))
  ([r txt]
   (test-for-n-occurences-of-regex-in-string (re-matcher r txt) 0 nil)))

(deftest test-for-n-occurences-of-regex-in-string-works
  (is (= 0 (test-for-n-occurences-of-regex-in-string #"\d" "aabbcc")))
  (is (= 1 (test-for-n-occurences-of-regex-in-string #"\d" "aaabb1cccdd")))
  (is (= 3 (test-for-n-occurences-of-regex-in-string #"(?m)^# line$"
                                                     "not this line\n# line\nnot this one either\n# line\n# line"))))

;; ----------------------------------------

(tct/defspec stringwriter-reducer-produces-all-the-lines-1
  1000
  (tcprop/for-all
   [noks tcgen/pos-int         ;; ok 1 okname\d+
    nnotoks tcgen/pos-int      ;; not ok 1 notokname\d+
    noks_skip tcgen/pos-int    ;; ok 1 skipname\d+ # SKIP skipname\d
    nnotoks_todo tcgen/pos-int ;; not ok 1 todoname\d+ # TODO todoname\d+
    ndiags tcgen/pos-int]      ;; # diag\d+
   true))
   
