# tappit

A Clojure library for producing [TAP](http://testanything.org/) output.

[![Build Status](https://travis-ci.org/pieterbreed/tappit.svg?branch=master)](https://travis-ci.org/pieterbreed/tappit)

This work is inspired by [this python TAP library](https://github.com/rjbs/tapsimple).

## Usage

### Example1

Based on [this Python `tapsimple` module example](https://github.com/rjbs/tapsimple/blob/master/examples/ok.t).


```
$ cat doc/example1.boot
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

  (ok! 0 "zero is true" :todo "be more like python")
  (ok! nil "nil is true" :skip "not possible in this universe"))
$ doc/example1.boot 
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
ok 11 - "10" is a string
ok 12 - zero is true # TODO be more like python
not ok 13 - nil is true # SKIP not possible in this universe
# 
# ----------------------------------------
# You planned 15, did 13 and had 11 oks.
```

There are more examples in the `doc` folder.

## License

Copyright © 2016 Pieter Breed

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
