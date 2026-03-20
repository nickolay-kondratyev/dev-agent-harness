# Exploration: End-to-End Integration Test for Straightforward Workflow

## Executive Summary

This exploration document provides a comprehensive guide for implementing an **end-to-end integration test** that exercises the full ticket-driven workflow from CLI invocation through agent execution to completion. The test should validate the "straightforward" workflow, which consists of a single part with an implementation agent (doer).

**Key finding**: The project already has mature integration test infrastructure in place. Building an e2e test requires:
1. Understanding the CLI entry point and startup sequence
2. Leveraging `SharedContextDescribeSpec` for shared infrastructure
3. Creating a ticket fixture and temporary working directory
4. Invoking the binary via `ProcessBuilder` (not just library calls)
5. Implementing callback script execution verification

---

## Part 1: Integration Test Infrastructure (Existing Patterns)

### SharedContextDescribeSpec and SharedContextIntegFactory

**Location**: 
- `app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextDescribeSpec.kt` (ref.ap.20lFzpGIVAbuIXO5tUTBg.E)
- `app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextIntegFactory.kt`

**What it provides**:
```kotlin
abstract class SharedContextDescribeSpec(
    body: SharedContextDescribeSpec.() -> Unit,
    config: SharedContextSpecConfig = SharedContextSpecConfig(),
) : AsgardDescribeSpec(...)
{
    val shepherdContext: ShepherdContext = SharedContextIntegFactory.shepherdContext
}
```

**ShepherdContext contents** (ref.ap.TkpljsXvwC6JaAVnIq02He98.E):
```
ShepherdContext(
  infra: Infra(
    outFactory: OutFactory,
    tmux: TmuxInfra(
      commandRunner: TmuxCommandRunner,
      communicator: TmuxCommunicator,
      sessionManager: TmuxSessionManager,
    ),
    claudeCode: ClaudeCodeInfra(
      agentTypeAdapter: AgentTypeAdapter,  // ClaudeCodeAdapter with GLM enabled
    ),
  ),
  nonInteractiveAgentRunner: NonInteractiveAgentRunner,
  timeoutConfig: HarnessTimeoutConfig = defaults(),
)
```

**Integration test wiring** (from `ContextInitializer.forIntegTest()`):
- GLM is **enabled** — agents are redirected to GLM (Z.AI) instead of real Anthropic API
- Callback scripts directory overridden with sentinel value (replaced by `ServerPortInjectingAdapter`)
- Server port overridden with sentinel value (replaced by `ServerPortInjectingAdapter`)
- No requirement for `TICKET_SHEPHERD_SERVER_PORT` env var during initialization

**Lifecycle**: Process-scoped singleton, initialized once at class-load time via `runBlocking`. NOT closed between tests.

### isIntegTestEnabled() Gate

**Location**: `app/src/test/kotlin/com/glassthought/bucket/integTestSupport.kt`

```kotlin
fun isIntegTestEnabled(): Boolean =
    System.getProperty("runIntegTests") == "true" || TestEnvUtil.isRunningInIntelliJ
```

**Usage pattern**:
```kotlin
describe("GIVEN my integration test").config(isIntegTestEnabled()) {
    // test body
}
```

**Activation**:
- Via Gradle: `./gradlew :app:test -PrunIntegTests=true`
- Via IntelliJ: automatically enabled

---

## Part 2: CLI Entry Point and Binary Invocation

### AppMain (ref.ap.4JVSSyLwZXop6hWiJNYevFQX.E)

**Location**: `app/src/main/kotlin/com/glassthought/shepherd/cli/AppMain.kt`

**Entry point**:
```kotlin
fun main(args: Array<String>) {
    EnvironmentValidator.standard().validate()  // Fail hard if environment is invalid
    val exitCode = CommandLine(ShepherdRunCommand()).execute(*args)
    if (exitCode != 0) System.exit(exitCode)
}
```

**Command structure**:
```
shepherd run --workflow <name> --ticket <path> --iteration-max <N>
```

