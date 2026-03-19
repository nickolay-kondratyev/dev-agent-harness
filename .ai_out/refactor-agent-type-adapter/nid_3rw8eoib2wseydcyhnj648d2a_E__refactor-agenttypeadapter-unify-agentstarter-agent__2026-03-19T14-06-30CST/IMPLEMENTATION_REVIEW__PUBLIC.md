# Implementation Review: AgentTypeAdapter Refactor

## Summary

Unified `AgentStarter` + `AgentSessionIdResolver` into a single `AgentTypeAdapter` interface
with `ClaudeCodeAdapter` implementation. Old interfaces and implementations correctly deleted.
Tests migrated and passing (34 tests). Detekt clean. Baseline appropriately cleaned up.

**Overall assessment: PASS with two IMPORTANT items to address.**

---

## Checklist

### PASS: Tests pass
- `:app:test` passes (BUILD SUCCESSFUL)
- `sanity_check.sh` passes
- Detekt passes with no new issues

### PASS: Old files deleted
- `AgentStarter.kt`, `ClaudeCodeAgentStarter.kt` -- deleted
- `AgentSessionIdResolver.kt`, `ClaudeCodeAgentSessionIdResolver.kt` -- deleted
- Old test files deleted
- No stale imports or references to old types in `app/src/`

### PASS: `HandshakeGuid.kt` and `ResumableAgentSessionId.kt` preserved
- Both files still exist at `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/`
- Doc references updated to point to new `AgentTypeAdapter` (ref.ap.hhP3gT9qK2mR8vNwX5dYa.E)

### PASS: `resolveSessionId` returns `String` not `ResumableAgentSessionId`
- Matches spec: caller constructs `ResumableAgentSessionId` with additional context

### PASS: Bootstrap message embedded as positional CLI argument
- `shellQuote(params.bootstrapMessage)` added after all flags
- Shell escaping handles `$`, backticks, double quotes, backslashes, `!`

### PASS: Shell escaping correctness
- `shellQuote` wraps in double quotes with proper character escaping
- `escapeForBashC` handles single quotes in the `bash -c '...'` wrapper
- The two-layer escaping (double-quote for the argument, single-quote for bash -c) is correct
- Interaction between the two layers verified: single quotes inside a double-quoted bootstrap
  message are safely handled by `escapeForBashC`

### PASS: Test coverage
- All 33 original tests migrated (adapted for new API surface)
- New tests added for bootstrap message shell escaping (dollar signs, backticks)
- BDD style with GIVEN/WHEN/THEN, one assert per `it` block
- `CountingFakeGuidScanner` reusable fake for polling behavior tests

### PASS: Detekt baseline cleanup
- Removed entries all reference code that no longer exists (GLM/DirectLLM files removed
  on prior branches, `ClaudeCodeAgentSessionIdResolver` removed in this PR)
- Verified: no remaining detekt violations in any of the files whose baseline entries were removed

### PASS: `ContextInitializer` wiring
- `ClaudeCodeInfra` now holds `AgentTypeAdapter` instead of `ClaudeCodeAgentSessionIdResolver`
- Constructor injection, single wiring point

---

## IMPORTANT Issues

### 1. Spec divergence: `buildStartCommand` signature uses `BuildStartCommandParams` instead of `String`

**Spec (ref.ap.A0L92SUzkG3gE0gX04ZnK.E) says:**
```kotlin
interface AgentTypeAdapter {
    fun buildStartCommand(bootstrapMessage: String): TmuxStartCommand
    suspend fun resolveSessionId(handshakeGuid: HandshakeGuid): String
}
```

**Implementation says:**
```kotlin
fun buildStartCommand(params: BuildStartCommandParams): TmuxStartCommand
```

The `BuildStartCommandParams` data class bundles `bootstrapMessage`, `handshakeGuid`, `workingDir`,
`model`, `tools`, `systemPromptFilePath`, and `appendSystemPrompt`.

**Assessment:** The implementation's justification is documented in the PUBLIC.md -- these are all
per-session parameters that the caller must provide, and bundling them avoids a 7-parameter method
signature. However, the spec at ref.ap.A0L92SUzkG3gE0gX04ZnK.E explicitly shows the simpler
signature with only `bootstrapMessage`. The old `ClaudeCodeAgentStarter` took these as constructor
parameters (per-instance), not method parameters.

**Action needed:** Either:
- (a) Update the spec to match the implementation (document why `BuildStartCommandParams` is
  preferred over constructor injection for per-session values), OR
- (b) Move per-session config back to the constructor (matching the old pattern where
  `ClaudeCodeAgentStarter` was instantiated per-session)

Option (a) seems more appropriate since one `ClaudeCodeAdapter` instance should serve multiple
sessions. But the spec must be updated to reflect this decision.

### 2. Secondary constructor for test injection is public, not `internal`

In `ClaudeCodeAdapter.kt` (line 78), the comment says "Internal constructor" but the constructor
has no visibility modifier, making it `public` by default in Kotlin.

```kotlin
// Internal constructor allows tests to inject a fake GuidScanner.
constructor(
    guidScanner: GuidScanner,
    outFactory: OutFactory,
    ...
```

This means production code could accidentally use this constructor and inject an arbitrary
`GuidScanner`, bypassing `FilesystemGuidScanner`. The constructor should be marked `internal`
to match the stated intent.

**Fix:**
```kotlin
internal constructor(
    guidScanner: GuidScanner,
    ...
```

---

## Suggestions

### 1. Mutable `guidScanner` field

The `guidScanner` property is declared as `var` to support overwriting from the secondary
constructor:

```kotlin
private var guidScanner: GuidScanner = FilesystemGuidScanner(claudeProjectsDir, dispatcherProvider)
```

This creates an unnecessary `FilesystemGuidScanner` with `PLACEHOLDER_PATH` when the test
constructor is used, only to immediately discard it. Consider using a factory pattern or
making the primary constructor accept `GuidScanner` directly (with a companion factory for
production use) to avoid the throwaway object and mutable field.

### 2. `PLACEHOLDER_PATH` sentinel

`Path.of("/dev/null")` is used as a sentinel for the primary constructor when the test
constructor is used. This creates a `FilesystemGuidScanner` pointing at `/dev/null` that
is then overwritten. While it works, it couples the primary constructor to a workaround.
A cleaner approach would be a single primary constructor that takes `GuidScanner`, with a
companion `fun create(claudeProjectsDir: Path, ...)` factory for production.

### 3. Pre-existing: `TICKET_SHEPHERD_SERVER_PORT` not exported in start command

The spec (ref.ap.A0L92SUzkG3gE0gX04ZnK.E and spawn flow steps) shows:
```bash
export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.xxx && export TICKET_SHEPHERD_SERVER_PORT=8347 && claude ...
```

But neither the old `ClaudeCodeAgentStarter` nor the new `ClaudeCodeAdapter` exports
`TICKET_SHEPHERD_SERVER_PORT`. This is **not a regression** (the old code had the same gap),
but it is a spec deviation worth a follow-up ticket.

---

## Documentation Updates Needed

1. **Spec update**: ref.ap.A0L92SUzkG3gE0gX04ZnK.E in `doc/use-case/SpawnTmuxAgentSessionUseCase.md`
   should be updated to show `BuildStartCommandParams` instead of `buildStartCommand(bootstrapMessage: String)`,
   or the implementation should be changed to match the spec.

---

## Verdict

**PASS** -- pending the two IMPORTANT items above. The refactor is well-executed, tests are
thorough, escaping logic is correct, cleanup is complete. The spec divergence on the method
signature is the most significant item and needs resolution (either update spec or change code).
