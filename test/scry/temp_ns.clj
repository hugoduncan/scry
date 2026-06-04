(ns scry.temp-ns
  (:require
   [clojure.string :as str]))

(defonce ^:private run-id
  (str "r" (str/replace (str (java.util.UUID/randomUUID)) "-" "")))

(defonce ^:private counter
  (atom 0))

(defn unique-ns
  "Return a generated test namespace symbol with a collision-resistant run id
  and monotonic per-process suffix.

  The generated names are intended for temporary project tests that load real
  namespaces into the current JVM. Sharing this source across adjacent tests
  prevents accidental reuse in long-lived REPL sessions."
  [prefix leaf]
  (symbol (str prefix "." run-id ".n" (swap! counter inc) "." leaf)))
