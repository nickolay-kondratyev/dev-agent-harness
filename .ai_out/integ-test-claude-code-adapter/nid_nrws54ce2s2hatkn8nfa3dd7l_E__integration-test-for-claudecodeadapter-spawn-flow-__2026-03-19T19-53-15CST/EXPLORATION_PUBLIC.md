# Exploration: ClaudeCodeAdapter Integration Test

## 1. ClaudeCodeAdapter

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapter.kt`
**AP:** `ap.gCgRdmWd9eTGXPbHJvyxI.E`

Implements `AgentTypeAdapter`. Constructor is `internal` -- tests can inject a fake `GuidScanner`.
Production wiring via `ClaudeCodeAdapter.create(claudeProjectsDir, outFactory, ...)`.

### `buildStartCommand()` -- Full Code

```kotlin
override fun buildStartCommand(params: BuildStartCommandParams): TmuxStartCommand {
    val parts = mutableListOf("claude")

    parts.add("--model")
    parts.add(params.model)

    if (params.tools.isNotEmpty()) {
        parts.add("--tools")
        parts.add(params.tools.joinToString(","))
    }

    if (params.systemPromptFilePath != null) {
        val flag = if (params.appendSystemPrompt) {
            "--append-system-prompt-file"
        } else {
            "--system-prompt-file"
        }
        parts.add(flag)
        parts.add(params.systemPromptFilePath)
    }

    parts.add("--dangerously-skip-permissions")

    // Bootstrap message as positional initial prompt argument (after all flags).
    parts.add(shellQuote(params.bootstrapMessage))

    val claudeCommand = parts.joinToString(" ")
    val innerCommand = "cd ${params.workingDir} && " +
        "unset CLAUDECODE && " +
        "export ${Constants.AGENT_COMM.HANDSHAKE_GUID_ENV_VAR}=${params.handshakeGuid.value} && " +
        claudeCommand
    val fullCommand = "bash -c '${escapeForBashC(innerCommand)}'"

    return TmuxStartCommand(fullCommand)
}
```

**Key observation for GLM injection:** The `innerCommand` string is where env var exports are
prepended. GLM injection would add `export ANTHROPIC_BASE_URL=...` etc. before the `cd`.
Currently there is NO `Environment.isTest` check -- the deep memory says this is **not yet
implemented**.

### `resolveSessionId()` -- How It Works

1. Calls `pollUntilFound(handshakeGuid)` inside a `withTimeout(resolveTimeoutMs)`.
2. `pollUntilFound` loops: calls `guidScanner.scan(guid)`, returns on non-empty matches, otherwise `delay(pollIntervalMs)`.
3. After polling: expects exactly 1 match. Takes `matchingFiles.single().nameWithoutExtension` as the session ID.
4. Zero matches after timeout or multiple matches -> `IllegalStateException`.

Default timeout: 45s, poll interval: 500ms. The `GuidScanner` is the seam for testing.

---

## 2. AgentTypeAdapter Interface

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/AgentTypeAdapter.kt`
**AP:** `ap.hhP3gT9qK2mR8vNwX5dYa.E`

```kotlin
interface AgentTypeAdapter {
    fun buildStartCommand(params: BuildStartCommandParams): TmuxStartCommand
    suspend fun resolveSessionId(handshakeGuid: HandshakeGuid): String
}

data class BuildStartCommandParams(
    val bootstrapMessage: String,
    val handshakeGuid: HandshakeGuid,
    val workingDir: String,
    val model: String,
    val tools: List<String>,
    val systemPromptFilePath: String?,
    val appendSystemPrompt: Boolean,
)
```

---

## 3. GuidScanner Interface

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/GuidScanner.kt`

```kotlin
fun interface GuidScanner {
    suspend fun scan(guid: HandshakeGuid): List<Path>
}
```

Private production implementation `FilesystemGuidScanner` walks `claudeProjectsDir` for `*.jsonl`
files containing the GUID string.

---

## 4. HandshakeGuid

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/HandshakeGuid.kt`
**AP:** `ap.tzGA4RjdwGjQr9oZ0U2PsjhW.E`

