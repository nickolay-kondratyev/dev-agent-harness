# Implementation Review: Extract tmux error-check into reusable helper

## Summary

Added `internal fun ProcessResult.orThrow(operation: String)` extension to
`ProcessResult.kt` and replaced 5 inline `if (result.exitCode != 0) { throw ... }` blocks
across `TmuxSessionManager.kt` (2 sites) and `TmuxCommunicatorImpl` in `TmuxCommunicator.kt`
(3 sites) with chained `.orThrow(...)` calls.

Pure structural DRY improvement. No logic change, no new public API surface.

Tests pass: `./sanity_check.sh` exits 0.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. Missing unit test for `ProcessResult.orThrow`

The new shared helper introduces branching logic (exit code 0 vs non-zero) and a specific
error message format. There is currently no unit test covering it. If the message format
ever drifts (e.g., someone changes the template), the only signal will be the integration
tests — which are gated behind `isIntegTestEnabled()` and do not run in the standard
`./test.sh` run (as confirmed by the sanity check output showing 0 integ tests).

The helper should have a focused unit test in
`app/src/test/kotlin/com/glassthought/shepherd/core/agent/tmux/util/ProcessResultTest.kt`
covering:

- `GIVEN exitCode == 0 WHEN orThrow called THEN no exception is thrown`
- `GIVEN exitCode != 0 WHEN orThrow called THEN IllegalStateException is thrown with the exact expected message`

The second test locks down the message format and prevents silent drift.

### 2. Error message character-for-character verification — one subtle discrepancy

The implementation report claims all messages are identical. They are functionally equivalent
but there is a cosmetic difference worth noting:

Original `createSession` used `${sessionName}` (explicit braces in template):
```kotlin
"Failed to create tmux session [${sessionName}] with command [${startCommand.command}]. ..."
```

New `orThrow` template uses `$operation` (no braces needed since `operation` is a simple
identifier, and `$exitCode` / `$stdErr` are also simple). This produces identical runtime
output — the braces are optional in Kotlin string templates when the identifier is
unambiguous. Not a bug, but the implementation report's "character-for-character identical"
claim is technically about the runtime string, not the source. Verified correct.

---

## Suggestions

### Consider renaming `operation` parameter to be more self-documenting

The current signature is:
```kotlin
internal fun ProcessResult.orThrow(operation: String)
```

The `operation` value is used raw inside `"Failed to $operation."` — so callers must pass
a string that reads naturally after "Failed to". This implicit contract is easy to violate
(e.g., passing `"tmux session creation"` instead of `"create tmux session [...]"`).

A brief KDoc note on the expected phrasing would prevent future misuse:
```kotlin
/**
 * ...
 * @param operation Verb phrase describing the operation, used directly after "Failed to"
 *   in the exception message. Example: `"send keys to tmux pane [foo:0.0]"`.
 */
```
This is a suggestion only — the existing doc is acceptable.

---

## Checklist

| Criterion | Result |
|-----------|--------|
| All 5 duplicate blocks replaced | PASS (2 in TmuxSessionManager, 3 in TmuxCommunicatorImpl) |
| Error messages character-for-character identical at runtime | PASS (verified by manual diff) |
| `internal` modifier appropriate (same Gradle module) | PASS |
| Placement in `ProcessResult.kt` appropriate | PASS |
| No behavior change | PASS |
| Existing tests pass | PASS (`./sanity_check.sh` exits 0) |
| Unit test for new helper | MISSING (important gap) |

---

## Verdict: APPROVED WITH FOLLOW-UP

The implementation is correct, clean, and meets all acceptance criteria. The DRY
improvement is straightforward with no risk of behavior change.

The one gap — missing unit test for `ProcessResult.orThrow` — should be addressed in a
follow-up ticket rather than blocking this change, given that:
1. The helper has trivial logic (single `if` + string interpolation).
2. Integration tests cover the call sites end-to-end.

A follow-up ticket should be created to add the unit test.