**CLI parameters**:
- `--ticket` (required): path to ticket markdown file with YAML frontmatter (id, title, status)
- `--workflow` (required): workflow name (e.g., "straightforward", "with-planning")
- `--iteration-max` (required): default iteration budget for reviewer feedback loops

**Ticket requirements**:
- Must have `status: in_progress` in frontmatter
- Must have `id` and `title` fields
- The harness **fails hard** (calls `System.exit(1)`) if `status` is not `in_progress`

### Binary location and invocation:
```bash
./gradlew :app:installDist
./app/build/install/app/bin/app run --workflow straightforward --ticket /path/to/ticket.md --iteration-max 4
```

### How to invoke from test via ProcessBuilder:

```kotlin
val process = ProcessBuilder(
    "./app/build/install/app/bin/app",
    "run",
    "--workflow", "straightforward",
    "--ticket", ticketPath.toString(),
    "--iteration-max", "4",
)
    .directory(repoRoot.toFile())
    .inheritIO()
    .start()

val exitCode = process.waitFor()
```

---

## Part 3: Startup Sequence (ref.ap.HRlQHC1bgrTRyRknP3WNX.E)

### ShepherdInitializer orchestrates full startup

**Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializer.kt` (ref.ap.mFo35x06vJbjMQ8m7Lh4Z.E)

**Sequence**:
1. **EnvironmentValidator.validate()** — checks Docker, tmux, required env vars. Collects ALL failures before throwing.
2. **ContextInitializer.initialize()** → `ShepherdContext` (tmux, logging, adapters, infrastructure)
3. **ShepherdServer** startup — Ktor CIO on `TICKET_SHEPHERD_SERVER_PORT`
4. **TicketShepherdCreator.create()** → `TicketShepherd` (ticket-scoped wiring)
5. **TicketShepherd.run()** — drives workflow (**never returns**, exits process)
6. **Cleanup** — `ShepherdContext.close()` on failure

**Key constraint**: The harness attempts to bind to `TICKET_SHEPHERD_SERVER_PORT`. If the port is in use, it fails hard with a clear error.

---

## Part 4: The Straightforward Workflow

### Workflow definition

**Location**: `config/workflows/straightforward.json`

```json
{
  "name": "straightforward",
  "parts": [
    {
      "name": "main",
      "phase": "execution",
      "description": "Implement and review",
      "subParts": [
        { "name": "impl", "role": "IMPLEMENTATION_WITH_SELF_PLAN", "agentType": "CLAUDE_CODE", "model": "sonnet" },
        { "name": "review", "role": "IMPLEMENTATION_REVIEWER", "agentType": "CLAUDE_CODE", "model": "sonnet",
          "iteration": { "max": 4 } }
      ]
    }
  ]
}
```

**Structure**:
- Single part named "main"
- Two sub-parts: doer (impl) and reviewer (review)
- Doer role: `IMPLEMENTATION_WITH_SELF_PLAN`
- Reviewer role: `IMPLEMENTATION_REVIEWER`
- Both use `CLAUDE_CODE` agent type (spawned via tmux)
- Both use "sonnet" model
- Reviewer has iteration budget of 4

### Part execution flow (PartExecutorImpl)

**Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt` (ref.ap.8qYfNwR3xKpL2mZvJ5cTd.E)

**Doer + Reviewer path**:
1. Spawn doer agent → await `signal/started` via HTTP callback
2. Send doer instructions → await `signal/done COMPLETED` via HTTP callback
3. Validate doer's PUBLIC.md output
4. Git commit doer's work
5. Check context window → optionally perform self-compaction
6. Lazily spawn reviewer (first iteration only) → await `signal/started`
7. Send reviewer instructions → await `signal/done {PASS|NEEDS_ITERATION}`
8. If PASS: part complete, return `PartResult.Completed`
9. If NEEDS_ITERATION: increment iteration counter, re-send doer with reviewer feedback, go to step 2
10. If iteration limit exceeded: return `PartResult.FailedToConverge` (user can extend budget)

---

## Part 5: Agent Spawning and Handshake

