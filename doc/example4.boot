#!/usr/bin/env boot

(set-env! :dependencies '[[pieterbreed/tappit "0.9.5"]])
(require '[tappit.producer :refer [with-tap! ok]])

;; ----------------------------------------
;; OK - Let's test some stuff!

(with-tap! 
  (plan-for! 10000)

  (ok! 1 "Once to do it properly.")
  (ok! 1 "Second, to make sure.")
  (ok! 1 "Third, like Irish whiskey, to be sure that you're sure.")

  (bail-out! "This is criminally boring!")

  (if (not-bailed?)
    (println "yeehaa! - this line doesn't go to output")
    (dorun
     (for [x (range 4 (inc 10000))]
       (ok! x :skip "In fact, this should not be in the output either!")))))
