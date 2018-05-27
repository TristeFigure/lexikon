(ns lexikon.core
  (:require [clojure.spec.alpha :as s]
            [shuriken.spec :refer [conf either conform!]]))

(def contexts
  "The global store for contexts. An atom containing a map of maps:
    `{key -> {symbol -> value}}`"
  (atom {}))

(defn context!
  "Stores a map in memory under key `k`. Merges with the previous
  context if present.

  ```clojure
  (context! :k {'a 123})
  (context! :k 'b 456)

  (context :k)
  => {'a 123 'b 456}
  ```"
  ([k key value]
   (context! k {key value}))
  ([k ctx]
    (swap! contexts #(assoc % k (merge (get % k) ctx)))))

(defn context
  "Fetches a map stored in memory under key `k`. If `kk` is provided,
  fetches `kk` in `k`."
  ([k]
   (get @contexts k))
  ([k kk]
   (let [ctx (context k)]
     (if-let [found (get ctx kk)]
         found
         (get ctx (if (symbol? kk)
                    (-> kk name keyword)
                    (-> kk name symbol)))))))

(defn delete-context!
  "Deletes the map stored in memory under key `k`."
  [k]
  (swap! contexts dissoc k)
  nil)

(defmacro binding-context
  "Retrieves a stored lexical context from memory and binds it using
  `let`.

  ```clojure
  (context! :k {'a 1})

  (binding-context :k
    (inc a))
  => 2
  ```"
  [key & body]
  (let [locals (or (->> (context key)
                        keys
                        (map #(-> % name symbol))
                        vec)
                   [])]
    `(let ~(vec (mapcat (fn [l]
                          [l `(context ~key '~l)])
                        locals))
       ~@body)))

(defn- dequote [code]
  (if (and (seq? code) (-> code first (= 'quote)))
    (second code)
    code))

(defmacro lexical-context
  "Returns the current lexical context as a map from symbols to
  values. In macros (or anywhere `&env` is a local in scope),
  `lexical-context` will expand to code that will return the context
  of the expanded form rather than the context of the macro itself.
  Use `(lexical-context :local true)` to override this behavior.

  ```clojure
  (let [a 1 b 2 c 3]
    (lexical-context))
  => {'a 1 'b 2 'c 3}
  ```"
  [& {:as opts}]
  (if (and (or (contains? &env '&env))
           (not (:local opts)))
    ;; then we are in the context of a macro
    `(->> (keys ~'&env)
          (map #(vector `(quote ~%) %))
          (into {}))
    ;; else we are in the context of a normal form or want to get the
    ;; lexical context of the macro we are in.
    (->> (keys &env)
         (map #(vector `(quote ~%) %))
         (into {}))))

(defmacro lexical-eval
  "Evaluates code in the given lexical context. If no context is
  passed, [[lexical-context]] is used.

  ```clojure
  (let [a 1]
    (eval '(inc a))           ; CompilerException:
                              ;   Unable to resolve symbol: a
    (lexical-eval (inc a))    ; => 2
    (lexical-eval {'a 22} a)) ; => 22
  ```"
  ([code]
   `(lexical-eval (lexical-context) ~code))
  ([ctx code]
   `(let [k# (keyword (gensym "lexical-eval-"))]
      (try
        (context! k# ~ctx)
        (eval (list 'binding-context k# ~code))
        (finally
          (delete-context! k#))))))

;; TODO: meditate
(def ^:dynamic *warn-on-late-eval* true)

(defmacro ^:no-doc print-late-eval-warning [line x]
  `(do (println "*** Warning ***")
       (println "@" (format "%s:%s" *file* ~line))
       (println (str \' ~x \')
                "is a not a litteral map nor macro-expands to one. This will"
                "incur a call to eval at runtime.")
       (println "Set" `*warn-on-late-eval* "to false to silence this warning.")
       (newline)))

(s/def ::lexical-map-args
       (s/cat
         :params (either
                   :wrapped #(not= % :keywords)
                   :spliced (s/* #(not= % :keywords))
                   :unform  :wrapped)
         :keywords (conf (s/* (conf (s/cat
                                      :keywords-keyword #{:keywords}
                                      :keywords         any?)
                                    #(:keywords %)
                                    #(array-map :keywords-keyword :keywords
                                                :keywords %)))
                         first vector)))

(defmacro lexical-map
  "Turns a collection of symbols into a map of symbols to their value
  in the current lexical context.
  If `params` is not a litteral nor expands to one, `lexical-eval`
  will be used at runtime.
  However this will print a warning. Set `*warn-on-late-eval*` to
  false to silence it.

  ```clojure
  (let [a 1 b 2 c 3]
    (lexical-map '[a b])                 ; => {'a 1 'b 2}
    (lexical-map '[a b] :keywords true)) ; => {:a 1 :b 2}
  ```"
  [& args]
  (let [args (->> args dequote (map dequote))
        {:keys [params keywords] :or {keywords false}}
        (conform! ::lexical-map-args args)
        params (let [expanded (macroexpand params)]
                 (dequote (if (sequential? expanded)
                            (mapv dequote expanded)
                            expanded)))
        ctx-sym (gensym "ctx-")]
    (if-not (and (sequential? params) (every? symbol? params))
      ;; then params needs evaluation
      `(let [p# ~params]
         (when *warn-on-late-eval*
           (print-late-eval-warning ~(-> &form :line) p#))
         (lexical-eval (lexical-context)
                       (->> p#
                            (map (fn [sym#]
                                   [(if ~keywords
                                      (keyword sym#)
                                      `'~sym#)
                                    sym#]))
                            (into {}))))
      ;; else it is or has been expanded to a litteral
      `(let [~ctx-sym (lexical-context)]
         ~(->> params
               (map (fn [sym]
                      `[~(if keywords
                           (keyword sym)
                           `'~sym)
                        (get ~ctx-sym '~sym)]))
               (into {}))))))

(defmacro letmap
  "Binds each key of `m` to the corresponding value. Keys in `m` must
  be litteral symbols. If `m` is not a litteral map nor a macro that
  expands to one, `lexical-eval` will be used at runtime.
  However this will print a warning. Set `*warn-on-late-eval*` to
  false to silence it. `m` can be quoted or not.

  ```clojure
  (letmap '{a 1 b 2}
    [a b])
  => [1 2]
  ```"
  [m & body]
  (let [m (dequote (macroexpand m))]
    (if-not (map? m)
      ;; then m needs evaluation
      (let [k-sym (keyword (gensym "letmap-"))]
        `(let [m# ~m]
           (when *warn-on-late-eval*
             (print-late-eval-warning ~(-> &form meta :line) m#))
           (lexical-eval (merge (lexical-context) m#)
                         '(do ~@body))))
      ;; else it is or has been expanded to a litteral
      (let [m (->> m
                   (map (fn [[k v]]
                          [(dequote k) v]))
                   (into {}))]
        (assert (every? #(or (symbol? %) (keyword %))
                        (keys m)))
        (assert (every? (comp not namespace)
                        (keys m)))
        (let [m (->> m
                     (map (fn [[k v]]
                            [(if (symbol? k) `(quote ~k) k)
                             v]))
                     (into {}))
              m-sym (gensym "m")
              bindings
              (->> m
                   (map (fn [[k _v]]
                          (let [sym (if (keyword? k)
                                      (-> k name symbol)
                                      (dequote k))]
                            [sym `(get ~m-sym ~k)])))
                   (apply concat)
                   vec)]
          `(let [~m-sym ~m]
             (let ~bindings ~@body)))))))
