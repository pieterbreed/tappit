(ns tappit.reducer
  (:require [clojure.spec :as s]))

;; ----------------------------------------

(s/def ::okness #{:ok :not-ok})
(s/def ::name string?)
(s/def ::test-nr pos-int?)
(s/def ::detail (s/keys :opt [::name ::test-nr]))
(s/def ::diagnostics-type #{:skip :todo :diag})
(s/def ::diagnostics-msg string?)
(s/def ::diagnostics (s/keys :req [::diagnostics-type]
                             :opt [::diagnostics-msg]))
(s/def ::plan-nr pos-int?)
(s/def ::bail-out string?)
(s/def ::core-line (s/keys :req [::okness]
                           :opt [::detail
                                 ::diagnostics]))
(s/def ::line (s/or
               :diag (s/keys :req [::diagnostics-msg])
               :bail (s/keys :req [::bail-out])
               :plan (s/keys :req [::plan-nr])
               :core ::core-line))

;; ----------------------------------------

(defmulti tap-reducer
  "Clever pun, eh? \"Reduces\" an ordered sequence of taps into some other useful thing. Maybe a string-stream (like to stdout) or maybe an aggregation, for like stats or something."
  (fn [current _] (:type current)))

;; ----------------------------------------

(defn make-tap-reducer-combinator
  "Creates a tap-reducer that combines other tap-reducers into one main reducer that does everything."
  [& reducers]
  (hash-map
   :type ::combinator
   :combinators reducers))

(defmethod tap-reducer ::combinator
  [current x]
  (update-in current
             [:combinators]
             (fn [reducers]
               (->> reducers
                    (map #(tap-reducer % x))
                    (apply vector)))))

;; ----------------------------------------

(defn make-string-writer-reducer
  "Creates a tap-reducer whose only job is to output to a writer (eg *out*). The only state it carries is if a bail-out has been found yet or not, after which nothing else will go out to that stream."
  [w] {:type ::->java.io.Writer
       :bailed false
       :writer w})

(defn -get-string-writer-core-part [deets]
  (let [status-part (condp = (::okness deets)
                      :ok "ok"
                      :not-ok "not ok")
        counter-part (if (nil? (-> deets ::test-nr))
                       ""
                       (str  " "
                             (-> deets ::test-nr)))
        name-part (if (empty? (::name deets))
                    ""
                    (str " "
                         (::name deets)))
        diag-part (if (nil? (::diagnostics deets))
                    ""
                    (str " #"
                         (condp = (-> deets
                                      ::diagnostics
                                      ::diagnostics-type)
                           :skip " SKIP"
                           :todo " TODO"
                           :diag "")
                         (let [msg (-> deets
                                       ::diagnostics
                                       ::diagnostics-msg)]
                           (if (empty? msg) ""
                               (str " " msg)))))]
    (str status-part
         counter-part
         name-part
         diag-part)))

(defmethod tap-reducer ::->java.io.Writer
  [current new]

  (if (or (nil? new)
          (:bailed current))
    
    ;; short-circuit on error conditions
    current

    ;; normal processing
    (let [line  (s/conform ::line new)
          op    (first line)
          deets (second line)
          w     (:writer current)]
      (condp = op
        :bail (do
                (.write w (str "Bail out! " (::bail-out deets)
                               \newline))
                (assoc current :bailed true))
        :diag (do
                (.write w (str "# " (::diagnostics-msg deets)
                               \newline))
                current)
        :plan (do
                (.write w (str "1.." (::plan-nr deets)
                               \newline))
                current)
        :core (do
                (.write w (str (-get-string-writer-core-part deets)
                               \newline))
                current)))))

;; ----------------------------------------

(defn make-stats-aggregating-reducer
  "Creates a tap-reducer that gathers statistics over a tap stream like the following:

  $ (reduce tap-reducer (make-aggregator) (sgen/sample (s/gen ::line)))
  => {:type :aggregator, :nr-diags 4, :nr-oks 1, :nr-notoks 1, :total 2, :bailed (\"\" \"2\"), :planned-for 3}"
  []
  
  {:type ::aggregator
   :nr-diags 0
   :nr-oks 0
   :nr-notoks 0
   :total 0})

(defmethod tap-reducer ::aggregator
  [current new]
  (if (nil? new) current
      (let [line (s/conform ::line new)
            op        (first line)
            deets (second line)]
        (condp = op
          :bail (update-in current [:bailed] conj (::bail-out deets))
          :diag (update-in current [:nr-diags] inc)
          :plan (assoc current :planned-for (::plan-nr deets))
          :core (let [core-line (s/conform ::core-line deets)
                      ok-ness (::okness core-line)]

                  (as-> current $
                    (update-in $ [(if (= ok-ness :ok)
                                    :nr-oks
                                    :nr-notoks)] inc)
                    (update-in $ [:total] inc)))
          current))))

;; ----------------------------------------


