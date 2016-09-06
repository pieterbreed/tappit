(ns tappit.reducer-test
  (:require [tappit.reducer :refer :all]
            [clojure.test :refer :all]
            [clojure.string :as str]
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

(tct/defspec combinator-reducer-call-everything-it-should
  1000
  (tcprop/for-all
   [vint (tcgen/vector tcgen/int)]
   (let [n (count vint)

         ;; send n fake messages to n fake reducers and do a cleanup
         r (as-> (create-test-tap-reducer n) $
             (reduce tap-reducer $ vint)
             (tap-reducer-cleanup $))]

     ;; tests the n messages were passed through and that cleanup was called once
     (letfn [(failure-counter
               [current-failed next]
               (if (and (= n (:events next))
                        (= 1 (:cleaned-up next)))
                 current-failed
                 (inc current-failed)))]
       (= 0 (reduce failure-counter 0 (:reducers r)))))))


