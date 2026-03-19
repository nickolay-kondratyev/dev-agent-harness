# Implementation Review: ClaudeCodeAdapter Integration Test

## Summary

**Verdict: NEEDS_ITERATION**

The implementation adds a `GlmConfig` data class, wires GLM env-var injection into `ClaudeCodeAdapter.buildStartCommand()`, refactors `ContextInitializer` to share the ZAI API key, and creates both unit tests and an integration test. The production code and unit tests are well-structured. However, there are two blocking issues (one security-adjacent, one architectural) and the integration test does not validate two of the four original requirements.

---

## CRITICAL Issues

### 1. Shell injection via unquoted env var values in `GlmConfig.toEnvVarExports()`

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-6/app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/GlmConfig.kt` (line 39-46)

`toEnvVarExports()` interpolates values directly into shell export statements without any quoting:

```kotlin
fun toEnvVarExports(): String = listOf(
    "export ANTHROPIC_BASE_URL=$baseUrl",
    "export ANTHROPIC_AUTH_TOKEN=$authToken",
    // ...
).joinToString(" && ")
```

If `authToken` (or any other value) contains shell metacharacters -- spaces, `$`, backticks, `&&`, `;`, etc. -- the resulting command will break or execute unintended commands. While the token currently comes from a file read, this is a latent injection vector. The `baseUrl` is also unquoted.

The outer `escapeForBashC()` only escapes single quotes for the `bash -c '...'` wrapper; it does NOT sanitize the inner command semantics.

**Fix:** Quote env var values with double quotes inside the export statements:

```kotlin
fun toEnvVarExports(): String = listOf(
    "export ANTHROPIC_BASE_URL=\"$baseUrl\"",
    "export ANTHROPIC_AUTH_TOKEN=\"$authToken\"",
    "export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1",
    "export ANTHROPIC_DEFAULT_OPUS_MODEL=\"$defaultOpusModel\"",
    "export ANTHROPIC_DEFAULT_SONNET_MODEL=\"$defaultSonnetModel\"",
    "export ANTHROPIC_DEFAULT_HAIKU_MODEL=\"$defaultHaikuModel\"",
).joinToString(" && ")
```

Note that `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1` is a constant and does not need quoting.

---

## IMPORTANT Issues

### 2. GLM config always wired in production -- architectural concern

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-6/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt` (lines 115-117)

The implementation doc acknowledges: "GlmConfig is always wired in production `ContextInitializer`." This means **every production-spawned agent will be redirected to GLM**. The deep memory explicitly describes GLM as a test-time concern:

> "Integration tests that spawn real Claude Code agents MUST use GLM (Z.AI) instead of Claude."

If the intention is that production agents should also use GLM, this is fine but should be explicitly documented as an intentional decision (not just a side-effect of the integration test ticket). If not, this is a behavioral change to production that warrants a separate config flag (e.g., the `Environment.isTest` concept referenced in the deep memory).

**Action needed:** Explicit confirmation from the human engineer that production agents should always route through GLM. If not, make `glmConfig` injection conditional or move it to test-only wiring.

### 3. Integration test does not validate 2 of 4 original requirements

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-6/app/src/test/kotlin/com/glassthought/shepherd/integtest/ClaudeCodeAdapterSpawnIntegTest.kt`

The ticket requirements were:
- (a) Bootstrap handshake completes (`/signal/started` received) -- **NOT TESTED**
- (b) Session ID resolution finds the correct JSONL file with the GUID -- **Tested** (the `resolveSessionId` test)
- (c) Start command produces a working interactive session (not `-p` mode) -- **Partially tested** (tmux session exists, but no verification of interactivity)
- (d) Agent can receive `send-keys` input after bootstrap -- **NOT TESTED**

Requirements (a) and (d) are absent from the integration test. The test verifies command construction and session ID resolution but does not exercise the handshake protocol or `send-keys` communication.

**Fix:** Either add test cases for requirements (a) and (d), or document explicitly why they were deferred (perhaps as a follow-up ticket).

### 4. Unsafe cast in integration test

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-6/app/src/test/kotlin/com/glassthought/shepherd/integtest/ClaudeCodeAdapterSpawnIntegTest.kt` (line 35)

```kotlin
val adapter = shepherdContext.infra.claudeCode.agentTypeAdapter as ClaudeCodeAdapter
```

This is a downcast from the `AgentTypeAdapter` interface to the concrete `ClaudeCodeAdapter`. If the wiring ever changes to a different implementation (decorator, wrapper), this test will fail with a confusing `ClassCastException` rather than a meaningful message.

**Fix:** Either add a descriptive error message:
```kotlin
val adapter = shepherdContext.infra.claudeCode.agentTypeAdapter as? ClaudeCodeAdapter
    ?: error("Expected ClaudeCodeAdapter but got ${shepherdContext.infra.claudeCode.agentTypeAdapter::class.simpleName}")
```
Or expose `resolveSessionId` through the `AgentTypeAdapter` interface if it should be part of the integration test contract.

---

## Suggestions

### 5. Integration test command-string assertions duplicate unit tests

The `WHEN the start command is inspected` describe block in `ClaudeCodeAdapterSpawnIntegTest` (lines 98-126) re-validates that the command string contains specific substrings (GLM vars, model flag, permissions flag). These are already thoroughly covered in unit tests. Integration tests should focus on **end-to-end behavior** (does the agent actually start, respond, resolve session ID) rather than re-asserting string content that unit tests cover.

Consider removing the command-string inspection block from the integration test to keep it focused on its unique value: real agent spawning and session resolution.

### 6. `GlmConfig.toEnvVarExports()` return type could be more explicit

The method returns a raw `String` that is an internal shell-command fragment. Consider returning a typed wrapper (e.g., `ShellCommandFragment`) to make it clear this is not a general-purpose string and should only be used in shell command construction. This is a low-priority suggestion.

---

## What Looks Good

1. **`GlmConfig` data class** -- Clean, immutable, well-documented with KDoc and anchor point. The `standard()` factory provides sensible defaults.

2. **`ContextInitializer` refactoring** -- Extracting `readZaiApiKey()` into its own method is a good SRP improvement. Sharing the token between `NonInteractiveAgentRunner` and `ClaudeCodeAdapter` avoids reading the file twice.

3. **Unit tests are thorough** -- GLM config injection tests cover present/absent cases, ordering (GLM before `cd`), and all 6 env vars. The `GlmConfig.standard()` factory tests verify default mappings. Existing tests were not removed.

4. **BDD style and one-assert-per-test** -- All new tests follow the project's testing standards consistently.

5. **`afterEach` cleanup** in the integration test properly kills tmux sessions, following the pattern from `TmuxSessionManagerIntegTest`.

6. **No pre-existing tests were removed** -- All previous `ClaudeCodeAdapterTest` tests remain intact.