### ClaudeCodeAdapter (ref.ap.A0L92SUzkG3gE0gX04ZnK.E)

**Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapter.kt`

**Two main responsibilities**:
1. **buildStartCommand()** — constructs the tmux-invoked bash command
2. **resolveSessionId()** — polls for JSONL file containing handshake GUID (45s timeout)

**Command structure** (simplified):
```bash
bash -c '
  export TICKET_SHEPHERD_SERVER_PORT=<port>
  export TICKET_SHEPHERD_HANDSHAKE_GUID=handshake.<uuid>
  export PATH=$PATH:<scripts_dir>
  cd <workingDir>
  unset CLAUDECODE
  claude --model sonnet <--other args> "<bootstrap_message>"
'
```

**Environment variables exported to agent**:
- `TICKET_SHEPHERD_SERVER_PORT` — port for callback HTTP server
- `TICKET_SHEPHERD_HANDSHAKE_GUID` — unique identifier for this session
- `PATH` — prepended with callback scripts directory
- GLM configuration (if `GlmConfig` is set): `ANTHROPIC_BASE_URL`, `ANTHROPIC_AUTH_TOKEN`, model aliases

### HandshakeGuid

**Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/HandshakeGuid.kt`

Format: `handshake.<UUID>`

**Generated** by harness on agent spawn, passed to agent via env var and bootstrap message.

**Consumed** by agent: written to JSONL file (`.ai_out/.../.claude_code_session_id.jsonl`), enabling harness to resolve session ID.

### AgentFacadeImpl (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E)

**Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt`

**Four public methods**:
1. **spawnAgent(config: SpawnAgentConfig)** → `SpawnedAgentHandle`
   - Creates tmux session
   - Awaits `signal/started` HTTP callback (blocks on CompletableDeferred)
   - Resolves session ID from JSONL file
   - Returns handle with GUID and session ID

2. **sendPayloadAndAwaitSignal(handle, payload)** → `AgentSignal`
   - Writes instruction file (wrapped in ACK protocol)
   - Sends via `tmux send-keys`
   - Awaits signal completion via HTTP callback
   - Validates ACK before returning

3. **readContextWindowState(handle)** → `ContextWindowState`
   - Queries Claude Code session for context window usage
   - Returns `{remainingPercentage: Int?}`

4. **killSession(handle)** → void
   - Kills tmux session, removes from SessionsState

---

## Part 6: Callback Protocol (Agent ↔ Harness Communication)

### callback_shepherd.signal.sh script

**Location**: `app/src/main/resources/scripts/callback_shepherd.signal.sh`

**Usage**:
```bash
callback_shepherd.signal.sh <signal> [args...]
```

**Signal types**:
- `started` — agent initialization complete, ready for instructions
- `ack-payload <payload_id>` — ACK for wrapped instruction payload
- `done <result>` — work complete; result is COMPLETED, PASS, or NEEDS_ITERATION
- `self-compacted` — context window compaction complete
- `user-question <question_id>` — user question needs handling
- `fail-workflow <reason>` — unrecoverable error, abort workflow

**Implementation**:
```bash
#!/usr/bin/env bash
HANDSHAKE_GUID="${TICKET_SHEPHERD_HANDSHAKE_GUID:?}"
SERVER_PORT="${TICKET_SHEPHERD_SERVER_PORT:?}"
curl -X POST "http://localhost:${SERVER_PORT}/callback-shepherd/signal/${1}" \
  -H "X-Handshake-Guid: ${HANDSHAKE_GUID}" \
  -d "payload_id=${2:-}" \
  ...
```

### ShepherdServer routes

**Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/server/ShepherdServer.kt`

**POST endpoints**:
- `/callback-shepherd/signal/started` — marks session ready for payload delivery
- `/callback-shepherd/signal/ack-payload/<payload_id>` — agent ACK'd wrapped instruction
- `/callback-shepherd/signal/done/<result>` — completes agent execution (COMPLETED, PASS, NEEDS_ITERATION)
- `/callback-shepherd/signal/self-compacted` — completes self-compaction phase
- `/callback-shepherd/signal/user-question/<question_id>` — Q&A delivery
- `/callback-shepherd/signal/fail-workflow` — abort signal

