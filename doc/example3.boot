#!/usr/bin/env boot

(set-env! :dependencies '[[pieterbreed/tappit "0.9.0-SNAPSHOT"]])
(require '[tappit.producer :only [ok! isa! plan-for! diag!]])

;; ----------------------------------------
;; OK - Let's test some stuff!

(plan-for! 3)

(defn between? [bottom val top msg]
  (let [result (<= bottom val top)]
    (ok! result msg)
    (if-not result (diag! (str val " is not between " bottom " and " top)))
    result))

(between? 3 5 10 "5 is ok" )
(between? 5 5.5 6 "5[2] is ok")

;; This will fail

(between? 5 1 10 "1 is in range")





