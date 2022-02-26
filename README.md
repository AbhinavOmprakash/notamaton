# notamaton

A tiny library to create finite state automata or finite state machines using a concise data-driven syntax.

The syntax is dead simple. I'll show you with an example

Let's use the famous vending machine example. 
We have a vending machine that gives out candy 
when a total of 20 cents worth of coins is put in.

we have 5 and 10 cent coins only.

```clj
(require '[notamaton.core :as n])

(def states
  {0 {5 5
      10 10}
   5 {5 10
      10 15}
   10 {5 15
       10 [20 (fn [_]
                "here is candy")]}
   15 {5 [20 (fn [_]
                "here is candy")]}})


(reduce (n/create-fsm states 0) 0 [5 5 5 5]) 
; "here is candy"

(reduce (n/create-fsm states 0) 0 [10 5 5]) 
; "here is candy"

```
The syntax of the states map is simple. 


```clj
{:state1 {:input :new-state}}

;; if you want to pass a function that does some action like returning "here is candy"

{:state1 {:input [:new-state (fn [accumulator]
                               (inc accumulator))]}}

;; note the action function can be a 1 arity or a 3 arity function.
{:state1 {:input [:new-state (fn [state accumulator input]
                                ; do something with state input and accumulator
                               (...))]}}

```


Let's look at another example. Counting the occurences of "ab" in a given string.
example taken from [tilakone](https://github.com/metosin/tilakone)

```clj
(require '[notamaton.core :as n])

(def states
  {:start {\a :found-a}
   :found-a {\a :found-a
             \b [:start (fn [accumulator]
                          (inc accumulator))]}})


(def fsm (n/create-fsm states :start))


(reduce fsm 0 "abaaabaaaab")
; => 3

```
you might notice that the `fsm` would fail if the string contained a letter other than `a` or `b`.

let's add a catchall state.

```clj
(require '[notamaton.core :as n])

(def states
  {:start {\a :found-a}
   :found-a {\a :found-a
             \b [:start (fn [accumulator]
                          (inc accumulator))]}
   :_ :start})


(def fsm (n/create-fsm states :start))

(reduce fsm 0 "xabx")
; => 1

```

The syntax for the catchall state is the same as `{:input [:new-state fn]}`.
let's say we want to substract 1 when we get a match other than "ab"

```clj
(require '[notamaton.core :as n])

(def states
  {:start {\a :found-a}
   :found-a {\a :found-a
             \b [:start (fn [accumulator]
                          (inc accumulator))]}
   :_ [:start (fn [accumulator]
                (dec accumulator))]})


(def fsm (n/create-fsm states :start))

(reduce fsm 0 "xabx")
; => -1

```

You can also add catchalls for a particular state which will take precedence over the default catchall.

```clj
(require '[notamaton.core :as n])

(def states
  {:start {\a :found-a}
   :found-a {\a :found-a
             \b [:start (fn [accumulator]
                          (inc accumulator))]
             :_ [:start (fn [accumulator] (+ 10 accumulator))]}
   :_ [:start (fn [accumulator]
                (dec accumulator))]})

(def fsm (n/create-fsm states :start))

(reduce fsm 0 "abac")
; => 11

(reduce fsm 0 "abacx")
; => 10
```

# Sometimes you want the state machine without the state

"But it's called a state machine, Abhinav!" I know I Know.
Here me out. 
Let's say you have a big web application with a lot of objects that are in different states,
you obviously can't store them in memory, so you store them along with their **state** in the database.
But you still want to know how to transition the state of the object given some input.
That's where the "stateless fsm" comes in (terrible name, I know, let me know if you have a better name).

Here's a scenario, we are writing software for a high school and it determines which grade the kid goes to if they pass or fail.
the problem is, you can have thousands of students, and if you want it to scale to multiple schools, you're going to run out of memory.
So we can't store it in the RAM.


Here's how we can model the state of the students.
```clj
(require '[notamaton.core :as n])

(def states
  {9 {:pass 10
      :fail 9}
   10 {:pass 11
       :fail 10}
   11 {:pass 12
       :fail 11}
   12 {:pass [:college (fn [_] "Get outta here")]
       :fail 12}})

; SOME of our students

(def tom
  {:grade 9
   :status :pass})

(def jim
  {:grade 9
   :status :fail})

(def jess
  {:grade 12
   :status :pass})


;; We create a stateless fsm

(def next-grade (n/create-stateless-fsm states))



(next-grade (:grade tom) nil (:status tom)) 
; {:state 10, :acc nil}

(next-grade (:grade jim) nil (:status jim))
; {:state 9, :acc nil}

(next-grade (:grade jess) nil (:status jess)) 
; {:state :college, :acc "Get outta here"}

```

`create-stateless-fsm` returns a function that takes 3 arguments: `current-state`, `accumulator`, `input`.

