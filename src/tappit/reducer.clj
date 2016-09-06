(ns tappit.reducer
  (:require [clojure.spec :as s]))

;; ----------------------------------------

(s/def ::okness #{:ok :not-ok})
(s/def ::name string?)
(s/def ::test-nr pos-int?)
(s/def ::detail (s/keys :opt [::name ::test-nr]))

(s/def ::diagnostics-msg string?)
(s/def ::inline-diagnostics-type #{:skip :todo :diag})
(s/def ::inline-diagnostics (s/keys :req [::inline-diagnostics-type]
                                    :opt [::diagnostics-msg]))
(s/def ::diag-line (s/keys :req [::diagnostics-msg]))

(s/def ::bail-out string?)
(s/def ::bail-line (s/keys :req [::bail-out]))

(s/def ::plan-nr pos-int?)
(s/def ::plan-line (s/keys :req [::plan-nr]))

(s/def ::core-line (s/keys :req [::okness]
                           :opt [::detail
                                 ::inline-diagnostics]))

(s/def ::line (s/or
               :diag ::diag-line
               :bail ::bail-line
               :plan ::plan-line
               :core ::core-line))

;; ----------------------------------------
;; utils to create these lines in the correct format

(s/fdef diag
  :args (s/cat :msg string?)
  :ret ::diag-line
  :fn #(= (-> % :ret ::diagnostics-msg)
          (-> % :args :msg)))
(defn diag
  "Make a diagnostics line item"
  [msg]
  {::diagnostics-msg msg})

;; --------------------

(s/fdef bail
  :args (s/cat :msg string?)
  :ret ::bail-line
  :fn #(= (-> % :ret ::bail-out)
          (-> % :args :msg)))
(defn bail
  "Makes a bail-out line"
  [msg]
  {::bail-out msg})

;; --------------------

(s/fdef plan
  :args (s/cat :n pos-int?)
  :ret ::plan-line
  :fn #(= (-> % :ret ::plan-nr)
          (-> % :args :n)))
(defn plan
  "Makes a bail-out line"
  [n]
  {::plan-nr n})

;; --------------------

(s/fdef plan
  :args (s/cat :n pos-int?)
  :ret ::plan-line
  :fn #(= (-> % :ret ::plan-nr)
          (-> % :args :n)))
(defn plan
  "Makes a bail-out line"
  [n]
  {::plan-nr n})

;; ----------------------------------------

(defn -type-of-first [current & _] (:type current))

(defmulti tap-reducer
  "Clever pun, eh? \"Reduces\" an ordered sequence of taps into some other useful thing. Maybe a string-stream (like to stdout) or maybe an aggregation, for like stats or something."
  -type-of-first)

(defmulti tap-reducer-cleanup
  "A cleanup step optionally has to run once the stream has been exhausted."
  -type-of-first)

;; default cleanup does nothing (for now)
(defmethod tap-reducer-cleanup :default [current] current)

;; ----------------------------------------

(defn make-tap-reducer-combinator
  "Creates a tap-reducer that combines other tap-reducers into one main reducer that does everything."
  [& reducers]
  (hash-map
   :type ::combinator
   :reducers reducers))

(defmethod tap-reducer ::combinator
  [current x]
  (update-in current
             [:reducers]
             (fn [reducers]
               (->> reducers
                    (map #(tap-reducer % x))
                    (apply vector)))))

(defmethod tap-reducer-cleanup ::combinator
  [current]

  ;; run cleanup on the embedded reducers
  (->> (:reducers current)
       (map tap-reducer-cleanup)
       vec
       (assoc current :reducers)))

;; ----------------------------------------

(defn make-string-writer-reducer
  "Creates a tap-reducer whose only job is to output to a writer (eg *out*). The only state it carries is if a bail-out has been found yet or not, after which nothing else will go out to that stream."
  [w] {:type ::->java.io.Writer
       :bailed false
       :cores 0
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
                (assoc current
                       :planned true))
        :core (do
                (.write w (str (-get-string-writer-core-part deets)
                               \newline))
                (update-in current [:cores] inc))))))

(defmethod tap-reducer-cleanup ::->java.io.Writer
  [current]
  (if (:planned current) current
      (do 
        (.write "1.." (:cores current)
                \newline)
        current)))

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

;; ----------------------------------------

(defn make-commenting-reducer
  "Creates a tap-reducer that embeds both a string-writer-reducer and a stat-aggregating reducer. It uses the stats to provide extra comments to humans after the string-writer is done. eg \"# All good!\" at the end of the stream."
  [w]
  (hash-map :type ::commenting-reducer
            :string-writer (make-string-writer-reducer)
            :stats (make-stats-aggregating-reducer)))

(defmethod tap-reducer ::commenting-reducer
  [current new]
  (assoc current
         :string-writer (tap-reducer (:string-writer current)
                                     new)
         :stats         (tap-reducer (:stats current)
                                     new)))

(defmethod tap-reducer-cleanup ::commenting-reducer
  [current]
  (let [all-good (= (-> current :stats :nr-oks)
                    (-> current :stats :total))

        ;; we are going to produce one extra diagnostic message
        ;; based on what the accumulated stats are telling us
        human-status-msg (if all-good
                           {::diagnostics-msg "All good!"}
                           (let [stats (:stats current)
                                 oks (:nr-oks stats)
                                 total (:total stats)]
                             {::diagnostics-msg (str "SCORE: (" oks
                                                     "/" total
                                                     ") "
                                                     (double (-> (/ oks total)
                                                                 (* 100)))
                                                     "% OK")}))

        ;; send the last message before cleanup
        new-current (tap-reducer current human-status-msg)]

    ;; call cleanup on the embeds
    (assoc new-current
           :stats (tap-reducer-cleanup (:stats new-current))
           :string-writer (tap-reducer-cleanup (:string-writer new-current)))))


