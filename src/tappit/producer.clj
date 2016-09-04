(ns tappit.producer
  (:require [clojure.core.async :as csp]))

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
      skip :skip
      :or {name ""
           todo nil
           skip nil}}]
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


(defmacro with-tap!
  [& body]
  `(let [tap# (atom {})
         ~'ok! (fn [& rst#] (apply ->ok! tap# rst#))
         ~'isa! (fn [& rst#] (apply ->isa! tap# rst#))
         ~'plan-for! (fn [& rst#] (apply ->plan-for! tap# rst#))
         ~'diag! (fn [& rst#] (apply ->diag! tap# rst#))
         ~'=! (fn [& rst#] (apply ->=! tap# rst#))
         ~'bail-out! (fn [& rst#] (apply ->bail-out! tap# rst#))
         ~'bailed? (fn [& rst#] (apply ->bailed? tap# rst#))
         ~'not-bailed? (fn [& rst#] (apply ->not-bailed? tap# rst#))
         result# (do ~@body)]
     (->cleanup! tap#)
     (deref tap#)))

