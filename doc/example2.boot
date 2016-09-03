#!/usr/bin/env boot

(set-env! :dependencies '[[pieterbreed/tappit "0.9.0-SNAPSHOT"]])
(require '[tappit.producer :only [ok! isa! eq! plan-for!]])

;; ----------------------------------------
;; OK - Let's test some stuff!

(plan-for! 15)

(ok! 1)
(ok! 1 "everything is ok")
(ok! 0 "always fails")

(ok! (= 10 10) "is ten ten?")
(ok! ok "even ok is ok!")
(ok! (type ok) "ok is not nil. ok is not nothing.")
(ok! true "the truth will set you ok.")
(ok! (not false) "and nothing but the truth.")
(ok! false "and we'll know if you lie to us")

(isa! 10 int? "10")
(isa! "10" string? "takes any predicate")

(ok! 0 "zero is true" :todo "be more like ruby")
(ok! nil "nil is true" :skip "not possible in this universe")

(eq! "not" "equal" "two different strings are not equal")