**Header**: `X-Handshake-Guid: <guid>` identifies the session.

**Action**: Each endpoint completes a `CompletableDeferred<AgentSignal>` in `SessionsState`, waking the suspended `PartExecutor` coroutine.

---

## Part 7: Integration Test Examples

### Example 1: ClaudeCodeAdapterSpawnIntegTest

**Location**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/ClaudeCodeAdapterSpawnIntegTest.kt`

**What it tests**:
- Spawn real tmux session with ClaudeCodeAdapter
- Session exists and is responsive
- Session ID resolution works
- send-keys works on live session

**Pattern**:
```kotlin
@OptIn(ExperimentalKotest::class)
class ClaudeCodeAdapterSpawnIntegTest : SharedContextDescribeSpec({
    describe("GIVEN ClaudeCodeAdapter with GLM config").config(isIntegTestEnabled()) {
        val sessionManager = shepherdContext.infra.tmux.sessionManager
        val adapter = shepherdContext.infra.claudeCode.agentTypeAdapter
        
        val handshakeGuid = HandshakeGuid.generate()
        val params = BuildStartCommandParams(...)
        val startCommand = adapter.buildStartCommand(params)
        val session = sessionManager.createSession(sessionName, startCommand)
        
        it("THEN session exists") {
            session.exists() shouldBe true
        }
    }
})
```

### Example 2: AgentFacadeImplIntegTest

**Location**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/AgentFacadeImplIntegTest.kt`

**What it tests**:
- Full `AgentFacadeImpl` signal flow end-to-end
- Spawn agent → started signal → payload → done signal
- HTTP server + callback protocol working together
- Session lifecycle (spawn, payload delivery, kill)

**Key infrastructure wired in test**:
```kotlin
val sessionsState = SessionsState()
val shepherdServer = ShepherdServer(sessionsState, outFactory)
val serverPort = ServerSocket(0).use { it.localPort }
val ktorServer = embeddedServer(CIO, port = serverPort) { ... }.start()

val wrappedAdapter = ServerPortInjectingAdapter(
    delegate = realAdapter,
    serverPort = serverPort,
    callbackScriptsDir = scriptsDir,
)

val facade = AgentFacadeImpl(
    sessionsState = sessionsState,
    agentTypeAdapter = wrappedAdapter,
    tmuxSessionCreator = sessionManager,
    ... // other deps
)
```

**Key pattern**: `ServerPortInjectingAdapter` wraps the real adapter and replaces sentinel values with dynamic port/scripts dir.

### Example 3: IntegTestHelpers and IntegTestCallbackProtocol

**Location**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/IntegTestHelpers.kt` and `IntegTestCallbackProtocol.kt`

**Helper functions**:
- `resolveCallbackScriptsDir()` — resolves absolute path to callback scripts
- `createSystemPromptFile()` — creates temp system prompt with callback protocol
- `createDoneInstructionFile()` — creates temp instruction to signal done

**System prompt content**:
```markdown
# Integration Test Agent Protocol

You are a test agent running in an integration test. Follow these rules EXACTLY:

## Callback Protocol

You MUST use `callback_shepherd.signal.sh` to communicate with the harness.

### On startup (FIRST thing you do):
\`\`\`bash
callback_shepherd.signal.sh started
\`\`\`

### When you receive a payload wrapped in XML tags:
1. First ACK the payload:
\`\`\`bash
callback_shepherd.signal.sh ack-payload <payload_id>
\`\`\`

2. Then read and follow the instructions in the payload.

### When done with work:
\`\`\`bash
callback_shepherd.signal.sh done completed
\`\`\`
```

---

## Part 8: GLM (Z.AI) Configuration for Integration Tests

### GLM Redirection

**Location**: `ai_input/memory/deep/integ_tests__use_glm_for_agent_spawning.md`