```kotlin
@JvmInline
value class HandshakeGuid(val value: String) {
    companion object {
        private const val PREFIX = "handshake."
        fun generate(): HandshakeGuid = HandshakeGuid("$PREFIX${UUID.randomUUID()}")
    }
}
```

---

## 5. SpawnAgentConfig

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/SpawnAgentConfig.kt`
**AP:** `ap.nDDZyl11vax5mqhyAiiDr.E`

```kotlin
data class SpawnAgentConfig(
    val partName: String,
    val subPartName: String,
    val subPartIndex: Int,
    val agentType: AgentType,
    val model: String,
    val role: String,
    val systemPromptPath: Path,
    val bootstrapMessage: String,
)
```

---

## 6. GLM Env Var Injection for Integration Tests

**Deep memory file:** `ai_input/memory/deep/integ_tests__use_glm_for_agent_spawning.md`

### Required Env Vars to Redirect Claude Code to GLM (Z.AI)

```bash
export ANTHROPIC_BASE_URL="https://api.z.ai/api/anthropic"
export ANTHROPIC_AUTH_TOKEN="${Z_AI_GLM_API_TOKEN}"
export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1
export ANTHROPIC_DEFAULT_OPUS_MODEL="glm-5"
export ANTHROPIC_DEFAULT_SONNET_MODEL="glm-5"
export ANTHROPIC_DEFAULT_HAIKU_MODEL="glm-4-flash"
```

### Implementation Hook

The deep memory specifies injecting these into `ClaudeCodeAdapter.buildStartCommand()` via
`environment.isTest` flag. However:

- **`Environment` interface with `isTest` does NOT exist yet** in the codebase.
- **No TODO markers for GLM injection** were found in the current source (the `ap.ifrXkqXjkvAajrA4QCy7V.E` referenced in the deep memory is absent from the code).
- The deep memory states: "As of 2026-03-11, GLM injection for tmux-spawned agents is **not yet implemented**."

### Token Availability

`Z_AI_GLM_API_TOKEN` is read from `${MY_ENV}/.secrets/Z_AI_GLM_API_TOKEN` by `ContextInitializerImpl`
(line 137 in `ContextInitializer.kt`). It is used for `NonInteractiveAgentRunner` but the same
token can be reused for agent spawning GLM redirect.

---

## 7. ContextInitializer -- How ClaudeCodeAdapter Is Wired

**File:** `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt`
**AP:** `ap.9zump9YISPSIcdnxEXZZX.E`

```kotlin
val claudeCodeInfra = ClaudeCodeInfra(
    agentTypeAdapter = ClaudeCodeAdapter.create(
        claudeProjectsDir = Constants.CLAUDE_CODE.defaultProjectsDir(),
        outFactory = outFactory,
    ),
)
```

`Constants.CLAUDE_CODE.defaultProjectsDir()` = `~/.claude/projects`.

---

## 8. Environment.isTest -- Status

**Does NOT exist.** The deep memory references `environment.isTest` as the trigger for GLM
injection, but no `Environment` interface exists in the codebase. The `EnvironmentValidator`
(`app/src/main/kotlin/com/glassthought/shepherd/core/initializer/EnvironmentValidator.kt`,
AP `ap.A8WqG9oplNTpsW7YqoIyX.E`) validates Docker and required env vars but has no `isTest`
concept.

---

## 9. Integration Test Infrastructure

### `isIntegTestEnabled()`

**File:** `app/src/test/kotlin/com/glassthought/bucket/integTestSupport.kt`

```kotlin
fun isIntegTestEnabled(): Boolean =
    System.getProperty("runIntegTests") == "true" || TestEnvUtil.isRunningInIntelliJ
