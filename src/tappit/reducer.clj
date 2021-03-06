(ns tappit.reducer
  (:require [clojure.spec :as s]))

;; ----------------------------------------

(s/def ::ok-ness #{:ok :not-ok})
(s/def ::name string?)
(s/def ::test-nr pos-int?)
(s/def ::detail (s/keys :opt [::name ::test-nr]))

(s/def ::diagnostics-msg string?)
(s/def ::inline-diagnostics-type #{:skip :todo :diag})
(s/def ::inline-diagnostics (s/keys :req [::inline-diagnostics-type
                                          ::diagnostics-msg]))
(s/def ::diag-line (s/keys :req [::diagnostics-msg]))

(s/def ::bail-out string?)
(s/def ::bail-line (s/keys :req [::bail-out]))

(s/def ::plan-nr pos-int?)
(s/def ::plan-line (s/keys :req [::plan-nr]))

(s/def ::test-line (s/keys :req [::ok-ness]
                           :opt [::detail
                                 ::inline-diagnostics]))

(s/def ::line (s/or
               :diag ::diag-line
               :bail ::bail-line
               :plan ::plan-line
               :test ::test-line))

;; ----------------------------------------
;; utils to create these lines in the correct format

(s/fdef diag
  :args (s/cat :msg string?)
  :ret ::diag-line
  :fn #(= (-> % :ret ::diagnostics-msg)
          (-> % :args :msg)))
(defn diag
  "Make a diagnostics item. They produce output like this:

  # Diagnostics lines start with \\#"
  [msg]
  {::diagnostics-msg msg})

;; --------------------

(s/fdef bail
  :args (s/cat :msg string?)
  :ret ::bail-line
  :fn #(= (-> % :ret ::bail-out)
          (-> % :args :msg)))
(defn bail
  "Makes a bail-out item. They look like this in the output:

  Bail out! No more Dylithium crystals

  After the engine accepts this, no more output will be produced."
  [msg]
  {::bail-out msg})

;; --------------------

(s/fdef plan
  :args (s/cat :n pos-int?)
  :ret ::plan-line
  :fn #(= (-> % :ret ::plan-nr)
          (-> % :args :n)))
(defn plan
  "Produces a plan line in the output.
   1..19

  You may _optionally_ call this before you run any tests. If you don't know how many tests you are going to run, rather don't call this at all."
  [n]
  {::plan-nr n})

;; --------------------

(s/def ::test-line-arguments-spec
  (s/cat :ok-ness ::ok-ness
         :detail (s/? (s/cat :test-nr ::test-nr
                             :name ::name))
         :inline-diagnostics (s/?
                              (s/cat :diag-type ::inline-diagnostics-type
                                     :diag-msg ::diagnostics-msg))))
(s/fdef test-line
  :args ::test-line-arguments-spec
  :ret ::test-line
  :fn #(and (= (-> % :ret ::ok-ness)
               (-> % :args :ok-ness))
            (= (-> % :ret ::detail ::name)
               (-> % :args :detail :name))
            (= (-> % :ret ::detail ::test-nr)
               (-> % :args :detail :test-nr))
            (= (-> % :ret ::inline-diagnostics ::inline-diagnostics-type)
               (-> % :args :inline-diagnostics :diag-type))
            (= (-> % :ret ::inline-diagnostics ::diagnostics-msg)
               (-> % :args :inline-diagnostics :diag-msg))))

