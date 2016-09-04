#!/usr/bin/env boot

(set-env! :dependencies '[[pieterbreed/tappit "0.9.5"]])
(require '[tappit.producer :refer [with-tap! ok]])

;; ----------------------------------------
;; OK - Let's test some stuff!

(with-tap!

  ;; plan 15 tests, only make 13
  (plan-for! 15)

  (ok! 1)
  (ok! 1 "everything is ok")
  (ok! 0 "never fails (in clojure)")

  (ok! (= 10 10) "is ten ten?")
  (ok! ok "even ok is ok!")
  (ok! (type ok) "ok is not nil. ok is not nothing.")
  (ok! true "the truth will set you ok.")
  (ok! (not false) "and nothing but the truth.")
  (ok! false "and we'll know if you lie to us")

  (ok! (integer? 10) "10 is an integer")
  (ok! (string? "10") "\"10\" is a string")

  (ok! 0 "zero is true" :todo "bonus - quite a lot like ruby")
  (ok! nil "nil is true" :skip "not possible in this universe"))
