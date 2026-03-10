# IMPLEMENTOR PRIVATE CONTEXT

## Task
Fix code review findings in Chainsaw harness HTTP server.

## Plan

**Goal**: Fix 4 code review findings in HarnessServer and its tests.

**Steps**:
1. [x] Read both source files to understand current state
2. [ ] Fix CRITICAL #1 — Assign `engine = server` before I/O call, wrap writePort in try/catch that calls close()
3. [ ] Fix IMPORTANT #2 — Wrap the inline start/close test in try/finally
4. [ ] Fix IMPORTANT #3 — Add malformed JSON test
5. [ ] Fix IMPORTANT #4 — Add response body assertion test
6. [ ] Run tests to verify all pass
7. [ ] Write PUBLIC.md

**Files to touch**:
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/HarnessServer.kt`
- `app/src/test/kotlin/com/glassthought/chainsaw/core/server/KtorHarnessServerTest.kt`

## Status
- COMPLETE. All 4 fixes applied, all 10 tests passing.
