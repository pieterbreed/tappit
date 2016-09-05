(ns tappit.producer)

;; protocols are low-level and variadic arguments
;; don't work well with them.
;; each ok!-based operation has 4 overloads.
;; eg (ok! thing "this is the name")
;;    (ok! thing :name "this is the name")
;;    (ok! thing "this is the name" :diag "diagnostics message)
;;    (ok! thing "this is the name" :skip "skip message")
(defprotocol Producer
  "Test Anything Protocol _Producer_"
  (plan-for!   [this n] "Creates the 1..N line")
  (diag!       [this msg] "Creates a diagnostics line")
  (bail-out!   [this msg] "Bail out / stop testing early")
  (done!       [this] "Wrap up the protocol")

  (bailed?     [this] "Did we bail already?")
  (not-bailed? [this] "Are we still good to go?")

  (ok!
    [this]
    [this thing]
    [this thing msg]
    [this thing flag value]
    [this thing msg flag value]
    "Base TAP operation. Assert that something is OK. Produces 'ok' or 'not ok'.")
  
  (isa!
    [this thing pred]
    [this thing pred msg]
    [this thing pred flag value]
    [this thing pred msg flag value]
    "Predicate-based assertions.")
  
  (=!
    [this thing1 thing2]
    [this thing1 thing2 msg]
    [this thing1 thing2 flag value]
    [this thing1 thing2 msg flag value]
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

(defn ->plan-for!
  [t n & _]
  (swap! t ->plan-for n)
  ok)

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

(defn ->diag!
  [t msg]
  (swap! t ->diag msg)
  ok)

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

(defn ->bail-out!
  [t msg]
  (swap! t ->bail-out msg)
  ok)

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
      (ok! [_ thing msg] (->ok! a thing msg))
      (ok! [_ thing flag value] (->ok! a thing flag value))
      (ok! [_ thing msg flag value] (->ok! a thing msg flag value))

      (isa! [_ thing pred] (->isa! a thing pred))
      (isa! [_ thing pred msg] (->isa! a thing pred msg))
      (isa! [_ thing pred flag value] (->isa! a thing pred flag value))
      (isa! [_ thing pred msg flag value] (->isa! a thing pred msg flag value))

      (=! [_ thing1 thing2] (->=! a thing1 thing2))
      (=! [_ thing1 thing2 msg] (->=! a thing1 thing2 msg))
      (=! [_ thing1 thing2 flag value] (->=! a thing1 thing2 flag value))
      (=! [_ thing1 thing2 msg flag value] (->=! a thing1 thing2 msg flag value)))))

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

