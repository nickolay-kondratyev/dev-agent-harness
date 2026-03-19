# ReInstructAndAwait Implementation Review

## Overall Verdict: PASS

The implementation is clean, spec-compliant, well-tested, and follows project conventions. No critical or important issues found.

---

## Category Verdicts

| Category | Verdict |
|----------|---------|
| Spec Compliance | PASS |
| Test Coverage | PASS |
| Code Quality | PASS |
| Security | PASS (N/A -- no security surface) |
| Architecture / Pattern Consistency | PASS |
| No Hacks | PASS |

---

## Spec Compliance: PASS

The implementation matches the spec (`doc/use-case/ReInstructAndAwait.md` / ref.ap.QZYYZ2gTi1D2SQ5IYxOU6.E) precisely:

- **`ReInstructOutcome` sealed class**: All three variants (`Responded`, `FailedWorkflow`, `Crashed`) match the spec definition exactly, including property names and types.
- **`ReInstructAndAwait` interface**: `suspend fun execute(handle: SpawnedAgentHandle, message: String): ReInstructOutcome` matches the spec signature.
- **Signal mapping**: `Done` -> `Responded`, `FailWorkflow` -> `FailedWorkflow`, `Crashed` -> `Crashed` all correct.
- **SelfCompacted handling**: The spec says "handled inside facade" (line 136 of spec). The implementation maps it to `Crashed` with a clear diagnostic message, which is the correct defensive approach -- if the facade fails to handle it, surfacing it as a crash is appropriate rather than silently ignoring.
- **Anchor points**: `ap.QZYYZ2gTi1D2SQ5IYxOU6.E` correctly referenced (not duplicated -- spec owns it). Implementation class has its own AP `ap.fXi4IJBxh0ez1Z7tvoamj.E`.

### message -> AgentPayload bridging

The spec defines `message: String`, and `AgentFacade.sendPayloadAndAwaitSignal` takes `AgentPayload(instructionFilePath: Path)`. The implementation bridges via `Path.of(message)` at line 86 of `ReInstructAndAwait.kt`. This is clean and correct -- the `message` parameter represents a file path string per the spec's "message content" which in this architecture is always a file path to an instruction file assembled by `ContextForAgentProvider`.

---

## Test Coverage: PASS

12 tests covering all spec-defined scenarios:

| Spec Scenario | Covered | Test Location |
|---------------|---------|---------------|
| Agent responds with Done (COMPLETED) | Yes | Lines 44-63 |
| Agent responds with Done (PASS) | Yes | Lines 65-79 |
| Agent responds with Done (NEEDS_ITERATION) | Yes | Lines 82-97 |
| Agent crashes | Yes | Lines 99-118 |
| Agent signals fail-workflow | Yes | Lines 120-139 |
| Agent self-compacts (unexpected) | Yes | Lines 141-162 |
| Payload/handle verification | Yes | Lines 164-184 |

Tests follow project standards:
- BDD style with GIVEN/WHEN/THEN nesting
- One logical assertion per `it` block
- Uses `FakeAgentFacade` for isolation
- Extends `AsgardDescribeSpec`
- No `delay` for synchronization
- Fail hard (FakeAgentFacade throws on unprogrammed methods)

---

## Code Quality: PASS

- **SRP**: `ReInstructAndAwaitImpl` has exactly one responsibility -- bridge message to payload and map signal to outcome.
- **DRY**: No duplication. The whole point of this class is to eliminate duplication at call sites.
- **Explicit**: No magic numbers, no hidden behavior. The `when` exhaustively covers all `AgentSignal` variants without an `else` branch, so the compiler will enforce coverage if new variants are added.
- **Constructor injection**: Single dependency (`AgentFacade`), no framework.
- **Composition over inheritance**: Interface + implementation, no inheritance hierarchy.
- **Immutability**: All data flows are immutable.

---

## Suggestions

### 1. Minor: Anchor point placement on sealed class

The anchor point `ap.QZYYZ2gTi1D2SQ5IYxOU6.E` appears as a comment on line 18 of the implementation rather than as an `@AnchorPoint` annotation. The spec owns this AP, so the comment-style reference is actually correct (only the spec should have the canonical definition). The `@AnchorPoint` annotation on the impl class (`ap.fXi4IJBxh0ez1Z7tvoamj.E`) is the right call. No change needed.

### 2. Consider: KDoc on the `message` parameter

The `message` parameter name could be slightly misleading -- it represents a file path, not a text message. The KDoc on the interface method does not explicitly call out that `message` is a file path string. This is a very minor documentation improvement:

```kotlin
// Current (line 60-63):
suspend fun execute(
    handle: SpawnedAgentHandle,
    message: String,
): ReInstructOutcome

// Suggestion -- add @param:
/**
 * @param message Path to the instruction file to deliver to the agent.
 */
```

However, this is spec-defined as `message: String`, so changing the parameter name would diverge from spec. The KDoc improvement is optional.

---

## Documentation Updates Needed

None. The implementation is self-contained, and CLAUDE.md already references the spec via anchor points.

---

## Tests Verified

- `sanity_check.sh`: PASS
- `test.sh` (includes `:app:test` and detekt): PASS
- All 12 `ReInstructAndAwaitImplTest` tests: PASS