**Why GLM**: Cost savings, no Anthropic quota consumed, GLM subscription already available via `Z_AI_GLM_API_TOKEN`.

**Mechanism**: Claude Code supports Anthropic-compatible API. Setting env vars before spawning `claude` redirects it to GLM:

```bash
export ANTHROPIC_BASE_URL="https://api.z.ai/api/anthropic"
export ANTHROPIC_AUTH_TOKEN="${Z_AI_GLM_API_TOKEN}"
export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1
export ANTHROPIC_DEFAULT_OPUS_MODEL="glm-5"
export ANTHROPIC_DEFAULT_SONNET_MODEL="glm-5"
export ANTHROPIC_DEFAULT_HAIKU_MODEL="glm-4-flash"
```

**Implementation**: `GlmConfig.standard(authToken)` in `ContextInitializer.forIntegTest()`, passed to `ClaudeCodeAdapter.create()`.

**When to enable**: Integration tests where agents are spawned via tmux. Already enabled in `SharedContextIntegFactory`.

**Token availability**: `Z_AI_GLM_API_TOKEN` is **required** by integration test infrastructure (already checked by `ContextInitializer`). No additional setup needed.

---

## Part 9: ServerPortInjectingAdapter Pattern

### Purpose

Wraps the real `AgentTypeAdapter` and replaces sentinel values in the command with dynamically-assigned values.

**Location**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/ServerPortInjectingAdapter.kt`

**Why needed**: 
- Integration tests create their own Ktor server with dynamic port (cannot pre-assign)
- `ContextInitializer.forIntegTest()` initializes with sentinels (never used by agents)
- At test time, actual port is known, so adapter replaces sentinels in command string

**Implementation**:
```kotlin
class ServerPortInjectingAdapter(
    private val delegate: AgentTypeAdapter,
    private val serverPort: Int,
    private val callbackScriptsDir: String,
) : AgentTypeAdapter {
    override fun buildStartCommand(params: BuildStartCommandParams): TmuxStartCommand {
        val delegateCommand = delegate.buildStartCommand(params)
        
        val modifiedCommand = delegateCommand.command
            .replace(
                "export TICKET_SHEPHERD_SERVER_PORT=$SENTINEL_PORT",
                "export TICKET_SHEPHERD_SERVER_PORT=$serverPort"
            )
            .replace(
                "export PATH=\$PATH:$SENTINEL_SCRIPTS_DIR",
                "export PATH=\$PATH:$callbackScriptsDir"
            )
        
        return TmuxStartCommand(modifiedCommand)
    }
}
```

---

## Part 10: E2E Test Requirements and Challenges

### Requirements for E2E test

1. **Fixture**: A ticket markdown file with valid YAML frontmatter (id, title, status: in_progress)
2. **Temporary directory**: A clean git repo or working tree to avoid polluting the main repo
3. **Binary built**: `./gradlew :app:installDist` before test runs
4. **Process invocation**: Use `ProcessBuilder` to spawn the binary
5. **Environment setup**: Export required env vars (Z_AI_GLM_API_TOKEN, TICKET_SHEPHERD_SERVER_PORT)
6. **Output verification**: Check `.ai_out/` for agent communication files (instructions, PUBLIC.md)
7. **Cleanup**: Kill remaining tmux sessions, remove temp directories

### Key challenges

1. **Process isolation**: The binary exits the JVM on success/failure. Cannot use library calls directly — must capture exit code.
2. **Async agent execution**: Agents run in tmux sessions (long-running). Must poll/wait for completion.
3. **File system state**: Agent writes to `.ai_out/`, creates git commits. Must verify via file inspection.
4. **Port contention**: Must dynamically assign a server port and export it. Cannot rely on `TICKET_SHEPHERD_SERVER_PORT` env var (harness reads it on startup).
5. **Callback script resolution**: Must ensure callback script is on agent's PATH (or use absolute path).
6. **GLM rate limiting**: May hit rate limits if many agents run concurrently. Integration tests should be serialized or use small budgets.

---

## Part 11: File Structure and Communication Artifacts

### .ai_out/ directory layout

```
.ai_out/
  <ticket_id>/
    <part_name>/
      <subpart_name>/
        comm/
          in/
            instructions.md  (harness → agent)
          out/
            PUBLIC.md        (agent → harness)
            PRIVATE.md       (agent internal, may be used in self-compaction)
        ack_payload_<id>.md  (payload wrapper if ACK protocol)
        session_id.txt       (resolved session ID)
