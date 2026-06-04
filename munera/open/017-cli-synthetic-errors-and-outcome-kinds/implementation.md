# Implementation notes

Created from psi integration feedback. No implementation yet.

2026-06-04 architecture review: No new actionable architectural-fit feedback. The design keeps fixes in the CLI-owned progress/result-file/outcome surfaces, uses additive CLI outcome/ex-data classification instead of changing core result maps, and preserves the core/Kaocha dependency boundary; META.md and doc/architecture.md were absent, so review used AGENTS.md/README.md plus design.md.

2026-06-04 ambiguity review: Found five actionable ambiguities in the design. The design needs to pin the exact CLI outcome classification API/key/vocabulary, mixed-signal precedence, how to identify synthetic load/suite errors from canonical results, nil-var progress/result-file naming details and collision behavior, and which public docs must document the machine-readable classification contract. Follow-up items were added to design-steps.md.
