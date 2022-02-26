(ns notamaton.core)


(defn arity
  ;; taken from https://stackoverflow.com/a/47861069/17938983
  "Returns the maximum arity of:
    - anonymous functions like `#()` and `(fn [])`.
    - defined functions like `map` or `+`.
    - macros, by passing a var like `#'->`.

  Returns `:variadic` if the function/macro is variadic."
  [f]
  (let [func (if (var? f) @f f)
        methods (->> func class .getDeclaredMethods
                     (map #(vector (.getName %)
                                   (count (.getParameterTypes %)))))
        var-args? (some #(-> % first #{"getRequiredArity"})
                        methods)]
    (if var-args?
      :variadic
      (let [max-arity (->> methods
                           (filter (comp #{"invoke"} first))
                           (sort-by second)
                           last
                           second)]
        (if (and (var? f) (-> f meta :macro))
          (- max-arity 2) ; substract implicit &form and &env arguments
          max-arity)))))


(defn make-inner-map-uniform
  [inner-m]
  (reduce-kv
    (fn [r k* v*]
      (conj r
            [k*
             (cond
               (and (vector? v*)
                    (< (count v*) 2))
               (conj v* (fn [_ data _] data))

               (vector? v*)
               (let [[state f] v*]
                 (if (fn? f)
                   (if (= 3 (arity f))
                     v*
                     [state (fn [_ data _]
                              (f data))])
                   (throw (IllegalArgumentException. (str "expected function but got " (type f))))))

               :else
               [v* (fn [_ data _] data)])]))
    {}
    inner-m))


(defn make-uniform
  "converts a map {:state {:action :new-state
                           :action2 [:new-state2 identity]}}
  to              {:state {:action [:new-state (fn [_ data _] data)
                           :action2 [:new-state2 (fn [_ data _] 
                                                   (identity data))]}}"
  [fsm]
  (reduce-kv (fn [res k m]
               (if (= k :_)
                 (conj res (make-inner-map-uniform {k m}))
                 (conj res
                       [k (make-inner-map-uniform m)])))
             {}
             fsm))


(defn insert-catchall
  "If a catchall for all states is defined 
  insert a catchall `:_` inside the {:action [:new-state f]} map if it doesn't already contain a catchall"
  [fsm]
  (if (contains? fsm :_)
    (let [default (:_ fsm)
          default* (make-inner-map-uniform {:_ default})]
      (reduce-kv (fn [res k v]
                   (if (contains? v :_)
                     (conj res [k v])
                     (conj res [k
                                (merge v default*)])))
                 {}
                 fsm))
    fsm))


(def compile-data
  (comp insert-catchall
        make-uniform))


(defn create-stateless-fsm
  "Takes in a map of `states` and returns a 3-arity function
  that takes the current state, accumulator and an input.
  and returns a map of the new state and `action` applied to the accumulator.
  
  the shape of the map is 
  {:state state
   :acc accumulator}


  `action` is a 1 or 3-arity function defined in states.
  "
  [states]
  (let [fsm* (compile-data states)]
    (fn [state accumulator input]
      (if-let [[state* f] (get-in fsm* [state input])]
        {:state state*
         :acc (f state accumulator input)}
        (if-let [[state* f] (get-in fsm* [state :_])]
          {:state state*
           :acc (f state accumulator input)}
          ;; catch all
          ;; should I return the same state or nil?
          {:state state
           :acc accumulator})))))


(defn create-fsm
  "Takes in a map of `states` and a starting state.

  Returns a 2-arity function that takes an accumulator and an input.
  which returns the value of `action` applied to the accumulator.

  `action` is a 1 or 3-arity function defined in states.
  "
  [states state]
  (let [state* (atom state)
        f (create-stateless-fsm states)]
    (fn [accumulator input]
      (let [{:keys [state acc]} (f @state* accumulator input)]
        (swap! state* (fn [_] state))
        acc))))


(comment 
  (def example-fsm {:state1 {:input2 :state2
                             :input3 [:state3 (fn [_state data _input]
                                                (inc data))]}})


(defn foo
  [_ d _]
  (+ 10 d))


(def fsm
  {:start {\a :found-a}
   :found-a {\a :found-a
             \b [:start (fn [data]
                          (inc data))]
             :_ [:start (fn [_ d _] (+ 10 d))]}
   :_ [:start (fn [data]
                (dec data))]})


(def f (create-fsm fsm :start))


(reduce f 0 "abacx")


(def states
  {0 {5 5
      10 10}
   5 {5 [10]
      10 15}
   10 {5 15
       10 [20 (fn [_data]
                "here is candy")]}
   15 {5 [20 (fn [_data]
               "here is candy")]}})


(get (compile-data states) 5)


(reduce (create-fsm states 0) 0 [5 5 5 5])


;; "here is candy"

(reduce (create-fsm states 0) 0 [10 5 5])


;; "here is candy"

(def states
  {9 {:pass 10
      :fail 9}
   10 {:pass 11
       :fail 10}
   11 {:pass 12
       :fail 11}
   12 {:pass [:college (fn [_] "Get outta here")]
       :fail 12}})


(def tom
  {:grade 9
   :status :pass})


(def jim
  {:grade 9
   :status :fail})


(def jess
  {:grade 12
   :status :pass})

(def next-grade (create-stateless-fsm states))


(next-grade (:grade tom) nil (:status tom)) 
; {:state 10, :data nil}

(next-grade (:grade jim) nil (:status jim))
; {:state 9, :data nil}

(next-grade (:grade jess) nil (:status jess))  ; {:state :college, :data "Get outta here"}

)

