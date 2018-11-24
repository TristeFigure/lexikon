# [lexikon](https://www.youtube.com/watch?v=wkNMf9wsy5Y)

*Black Smif-N-Wessun (comin to shake ya frame)*\
*Remember the name (nothin change)*\
*We dismember you lames*\
*Duck Down when we take aim, remainin on point*\
*Is how we stay ahead of the game, like links and change*\
*(To maintain is the main thing) The name change, the game change*\
*(But we still the same) Just elevated to a higher plane* \
*Commin to shake ya brain, commin to shake ya frame*

## Usage

```clojure
[lexikon "0.2.0"]
```

```clojure
(ns my-ns
  (:require [lexikon.core :refer :all]))
```

## [API doc](https://tristefigure.github.io/lexikon/)

## A note on the lexical environment in Clojure

To summarize, a lexical environment is a map binding symbols (in code) to their values (at runtime). In Clojure, these symbols are only available in a reified form at macroexpansion time while their values are only worked out at runtime. You have to do the splits from compile time to run time in order to build a reified form of the lexical environment. This is mainly what this library does.

## Capturing the lexical environment

```clojure
(let [a 1]
  (lexical-context))
;; => {'a 1}
```

### in macros

Since the lexical environment includes runtime values, it is only available at runtime in its full form. However, this can make writing macros that manipulate the lexical environment difficult, and this is why `lexical-context` does not behave the same way depending on whether it is called from a macro or a normal form. In a macro, it will expand to code that will yield the lexical environment in the expansion rather than in the macro. Observe:

```clojure
(defmacro m []
  (let [b 2]
    ;; at macroexpansion-time
    (println (lexical-context))
    ;;   {(quote a) a}
    (println (lexical-context :local true))
    ;;   {&form (m)
    ;;    &env {a #object[clojure.lang.Compiler$LocalBinding...]}
    ;;    b 2}
    
    ;; at runtime
    `(println (lexical-context))
    ;;   {a 1}))

(let [a 1]
  (m))
```

### partial capture

```clojure
(let [a 1 b 2 c 3]
  (lexical-map [a b])                 ; => {'a 1 'b 2}
  (lexical-map [a b] :keywords true)) ; => {:a 1 :b 2}
```

Instead of a vector of symbols, you can also pass a symbol provided it will resolve to a seq of symbols at runtime. Doing so will incur a call to eval at runtime. Set `*warn-on-late-eval*` to `false` to suppress the warning this will generate.

## Storing and reusing the lexical environment

```clojure
(let [a 1]
  (store-locals! :key))

(binding-stored-locals :key
  (println "unstored:" a))
;; unstored: 1
```

## `lexical-eval`

Evaluate code in the local lexical context.

```clojure
(let [a 1]
  (lexical-eval '(+ 1 a)))
; => 2
```

## `lexical-resolve`

Resolve a symbol in the current or given lexical context.

```clojure
(let [a 1]
  (lexical-resolve 'a))
; => 1
```

## `letmap`

```clojure
(letmap '{a 1 b 2}
  [a b])
=> [1 2]
```

Like `lexical-map`, `letmap` can accept a symbol as input map provided it will resolve to a map binding symbols to values at runtime. Set `*warn-on-late-eval*` to `false` to suppress the warning this will generate.

## Contexts

Lower-level functions used by this library.

```clojure
(context! :ctx {'a 123})
(context! :ctx 'b 456)

(context :ctx)
;; => [{'a 123 'b 456}] 

(binding-context :ctx
  (+ a b))
;; => 579

(delete-context! :ctx)
```

## License

Copyright © 2018 TristeFigure

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
