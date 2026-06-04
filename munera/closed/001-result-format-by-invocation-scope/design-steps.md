# Design Review Follow-up

- [x] Specify the canonical default top-level collection key(s) for formatted entries (for example, whether scoped results continue to use `:failures`, switch to/add `:results`, or expose both for compatibility).
- [x] Clarify whether single namespace and single var scoped results include entries for all executed vars, only failing/erroring vars, or a different filtered set so passing assertion details have a defined location.
- [x] Define the entry shape/status semantics for passing vars/assertions when assertion details are included, including what `:status` should be for fully passing vars.
- [x] Clarify scope classification when options contain both `:vars` and `:namespaces`, or when explicit `:vars` contains non-test vars that are filtered out before execution.
