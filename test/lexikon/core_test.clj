(ns lexikon.core-test
  (:require [clojure.test :refer :all]
            [lexikon.core :refer :all]))

(defmacro asserting-warnings [& exprs]
  (cons 'do
        (map (fn [x]
               `(let [s# (with-out-str ~x)]
                  (is (re-find #"\*\*\* Warning \*\*\*" s#))))
             exprs)))

(deftest test-context!-context-and-delete-context!
  (delete-context! :k)
  (context! :k {:a 1 :b 2})
  (context! :k :c 3)
  (is (= {:a 1 :b 2 :c 3} (context :k)))
  (delete-context! :k)
  (is (nil? (context :k))))

(deftest test-lexical-resolve
  (let [x 1
        the-sym 'x
        the-runtime-sym (symbol "abc")
        abc :def]
    (is (= 1    (lexical-resolve 'x)))
    (is (= nil  (lexical-resolve x)))
    (is (= 'x   (lexical-resolve 'the-sym)))
    (is (= 1    (lexical-resolve the-sym)))
    (is (= 'abc (lexical-resolve 'the-runtime-sym)))
    (is (= :def (lexical-resolve the-runtime-sym)))))

(deftest test-binding-context
  (delete-context! :key)
  (context! :key '{x 1 y 2})
  (is (= [1 2] (eval '(lexikon.core/binding-context :key [x y]))))
  (testing "with keywords"
    (delete-context! :key)
    (context! :key {:x 1 :y 2})
    (is (= [1 2] (eval '(lexikon.core/binding-context :key [x y]))))))

(defmacro expand-to-litteral-map []
  '{c 3})

(defmacro expand-to-litteral-vector []
  '[a b c])

(defn f []
  (lexical-context))

(defmacro m []
  (lexical-context))

(defmacro mm []
  `(lexical-context))

(defmacro mmm []
  (f))

(defmacro mmmm []
  `(f))

(def ^:private lc-results
  (let [a 1 b 2 c 3]
    [(lexical-context)
     (f)
     (m)
     (mm)
     (mmm)
     (mmmm)
     (macroexpand '(lexical-context))]))

(deftest test-lexical-context
  (testing "litteral"          (is (= '{a 1 b 2 c 3} (get lc-results 0))))
  (testing "fn"                (is (= {}             (get lc-results 1))))
  (testing "macro litteral"    (is (= '{a 1 b 2 c 3} (get lc-results 2))))
  (testing "macro backtick"    (is (= '{a 1 b 2 c 3} (get lc-results 3))))
  (testing "macro fn"          (is (= {}             (get lc-results 4))))
  (testing "macro backtick fn" (is (= {}             (get lc-results 5))))
  (testing "macroexpand"       (is (= {}             (get lc-results 6)))))

(deftest test-lexical-map
  (let [a 1 b 2 c 3]
    (is (= '{a 1 b 2 c 3}   (lexical-map '[a b c])))
    (is (= '{a 1 b 2 c 3}   (lexical-map [a b c])))
    (is (= '{a 1 b 2 c 3}   (lexical-map a b c)))
    (is (= '{a 1 b 2 c 3}   (lexical-map ['a 'b 'c])))
    (is (= '{a 1 b 2 c 3}   (lexical-map 'a 'b 'c)))
    (is (= {:a 1 :b 2 :c 3} (lexical-map [a b c] :keywords true)))
    (is (= '{a 1 b 2 c 3}   (lexical-map (expand-to-litteral-vector))))
    (is (= {}               (lexical-map '[])))
    (is (= {}               (lexical-map)))
    (asserting-warnings
      (is (= '{a 1 b 2 c 3} (let [kws '[a b c]] (lexical-map kws)))))))

(deftest test-lexical-eval
  (reset! contexts {})

  (is (= 2 (lexical-eval '{x 1} '(+ 1 x))))
  (is (= 2 (lexical-eval {:x 1} '(+ 1 x))))
  (is (= 2 (let [x 1] (lexical-eval (lexical-context) '(+ 1 x)))))
  (is (= 2 (let [ctx '{x 1}] (lexical-eval ctx '(+ 1 x)))))
  (is (= 2 (let [ctx {:x 1}] (lexical-eval ctx '(+ 1 x)))))
  (is (= 2 (let [x 1] (lexical-eval '(+ 1 x)))))

  (is (= {} @contexts)))

(deftest test-letmap
  (reset! contexts {})

  (is (= [1 2 3]   (letmap '{a 1 b 2 c 3} [a b c])))
  (is (= [1 2 3]   (letmap  {a 1 b 2 c 3} [a b c])))
  (is (= [1 2 3]   (letmap  {'a 1 'b 2 'c 3} [a b c])))
  (is (= [1 2 3]   (letmap {:a 1 :b 2 :c 3} [a b c])))
  (is (= [1 2 3]   (let [a 1 b 2] (letmap (expand-to-litteral-map) [a b c]))))
  (asserting-warnings
    (is (= [1 2 3] (let [m '{a 1 b 2 c 3}] (letmap m [a b c]))))
    (is (= [1 2 3] (let [m {:a 1 :b 2 :c 3}] (letmap m [a b c])))))

  (is (= {} @contexts)))
