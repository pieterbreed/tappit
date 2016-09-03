(ns tappit.producer
  (:require [clojure.core.async :as csp]))

(defonce ok :ok)
(defonce not-ok (not ok))

(defn ->ok!
  ([t thing] (->ok! t thing ""))
  ([t thing name & _]
   (swap! t (fn [current]

              (as-> current $
                
                ;; init the counter if this is the first test
                (if-not (:counter $)
                  (assoc current :counter 0)
                  current)

                ;; increment it for the next test result
                (update-in $ [:counter] inc)

                ;; print the status line
                (do 
                  (print (str (if thing "ok" "not ok")
                              " " (:counter $)
                              (if (< 0 (count name)) (str " - " name))))
                  ;; (and lastly return the current state value)
                  $))))
   ok))

(defn ->isa! [& rst])

(defn ->plan-for! [t n & _]
  (let [cleanup (atom nil)]
    (if (not= @t
              (swap! t (fn [current]
                         ;; non-silently ignore bad usage of the api by
                         ;; leaving a snarky diagnostics message
                         (if (:planned-for current)
                           (do (csp/go (->diag! t "You can only plan once! :@"))
                               current)
                           (assoc current
                                  :planned-for n
                                  :pnl t)))))
      (print (str "1.." n)))))

(defn ->diag! [t msg & _]
  (print (str "# " msg)))

(defn ->cleanup! [t])


(defmacro with-tap!
  [& body]
  `(let [tap# (atom {})
         ~'ok! (fn [& rst#] (apply ->ok! tap# rst#))
         ~'isa! (fn [& rst#] (apply ->isa! tap# rst#))
         ~'plan-for! (fn [& rst#] (apply ->plan-for! tap# rst#))
         ~'diag! (fn [& rst#] (apply ->diag! tap# rst#))
         result# (do ~@body)]
     (->cleanup! tap#)
     result#))