(defn test-line
  "Makes a test data item.

  eg: (test-line :ok) ;; bad style, but possible
      (test-line :not-ok) ;; so is this...
      (test-line :ok :diag \"diagnostics example message\") ;; same thing

      ;; the problem with the previous examples is that they don't contai
      ;; test numbers or test names. this is better:
      (test-line :not-ok 2 \"test name\")

      ;; a test-line may also have one of :skip :diag or :todo
      (test-line :ok 3 \"test name\" :skip \"diagnostics message that starts with SKIP\") 
      

  in general: (test-line (one-of #{:ok :not-ok})
                         (? test-nr-int 
                            test-name-string)
                         (? (one-of #{:diag :skip :todo})
                            diagnostics-msg-string))
  "
  [& rst]
  (let [args (s/conform ::test-line-arguments-spec rst)
        deets (:detail args)
        diag (:inline-diagnostics args)]

    (assert (not (nil? args)))

    ;; ok-ness is required, so start with that
    (as-> {::ok-ness (:ok-ness args)} $

      ;; add details, if given any
      (if (nil? deets) $
          (assoc $ ::detail {::name (:name deets)
                             ::test-nr (:test-nr deets)}))

      ;; add diagnostics, if given any
      (if (nil? diag) $
          (assoc $
                 ::inline-diagnostics
                 {::inline-diagnostics-type (:diag-type diag)
                  ::diagnostics-msg (:diag-msg diag)})))))

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

(defmulti bailed?
  "Have we bailed yet?"
  -type-of-first)

(defmethod bailed? :default [current] (:bailed current))

;; ----------------------------------------

(defn make-tap-reducer-combinator
  "Creates a tap-reducer that combines other tap-reducers into one main reducer that does everything."
  [& reducers]
  (hash-map
   :type ::combinator
   :reducers reducers))

(defmethod tap-reducer ::combinator
  [current x]
  (if (or (nil? x)
          (:bailed current))
    current
    (update-in current
               [:reducers]
               (fn [reducers]
                 (->> reducers
                      (map #(tap-reducer % x))
                      (apply vector))))))

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
       :tests 0
       :writer w})

(defn -make-test-line
  [line]
  
  (let [deets (::detail line)
        status-part (condp = (::ok-ness line)
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
        diag-part (if (nil? (::inline-diagnostics line))
                    ""
                    (str " #"
                         (condp = (-> line
                                      ::inline-diagnostics
                                      ::inline-diagnostics-type)
                           :skip " SKIP"
                           :todo " TODO"
                           :diag "")
                         (let [msg (-> line
                                       ::inline-diagnostics
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
                (assoc current :planned true))
        :test (do
                (.write w (str (-make-test-line deets)
                               \newline))
                (update-in current [:tests] inc))))))

(defmethod tap-reducer-cleanup ::->java.io.Writer
  [current]
  (if (not (or (:planned current)
               (:bailed current)))
    (do (.write (:writer current)
                (str 
                 "1.."
                 (:tests current)
                 \newline)))))

;; ----------------------------------------

(defn make-stats-aggregating-reducer
  "Creates a tap-reducer that gathers statistics over a tap stream like the following:

  $ (reduce tap-reducer (make-aggregator) (sgen/sample (s/gen ::line)))
  => {:type :aggregator, :nr-diags 4, :nr-oks 1, :nr-notoks 1, :total 2, :bailed (\"\" \"2\"), :planned-for 3}"
  []
  
  {:type ::aggregator
   :bailed false
   :nr-diags 0
   :nr-oks 0
   :nr-notoks 0
   :total 0})

(defmethod tap-reducer ::aggregator
  [current new]
  (if (or (nil? new)
          (:bailed current))
    current
    (let [line (s/conform ::line new)
          op        (first line)
          deets (second line)]
      (condp = op
        :bail (assoc current :bailed (::bail-out deets))
        :diag (update-in current [:nr-diags] inc)
        :plan (assoc current :planned-for (::plan-nr deets))
        :test (let [test-line (s/conform ::test-line deets)
                    ok-ness (::ok-ness test-line)]

                (as-> current $
                  (update-in $ [(if (= ok-ness :ok)
                                  :nr-oks
                                  :nr-notoks)] inc)
                  (update-in $ [:total] inc)))))))

;; ----------------------------------------

(defn make-commenting-reducer
  "Creates a tap-reducer that embeds both a string-writer-reducer and a stat-aggregating reducer. It uses the stats to provide extra comments to humans after the string-writer is done. eg \"# All good!\" at the end of the stream."
  [w]
  (hash-map :type ::commenting-reducer
            :string-writer (make-string-writer-reducer w)
            :stats (make-stats-aggregating-reducer)))

(defmethod tap-reducer ::commenting-reducer
  [current new]
  (if (or (nil? new)
          (:bailed current))
    current
    (assoc current
           :string-writer (tap-reducer (:string-writer current)
                                       new)
           :stats         (tap-reducer (:stats current)
                                       new))))

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


