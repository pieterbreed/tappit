(ns tappit.producer
  (:require [clojure.core.async :as csp]))

(defonce ok :ok)
(defonce not-ok (not ok))

(defn ->ok!
  ([t thing] (->ok! t thing ""))
  ([t thing name & _]
   (swap! t (fn
              [{last-action :last-action
                :as current}]
              (let [new (assoc current
                               :oks (let [oks (get current :oks 0)]
                                      (if thing (inc oks) oks))
                               :counter (inc (get current :counter 0))
                               :last-action :->ok)]
                (print (str (if (= last-action :->ok)
                              "\n"
                              "")
                            (if thing
                              "ok"
                              "not ok")
                            " " (:counter new)
                            (if (< 0 (count name)) (str " - " name))))
                new)))
   ok))

(defn ->isa! [& rst]
  ok)

(defn ->plan-for
  [{:as current
    last-action :last-action}
   n]
  ;; non-silently ignore bad usage of the api by
  ;; leaving a snarky diagnostics message
  (if (= last-action
         :->ok)
    (println))
  (println (str "1.." n))
  (assoc current
         :planned-for n
         :last-action :->plan-for))

(defn ->plan-for!
  [t n & _]
  (swap! t ->plan-for n)
  ok)

(defn ->diag [{last-action :last-action
               diags :diags
               :as current}
              msg]

  (println (str (if (= last-action
                       :->ok)
                  " "
                  "")
                (str "# " msg)))
  
  (assoc current
         :last-action :->diag
         :diags (if diags (inc diags)
                    1)))

(defn ->diag!
  [t msg]
  (swap! t ->diag msg)
  ok)

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
         result# (do ~@body)]
     (->cleanup! tap#)
     (deref tap#)))

