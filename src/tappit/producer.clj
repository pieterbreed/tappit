(ns tappit.producer
  "Import this namespace if you want to produce TAP-based output, for eg:

  (require '[tappit.producer :refer [with-tap! ok]])

  (with-tap!

  ;; plan 15 tests, only make 13
  (plan-for! 15)

  (ok! 1)
  (ok! 1 \"everything is ok\")
  (ok! 0 \"never fails (in clojure)\")

  (ok! (= 10 10) \"is ten ten?\")
  (ok! ok \"even ok is ok!\")
  (ok! (type ok) \"ok is not nil. ok is not nothing.\")
  (ok! true \"the truth will set you ok.\")
  (ok! (not false) \"and nothing but the truth.\")
  (ok! false \"and we'll know if you lie to us\")

  (ok! (integer? 10) \"10 is an integer\")
  (ok! (string? \"10\") \\\"10\\\" is a string\")

  (ok! 0 \"zero is true\" :todo \"be more like python\")
  (ok! nil \"nil is true\" :skip \"not possible in this universe\"))

  to produce this output:

  1..15
  ok 1
  ok 2 - everything is ok
  ok 3 - never fails (in clojure)
  ok 4 - is ten ten?
  ok 5 - even ok is ok!
  ok 6 - ok is not nil. ok is not nothing.
  ok 7 - the truth will set you ok.
  ok 8 - and nothing but the truth.
  not ok 9 - and we'll know if you lie to us
  ok 10 - 10 is an integer
  ok 11 - \"10\" is a string
  ok 12 - zero is true # TODO be more like python
  not ok 13 - nil is true # SKIP not possible in this universe
  # 
  # ----------------------------------------
  # You planned 15, did 13 and had 11 oks.

  "
  (:require [tappit.reducer :as tr])) ;; tr->tap-reduce

;; protocols are low-level and variadic arguments don't
;; work well with them.
;; each ok!-based operation has 4 overloads.
;; eg (ok! thing "this is the name")
;;    (ok! thing :name "this is the name")
;;    (ok! thing "this is the name" :diag "diagnostics message)
;;    (ok! thing "this is the name" :skip "skip message")
(defprotocol Producer
  "Test Anything Protocol _Producer_"
  (plan-for!   [this n] "Creates the 1..N line")
  (diag!       [this msg] "Creates a diagnostics line (ie it starts with '#')")
  (bail-out!   [this msg] "Bail out / stop early")
  (done!       [this] "Wrap up the protocol. Usually with-style api's should call this for you. This can only be called once, when you're done.")

  (bailed?     [this] "Did we bail already?")
  (not-bailed? [this] "Are we still good to go?")
  
  (ok!
    [this]
    [this thing]
    [this thing name]
    [this thing flag value]
    [this thing name flag value]
    "Base TAP operation. Assert that something is OK. Produces 'ok' or 'not ok'.")
  
  (isa!
    [this thing pred]
    [this thing pred name]
    [this thing pred flag value]
    [this thing pred name flag value]
    "Predicate-based assertions.")
  
  (=!
    [this thing1 thing2]
    [this thing1 thing2 name]
    [this thing1 thing2 flag value]
    [this thing1 thing2 name flag value]
    "Asserting that two things have the same value."))

;; ----------------------------------------

(defonce ok :ok)

;; ----------------------------------------

(defn -OK!
  [t]
  (if (boolean t) :ok :not-ok))

(defn create-atom-reducer-tap-producer
  "Creates a tap-producing API, but using the tappit.reducer api"
  []
  (let [aa (atom (tr/make-commenting-reducer *out*))
        ! (fn [item] (swap! aa tr/tap-reducer item))
        get-test-nr (let [counter (atom 0)]
                      (fn [] (swap! counter inc)))]
    (reify Producer
      (plan-for! [_ n]   (! (tr/plan n)))
      (diag!     [_ msg] (! (tr/diag msg)))
      (bail-out! [_ msg] (! (tr/bail msg)))
      (done!     [_]     (swap! aa tr/tap-reducer-cleanup))

      (bailed? [_] (tr/bailed? @aa))
      (not-bailed? [_] (not (tr/bailed? @aa)))

      ;; --------------------
      ;; ok!
      
      (ok! [_]
        (! (tr/test-line :ok
                         (get-test-nr))))
      (ok! [_ thing]
        (! (tr/test-line (-OK! thing)
                         (get-test-nr)
                         "")))
      (ok! [_ thing name]
        (! (tr/test-line (-OK! thing)
                         (get-test-nr)
                         name)))
      (ok! [_ thing flag value]
        (! (tr/test-line (-OK! thing)
                         (get-test-nr)
                         ""
                         flag
                         value)))
      (ok! [_ thing name flag value]
        (! (tr/test-line (-OK! thing)
                         (get-test-nr)
                         name
                         flag
                         value)))

      ;; --------------------
      ;; isa!
      
      (isa! [_ thing pred]
        (! (tr/test-line (-OK! (pred thing))
                         (get-test-nr))))
      (isa! [_ thing pred name]
        (! (tr/test-line (-OK! (pred thing))
                         (get-test-nr)
                         name)))
      (isa! [_ thing pred flag value]
        (! (tr/test-line (-OK! (pred thing))
                         (get-test-nr)
                         ""
                         flag
                         value)))
      (isa! [_ thing pred name flag value]
        (! (tr/test-line (-OK! (pred thing))
                         (get-test-nr)
                         name
                         flag
                         value)))

      ;; --------------------
      ;; =!
      (=! [_ thing1 thing2]
        (! (tr/test-line (-OK! (= thing1 thing2))
                         (get-test-nr))))
      (=! [_ thing1 thing2 name]
        (! (tr/test-line (-OK! (= thing1 thing2)
                               (get-test-nr)
                               name))))
      (=! [_ thing1 thing2 flag value]
        (! (tr/test-line (-OK! (= thing1 thing2)
                               (get-test-nr)
                               ""
                               flag
                               value))))
      (=! [_ thing1 thing2 name flag value]
        (! (tr/test-line (-OK! (= thing1 thing2)
                               (get-test-nr)
                               name
                               flag
                               value)))))))

;; ----------------------------------------


(defmacro with-tap!
  [& body]
  `(let [tap# (create-atom-reducer-tap-producer)
         ~'ok! (fn [& rst#] (apply ok! tap# rst#))
         ~'isa! (fn [& rst#] (apply isa! tap# rst#))
         ~'plan-for! (fn [& rst#] (apply plan-for! tap# rst#))
         ~'diag! (fn [& rst#] (apply diag! tap# rst#))
         ~'=! (fn [& rst#] (apply =! tap# rst#))
         ~'bail-out! (fn [& rst#] (apply bail-out! tap# rst#))
         ~'bailed? (fn [& rst#] (apply bailed? tap# rst#))
         ~'not-bailed? (fn [& rst#] (apply not-bailed? tap# rst#))
         result# (do ~@body)]
     (done! tap#)))

