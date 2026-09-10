💡 Kaocha reporter `:end-test-var` is too early to snapshot scry's detailed completed entry: finalized counts, assertion history, and captured output may only exist after leaf `post-test` hooks. Emit concrete completion callbacks from an adapter-owned `post-test` plugin placed exactly once and last, after capture-output and user plugins; use the same leaf-to-canonical conversion as the final result and return the leaf unchanged.

Keep reporter-based progress only for synthetic suite/load errors. Kaocha assertion-error reporter events can omit `:var` even while a concrete var is active, so track begin/end-var state to suppress false synthetic callbacks, then clear it so later unowned errors are reported.

Regression tests should prove callback/final-entry parity and mutate counts, assertion history, and output in a preceding user hook. Also cover a throwing concrete var (one concrete callback, no synthetic duplicate) and an unowned error after var completion.