```

### Instruction file structure

Harness writes instruction file at test-specified path, then sends via `send-keys`. Instruction includes:
- Current task/context from workflow
- Feedback from reviewer (if iteration)
- Self-compaction instruction (if compaction phase)

### PUBLIC.md validation

After agent signals done, harness reads `comm/out/PUBLIC.md` and validates:
- Must exist
- Must contain YAML frontmatter with `status: done` or `status: completed`
- If reviewer, must have `result: PASS|NEEDS_ITERATION`

---

## Part 12: Key Constants and Timeout Configuration

### HarnessTimeoutConfig

**Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/data/HarnessTimeoutConfig.kt`

**Production defaults**:
```kotlin
HealthTimeoutLadder(
    startup = 30.minutes,      // Max time for agent to signal started
    normalActivity = 120.minutes,
    pingResponse = 5.minutes,   // Max time for agent to respond to health check
)
healthCheckInterval = 10.seconds
payloadAckTimeout = 10.minutes
payloadAckMaxAttempts = 5
```

**Integration test recommendations**:
```kotlin
HarnessTimeoutConfig(
    healthTimeouts = HealthTimeoutLadder(
        startup = 3.minutes,       // GLM may be slow
        normalActivity = 5.minutes,  // Shorter for integ test
        pingResponse = 1.minute,
    ),
    healthCheckInterval = 5.seconds,
    payloadAckTimeout = 2.minutes,
    payloadAckMaxAttempts = 3,
)
```

### Constants

**Location**: `app/src/main/kotlin/com/glassthought/shepherd/core/Constants.kt`

```kotlin
object Constants {
    object AGENT_COMM {
        const val SERVER_PORT_ENV_VAR = "TICKET_SHEPHERD_SERVER_PORT"
        const val HANDSHAKE_GUID_ENV_VAR = "TICKET_SHEPHERD_HANDSHAKE_GUID"
    }
    object REQUIRED_ENV_VARS {
        const val MY_ENV = "MY_ENV"  // Points to ~/.env or similar
        // Z_AI_GLM_API_TOKEN read from ${MY_ENV}/.secrets/Z_AI_GLM_API_TOKEN
    }
}
```

---

## Part 13: Test Execution and Gradle Integration

### Run integration tests

```bash
# All tests (including integration tests)
./gradlew :app:test -PrunIntegTests=true

# Only unit tests (skip integration tests)
./test.sh

# From IntelliJ: automatically enabled, no property needed
```

### Test script

**Location**: `test.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

# Ensure asgard libs are in maven local
source "$(dirname "${BASH_SOURCE[0]}")/_prepare_pre_build.sh"
_prepare_asgard_dependencies

mkdir -p .tmp/
./gradlew :app:test 2>&1 | tee .tmp/test.txt
```

### Gradle wiring (build.gradle.kts)

```kotlin
tasks.test {
    useJUnitPlatform()
    
    // Inject runIntegTests property as system property so isIntegTestEnabled() can read it
    systemProperty("runIntegTests", project.findProperty("runIntegTests") ?: "false")
    
    // Cache invalidation: Gradle treats system properties as inputs
    inputs.property("runIntegTests", project.findProperty("runIntegTests") ?: "false")
}
```

---

## Part 14: Recommended E2E Test Structure

Based on existing patterns, here's the skeleton for an e2e test:

