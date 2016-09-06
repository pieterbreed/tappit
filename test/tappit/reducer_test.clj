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
;; testing the fns that create valid 'lines'

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
  (is (specced-fn-passes-check 'tappit.reducer/plan))
  (is (specced-fn-passes-check 'tappit.reducer/test-line)))

;; ----------------------------------------
;; testing the combinator reducer

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

(defn count-nr-of-occurences-of-regex-in-string
  "Tests whether txt contains exactly n repititions of the regex. 

  (You _probably_ want to use the 2-argument overload.)

  eg: (count-nr-of-occurences-of-regex-in-string
          #\"(?m)^# 1$\"
          \"nonsenseline\\n# 1\\n# 1\\nmore nonsense\")
     -> 2
  "
  ([matcher counter dontcallthisoverload]
   (if (re-find matcher) (recur matcher (inc counter) nil)
       counter))
  ([r txt]
   (count-nr-of-occurences-of-regex-in-string (re-matcher r txt) 0 nil)))

;; this is a test so that the utility testing fn works properly
(deftest count-nr-of-occurences-of-regex-in-string-works
  (is (= 0 (count-nr-of-occurences-of-regex-in-string
            #"\d" "aabbcc")))
  (is (= 1 (count-nr-of-occurences-of-regex-in-string
            #"\d" "aaabb1cccdd")))
  (is (= 3 (count-nr-of-occurences-of-regex-in-string
            #"(?m)^# line$"
            "not this line\n# line\nnot this one either\n# line\n# line"))))

;; ----------------------------------------
;; testing make-string-writer-reducer

(defn make-n-ok-lines
  [n-oks]
  (for [i (range 1 (inc n-oks))]
    (test-line :ok i (str "okname" i))))
;; (make-n-ok-lines 1)

(defn make-not-ok-lines
  [n-not-oks]
  (for [i (range 1 (inc n-not-oks))]
    (test-line :not-ok i (str "notokname" i))))
;; (make-not-ok-lines 2)

(defn make-ok-skip-lines
  [n-oks-skip]
  (for [i (range 1 (inc n-oks-skip))]
    (test-line :ok
               i (str "skipname" i)
               :skip (str "skipname" i))))
;; (make-ok-skip-lines 1)

(defn make-not-ok-todo-lines
  [n-not-oks-todo]
  (for [i (range 1 (inc n-not-oks-todo))]
    (test-line :not-ok
               i (str "todoname" i)
               :todo (str "todoname" i))))
;; (make-not-ok-todo-lines 3)

(defn make-diag-lines
  [n-diags]
  (for [i (range 1 (inc n-diags))]
    (diag (str "diag" i))))
;; (make-diag-lines 3)

(defn make-random-document
  [n-oks
   n-not-oks
   n-oks-skip
   n-not-oks-todo
   n-diags]
  (let [ok-lines           (make-n-ok-lines n-oks)
        not-ok-lines       (make-not-ok-lines n-not-oks)
        ok-skip-lines      (make-ok-skip-lines n-oks-skip)
        not-ok-todo-lines  (make-not-ok-todo-lines n-not-oks-todo)
        diag-lines         (make-diag-lines n-diags)

        scrambled-all-lines (shuffle 
                             (concat ok-lines
                                     not-ok-lines
                                     ok-skip-lines
                                     not-ok-todo-lines
                                     diag-lines))
        w (java.io.StringWriter.)
        string-tap-reducer (make-string-writer-reducer
                            w)]
    (do (reduce tap-reducer string-tap-reducer scrambled-all-lines)
        (.toString w))))
;; (make-random-document 1 1 1 1 1)

(tct/defspec stringwriter-reducer-produces-all-the-lines-1
  1000
  (tcprop/for-all
   [n-oks tcgen/pos-int          ;; ok \d+ okname\d+
    n-not-oks tcgen/pos-int      ;; not ok \d+ notokname\d+
    n-oks-skip tcgen/pos-int     ;; ok \d+ skipname\d+ # SKIP skipname\d
    n-not-oks-todo tcgen/pos-int ;; not ok \d+ todoname\d+ # TODO todoname\d+
    n-diags tcgen/pos-int]       ;; # diag\d+
   
   (let [random-document (make-random-document n-oks
                                               n-not-oks
                                               n-oks-skip
                                               n-not-oks-todo
                                               n-diags)]
     (and (= n-oks (count-nr-of-occurences-of-regex-in-string
                    #"(?m)ok \d+ okname\d+"
                    random-document))
          (= n-not-oks (count-nr-of-occurences-of-regex-in-string
                        #"(?m)not ok \d+ notokname\d+"
                        random-document))
          (= n-oks-skip (count-nr-of-occurences-of-regex-in-string
                         #"(?m)ok \d+ skipname\d+ # SKIP skipname\d+"
                         random-document))
          (= n-not-oks-todo (count-nr-of-occurences-of-regex-in-string
                             #"(?m)not ok \d+ todoname\d+ # TODO todoname\d+"
                             random-document))
          (= n-diags (count-nr-of-occurences-of-regex-in-string
                      #"(?m)# diag\d+"
                      random-document))))))
   
