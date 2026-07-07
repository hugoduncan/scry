(ns scry.fixtures.pathological
  (:require [clojure.test :refer [deftest is]]))

(deftest cyclic-failure-actual-does-not-crash-cli
  ;; Produces cyclic assertion data that CLI result-file serialization must
  ;; sanitize without hiding the assertion failure.
  (let [x (java.util.HashMap.)]
    (.put x "self" x)
    (is (= {} x))))

(deftest throwable-with-cyclic-ex-data-does-not-crash-cli
  ;; Produces cyclic ex-data that Throwable normalization must bound while
  ;; preserving the test error signal. The Throwable wrapper avoids clojure.test
  ;; trying to print cyclic ExceptionInfo data before scry captures the error.
  (let [m (java.util.IdentityHashMap.)]
    (.put m :self m)
    (throw (RuntimeException. "boom" (proxy [RuntimeException clojure.lang.IExceptionInfo]
                                            ["root"]
                                       (getData [] {:cyclic m}))))))