```kotlin
@OptIn(ExperimentalKotest::class)
class StraightforwardWorkflowE2EIntegTest : SharedContextDescribeSpec({
    
    describe("GIVEN straightforward workflow with ticket fixture").config(isIntegTestEnabled()) {
        
        // 1. Create temp directory and ticket fixture
        val tempDir = Files.createTempDirectory("shepherd-e2e-test")
        val ticketFile = tempDir.resolve("ticket.md")
        ticketFile.writeText("""
            ---
            id: test-straightforward-e2e
            title: Test Straightforward E2E
            status: in_progress
            ---
            
            # Test Ticket
            
            Implement a simple feature.
        """.trimIndent())
        
        // 2. Find installed binary
        val binaryPath = Paths.get(System.getProperty("user.dir"))
            .resolve("app/build/install/app/bin/app")
        
        // 3. Allocate server port
        val serverPort = ServerSocket(0).use { it.localPort }
        
        afterSpec {
            // Cleanup: kill remaining tmux sessions, delete temp dir
            ProcessBuilder("tmux", "kill-session", "-t", "*").start().waitFor()
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
        
        describe("WHEN the harness runs with straightforward workflow") {
            
            it("THEN the binary exits with code 0 and produces agent communication files") {
                // 4. Invoke binary
                val process = ProcessBuilder(
                    binaryPath.toString(),
                    "run",
                    "--workflow", "straightforward",
                    "--ticket", ticketFile.toString(),
                    "--iteration-max", "4",
                )
                    .directory(System.getProperty("user.dir").let { File(it) })
                    .environment()["TICKET_SHEPHERD_SERVER_PORT"] = serverPort.toString()
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
                
                // 5. Wait for completion (with generous timeout for GLM)
                val completed = process.waitFor(10, TimeUnit.MINUTES)
                val exitCode = if (completed) process.exitValue() else -1
                
                // 6. Verify results
                exitCode shouldBe 0
                ticketFile.toFile().exists() shouldBe true
                // Check that .ai_out/ has communication files
            }
        }
    }
})
```

---

## Part 15: Key Files Reference

### Core Infrastructure
| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/glassthought/shepherd/cli/AppMain.kt` | CLI entry point (ref.ap.4JVSSyLwZXop6hWiJNYevFQX.E) |
| `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializer.kt` | Startup orchestration (ref.ap.mFo35x06vJbjMQ8m7Lh4Z.E) |
| `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt` | Context initialization (ref.ap.9zump9YISPSIcdnxEXZZX.E) |
| `app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherd.kt` | Central coordinator (ref.ap.Kx7mN3pRvWqY8jZtL5cBd.E) |

### Execution
| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutor.kt` | Part executor interface (ref.ap.2vKbN8rMwXpL5jZqYc7Td.E) |
| `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt` | Part execution logic (ref.ap.8qYfNwR3xKpL2mZvJ5cTd.E) |

