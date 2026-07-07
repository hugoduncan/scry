# Munera Plan

## Open

1. `025-robust-cli-failure-serialization` — Preserve real test failure
   signal and make CLI result serialization robust against pathological
   Throwable/data graphs (cycle-safe/depth-limited EDN sanitizer, bounded
   Throwable normalization, separate diagnostic-error outcome).
