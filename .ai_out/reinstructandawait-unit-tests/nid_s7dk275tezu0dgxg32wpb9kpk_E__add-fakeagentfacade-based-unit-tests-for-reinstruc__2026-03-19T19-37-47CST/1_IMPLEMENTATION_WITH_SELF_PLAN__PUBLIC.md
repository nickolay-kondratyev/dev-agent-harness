# Add FakeAgentFacade-based Unit Tests for ReInstructAndAwaitImpl

## What was done

Added 4 new test scenarios to `ReInstructAndAwaitImplTest` using FakeAgentFacade interaction verification:

1. **Sequential execute calls on same handle** -- Programs FakeAgentFacade with an `ArrayDeque` returning NEEDS_ITERATION then COMPLETED. Verifies `sendPayloadCalls` records exactly 2 entries with correct payloads and handle identity.

2. **Explicit Path equality for instructionFilePath** -- Verifies that `sendPayloadCalls[0].payload.instructionFilePath` equals `Path.of(message)` using explicit Path equality, strengthening the path contract.

3. **Multiple execute calls with different handles** -- Creates two `SpawnedAgentHandle` instances with different GUIDs ("doer" and "reviewer"). Verifies handle identity is preserved in the recorded calls.

4. **AgentFacade throws exception propagates uncaught** -- Programs FakeAgentFacade to throw `IllegalStateException`. Verifies `ReInstructAndAwaitImpl` does NOT swallow exceptions using `shouldThrow<IllegalStateException>`.

## Files modified

- `app/src/test/kotlin/com/glassthought/shepherd/usecase/reinstructandawait/ReInstructAndAwaitImplTest.kt`
  - Added `shouldThrow` and `ArrayDeque` imports
  - Modified `buildHandle()` to accept optional `guidValue` parameter (default preserved)
  - Added 4 new `describe` blocks with 8 new `it` assertions

## Tests

All 20 tests pass (12 existing + 8 new `it` blocks across the 4 scenarios):
```
BUILD SUCCESSFUL
```

## Decisions

- Used `IllegalStateException` (via `error()`) instead of `RuntimeException` for scenario 4, because detekt's `TooGenericExceptionThrown` rule flagged `RuntimeException`. Using `error()` is idiomatic Kotlin and still validates exception propagation.
- Each `it` block in scenario 1 builds its own `FakeAgentFacade` with a fresh `ArrayDeque`, since `it` blocks run independently and a shared deque would exhaust across blocks.