### Agent Communication
| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/adapter/ClaudeCodeAdapter.kt` | Claude Code spawning (ref.ap.A0L92SUzkG3gE0gX04ZnK.E) |
| `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt` | Agent lifecycle management |
| `app/src/main/kotlin/com/glassthought/shepherd/core/server/ShepherdServer.kt` | HTTP callback server |
| `app/src/main/resources/scripts/callback_shepherd.signal.sh` | Agent→harness signaling script |

### Integration Tests
| File | Purpose |
|------|---------|
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextDescribeSpec.kt` | Base class for integ tests (ref.ap.20lFzpGIVAbuIXO5tUTBg.E) |
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextIntegFactory.kt` | Shared infrastructure initialization |
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/ClaudeCodeAdapterSpawnIntegTest.kt` | Adapter spawn test example |
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/AgentFacadeImplIntegTest.kt` | Facade signal test example |
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/ServerPortInjectingAdapter.kt` | Dynamic port injection (ref.ap.UsyJHSAzLm5ChDLd0H6PK.E) |
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/IntegTestHelpers.kt` | Test fixture helpers |
| `app/src/test/kotlin/com/glassthought/shepherd/integtest/IntegTestCallbackProtocol.kt` | Callback protocol text |
| `app/src/test/kotlin/com/glassthought/bucket/integTestSupport.kt` | isIntegTestEnabled() gate |

### Configuration
| File | Purpose |
|------|---------|
| `config/workflows/straightforward.json` | Straightforward workflow definition |
| `doc/high-level.md` | Architecture & spec (ref.ap.mmcagXtg6ulznKYYNKlNP.E) |

---

## Part 16: Summary of Key Insights

1. **Process-scoped infrastructure**: `SharedContextIntegFactory` initializes once per test JVM, creating a shared `ShepherdContext` with tmux, logging, and GLM-enabled adapters. Reused across tests for speed.

2. **CLI-based testing**: E2E tests must invoke the binary via `ProcessBuilder`, not library calls, because the harness exits the JVM on completion.

3. **Callback protocol**: Agents signal to harness via HTTP POST to `localhost:PORT/callback-shepherd/signal/...`, with `X-Handshake-Guid` header.

4. **GLM redirection**: Integration tests automatically use GLM (Z.AI) instead of Anthropic API. No configuration needed; already wired in `ContextInitializer.forIntegTest()`.

5. **Straightforward workflow**: Single part with doer (impl) + reviewer, up to 4 iterations. Doer completes with COMPLETED, reviewer responds with PASS or NEEDS_ITERATION.

6. **Port injection**: `ServerPortInjectingAdapter` wraps the real adapter and replaces sentinel values in commands with dynamically-assigned port and callback scripts directory.

7. **Timeout tuning**: Production defaults are very generous (30-120 min). Integ tests should use shorter timeouts (3-5 min) to fail fast on GLM slowness.

8. **Agent command construction**: `buildStartCommand()` exports env vars (server port, handshake GUID, PATH to scripts), then invokes `claude --model sonnet` with a bootstrap message.

9. **Session ID resolution**: Agent writes `.claude_code_session_id.jsonl` file containing handshake GUID. Harness polls this file to resolve the session ID.

10. **File-based communication**: Harness and agent communicate via:
    - **Instructions** (harness → agent): written to `comm/in/instructions.md`, sent via `tmux send-keys`
    - **PUBLIC.md** (agent → harness): written by agent, read by harness after done signal
    - **Callback scripts**: agent executes `callback_shepherd.signal.sh` to POST to HTTP server

---

## Next Steps

When implementing the e2e test:

1. **Extend `SharedContextDescribeSpec`** to avoid redundant infrastructure setup
2. **Create a ticket fixture** with valid YAML frontmatter (id, title, status: in_progress)
3. **Allocate a dynamic server port** via `ServerSocket(0).use { it.localPort }`
4. **Invoke the binary** via `ProcessBuilder`, exporting `TICKET_SHEPHERD_SERVER_PORT`
5. **Wait for process completion** with a generous timeout (10+ minutes for GLM)
6. **Verify results** by checking:
   - Exit code (should be 0)
   - `.ai_out/` structure for communication files
   - Git commits created for agent work
7. **Cleanup**: Kill remaining tmux sessions, remove temp directories
8. **Gate test** with `.config(isIntegTestEnabled())` to skip in default runs
9. **Run with**: `./gradlew :app:test -PrunIntegTests=true`

---

## Appendix: Environment Variables Checklist

### Required for integration test to run

- `Z_AI_GLM_API_TOKEN` — available from `${MY_ENV}/.secrets/Z_AI_GLM_API_TOKEN` (checked by `ContextInitializer`)
- `MY_ENV` — path to environment directory (checked by `EnvironmentValidator`)

### Test must export

- `TICKET_SHEPHERD_SERVER_PORT` — dynamically assigned port for HTTP server (read by harness at startup)

### Agent receives (via ClaudeCodeAdapter)

- `TICKET_SHEPHERD_SERVER_PORT` — same port as above
- `TICKET_SHEPHERD_HANDSHAKE_GUID` — unique identifier for this session
- `PATH` — prepended with callback scripts directory
- GLM env vars (if GLM enabled): `ANTHROPIC_BASE_URL`, `ANTHROPIC_AUTH_TOKEN`, model aliases

