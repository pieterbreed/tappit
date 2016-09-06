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
(defonce not-ok (not ok))

(def ->diag)
(def -bailed?)

(defn ->ok
  [{last-action :last-action
    oks :oks
    counter :counter
    :as current
    :or {oks 0
         counter 0}}
   thing
   & {name :name
      todo :todo
      diag :diag
      skip :skip
      :or {name ""
           todo nil
           skip nil
           diag nil}}]
  (if (-bailed? current)
    current
    (let [new (assoc current
                     :oks (if thing (inc oks) oks)
                     :counter (inc counter)
                     :last-action :->ok)]
      (print (str (if (= last-action :->ok)
                    "\n"
                    "")
                  (if thing
                    "ok"
                    "not ok")
                  " " (:counter new)
                  (if (< 0 (count name)) (str " - " name))))

      (cond
        (not (nil? skip))
        (->diag new (str "SKIP " skip))

        (not (nil? todo))
        (->diag new (str "TODO "
                         todo))

        (not (nil? diag))
        (->diag new (str diag))

        true new))))

(defn ->plan-for
  [{:as current
    last-action :last-action}
   n]
  (if (-bailed? current)
    current
    (do 
      (if (= last-action
             :->ok)
        (println))
      (println (str "1.." n))
      (assoc current
             :planned-for n
             :last-action :->plan-for))))

(defn ->diag [{last-action :last-action
               diags :diags
               :as current}
              msg]

  (if (-bailed? current)
    current
    (do 
      (println (str (if (= last-action
                           :->ok)
                      " "
                      "")
                    (str "# " msg)))

      (assoc current
             :last-action :->diag
             :diags (if diags (inc diags)
                        1)))))


(defn ->bail-out
  [{:as current
    last-action :last-action}
   msg]
  (if (-bailed? current)
    current
    (do 
      (if (= last-action :->ok) (println))
      (println (str "Bail out! " msg))
      (assoc current
             :bailed true
             :last-action :->bail-out))))

(defn -bailed?
  [current]
  (:bailed current))

(defn ->bailed?
  [t]
  (-bailed? @t))

(defn ->not-bailed?
  [t]
  (not (->bailed? t)))

(defn ->cleanup
  [{:keys [last-action
           planned-for
           counter
           oks]
    :as current}]

  (as-> current $
    (->diag $ "")
    (->diag $ "----------------------------------------")
    (if (nil? planned-for)
      (do 
        (->plan-for $ counter)
        (if (= counter oks)
          (->diag $ "All good")
          (->diag $ (str "You did "
                         counter
                         " and had "
                         oks
                         " oks."))
          ))
      (do 
        (if (= planned-for counter oks)
          (->diag $ "All good")
          (->diag $ (str "You planned "
                         planned-for
                         ", did "
                         counter
                         " and had "
                         oks
                         " oks.")))))))


;; ----------------------------------------

(defn ->ok!
  [t thing & rst]

  (if (string? (first rst))
    (apply ->ok! t thing :name (first rst) (rest rst))
    (apply swap! t ->ok thing rst))
  
  ok)

(defn ->=!
  [t thing1 thing2 & rst]
  (apply ->ok! t (= thing1 thing2) rst))

(defn ->isa!
  [t thing pred & rst]
  (apply ->ok! t (pred thing) rst))

(defn ->plan-for!
  [t n & _]
  (swap! t ->plan-for n)
  ok)

(defn ->diag!
  [t msg]
  (swap! t ->diag msg)
  ok)

(defn ->bail-out!
  [t msg]
  (swap! t ->bail-out msg)
  ok)

(defn ->cleanup!
  [t]
  (swap! t ->cleanup)
  ok)

;; ----------------------------------------

(defn create-atom-tap-producer
  []
  (let [a (atom {})]
    (reify Producer
      (plan-for! [_ n] (->plan-for! a n))
      (diag! [_ msg] (->diag! a msg))
      (bail-out! [_ msg] (->bail-out! a msg))
      (done! [_] (->cleanup! a))

      (bailed? [_] (->bailed? a))
      (not-bailed? [_] (->not-bailed? a))

      (ok! [_] (->ok! a true))
      (ok! [_ thing] (->ok! a thing))
      (ok! [_ thing name] (->ok! a thing name))
      (ok! [_ thing flag value] (->ok! a thing flag value))
      (ok! [_ thing name flag value] (->ok! a thing name flag value))

      (isa! [_ thing pred] (->isa! a thing pred))
      (isa! [_ thing pred name] (->isa! a thing pred name))
      (isa! [_ thing pred flag value] (->isa! a thing pred flag value))
      (isa! [_ thing pred name flag value] (->isa! a thing pred name flag value))

      (=! [_ thing1 thing2] (->=! a thing1 thing2))
      (=! [_ thing1 thing2 name] (->=! a thing1 thing2 name))
      (=! [_ thing1 thing2 flag value] (->=! a thing1 thing2 flag value))
      (=! [_ thing1 thing2 name flag value] (->=! a thing1 thing2 name flag value)))))

;; ----------------------------------------

;; (defn create-atom-reducer-tap-producer
;;   "Creates a tap-producing API, but using the tappit.reducer api"
;;   []
;;   (let [aa (atom (tr/make-commenting-reducer *out*))
;;         ! (fn [item] (swap! a tr/tap-reducer item))]
;;     (reify Producer
;;       (plan-for! [_ n]   (! (tr/plan n)))
;;       (diag!     [_ msg] (! (tr/diag msg)))
;;       (bail-out! [_ msg] (! (tr/bail msg)))
;;       (done!     [_]     (swap! aa tap-reducer-cleanup))

;;       (bailed? [_] (->bailed? a))
;;       (not-bailed? [_] (->not-bailed? a))

;;       (ok! [_] (->ok! a true))
;;       (ok! [_ thing] (->ok! a thing))
;;       (ok! [_ thing name] (->ok! a thing name))
;;       (ok! [_ thing flag value] (->ok! a thing flag value))
;;       (ok! [_ thing name flag value] (->ok! a thing name flag value))

;;       (isa! [_ thing pred] (->isa! a thing pred))
;;       (isa! [_ thing pred name] (->isa! a thing pred name))
;;       (isa! [_ thing pred flag value] (->isa! a thing pred flag value))
;;       (isa! [_ thing pred name flag value] (->isa! a thing pred name flag value))

;;       (=! [_ thing1 thing2] (->=! a thing1 thing2))
;;       (=! [_ thing1 thing2 name] (->=! a thing1 thing2 name))
;;       (=! [_ thing1 thing2 flag value] (->=! a thing1 thing2 flag value))
;;       (=! [_ thing1 thing2 name flag value] (->=! a thing1 thing2 name flag value)))))

;; ----------------------------------------


(defmacro with-tap!
  [& body]
  `(let [tap# (create-atom-tap-producer)
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