```

Enable via: `./gradlew :app:test -PrunIntegTests=true`

### SharedContextDescribeSpec

**File:** `app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextDescribeSpec.kt`
**AP:** `ap.20lFzpGIVAbuIXO5tUTBg.E`

Base class providing shared `ShepherdContext` singleton. Extends `AsgardDescribeSpec`.
Exposes `shepherdContext` property for access to infra deps.

### SharedContextIntegFactory

**File:** `app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextIntegFactory.kt`

Process-scoped singleton. Initializes `ShepherdContext` via `ContextInitializer.standard().initialize()`.
Not closed between tests -- held for JVM lifetime.

---

## 10. Example Integration Test Pattern

**File:** `app/src/test/kotlin/com/glassthought/bucket/TmuxSessionManagerIntegTest.kt`

```kotlin
@OptIn(ExperimentalKotest::class)
class TmuxSessionManagerIntegTest : SharedContextDescribeSpec({

    describe("GIVEN TmuxSessionManager").config(isIntegTestEnabled()) {
        val sessionManager = shepherdContext.infra.tmux.sessionManager
        val createdSessions = mutableListOf<TmuxSession>()

        afterEach {
            // Clean up any sessions created during tests.
            createdSessions.forEach { session ->
                try {
                    sessionManager.killSession(session)
                } catch (_: Exception) {
                    // Session may already be killed by the test.
                }
            }
            createdSessions.clear()
        }

        describe("WHEN session is created") {
            it("THEN exists() returns true") {
                val sessionName = "test-exists-${System.currentTimeMillis()}"
                val session = sessionManager.createSession(sessionName, TmuxStartCommand("bash"))
                createdSessions.add(session)
                session.exists() shouldBe true
            }
        }

        describe("WHEN killSession is called on existing session") {
            it("THEN exists() returns false") {
                val sessionName = "test-kill-${System.currentTimeMillis()}"
                val session = sessionManager.createSession(sessionName, TmuxStartCommand("bash"))
                sessionManager.killSession(session)
                session.exists() shouldBe false
            }
        }
    }
})
```

**Key patterns:**
1. Extends `SharedContextDescribeSpec` (not `AsgardDescribeSpec` directly)
2. Top-level `describe` gated with `.config(isIntegTestEnabled())`
3. `@OptIn(ExperimentalKotest::class)` annotation on the class
4. Access shared deps via `shepherdContext.infra.*`
5. `afterEach` cleanup for created tmux sessions
6. Unique session names using timestamp to avoid collisions

---

## 11. Integration Test Section from SpawnTmuxAgentSessionUseCase.md

From `doc/use-case/SpawnTmuxAgentSessionUseCase.md` section "Integration Testing -- ClaudeCodeAdapter":

> `ClaudeCodeAdapter` **must** be validated by integration tests against a real Claude Code session.
> This confirms:
> - The JSONL file format assumption still holds (Claude Code could change it across versions)
> - GUID matching works end-to-end (env var -> bootstrap message -> JSONL write -> scan -> resolve)
> - Start command construction produces a valid, launchable command
>
> **Resource efficiency:** Session ID resolution testing should be part of a **broader integration
> test that spawns a single real agent session** and validates multiple concerns from that one
> session (e.g., bootstrap handshake, session ID resolution, callback protocol). Spawning a
> separate agent session just for resolver testing would be wasteful.

---

## 12. Summary of Key Gaps for Implementation

| Gap | Description |
|-----|-------------|
| `Environment.isTest` | Does not exist. Deep memory references it for GLM injection trigger. |
| GLM env var injection | Not implemented in `buildStartCommand()`. No TODO markers in code. |
| `ClaudeCodeAdapter` integ test | Does not exist yet. This is the ticket's objective. |
| `Z_AI_GLM_API_TOKEN` in spawn | Token is read by `ContextInitializer` for `NonInteractiveAgentRunner` but NOT passed to `ClaudeCodeAdapter` or injected into spawn commands. |
