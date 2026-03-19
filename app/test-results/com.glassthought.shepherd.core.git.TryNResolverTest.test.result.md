---
spec: "com.glassthought.shepherd.core.git.TryNResolverTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN TryNResolver
  - WHEN .ai_out/ directories for try-1 and try-2 exist
    - [PASS] THEN returns 3
  - WHEN .ai_out/ directories for try-1 and try-3 exist but not try-2
    - [PASS] THEN returns 2 (first gap)
  - WHEN .ai_out/ directory for try-1 exists
    - [PASS] THEN returns 2
  - WHEN .ai_out/ exists but contains directories for a different ticket
    - [PASS] THEN returns 1 (unrelated directories are ignored)
  - WHEN no .ai_out/ directory exists
    - [PASS] THEN returns 1
