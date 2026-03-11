# Detailed Implementation Plan: Wire up TmuxAgentSession Spawn Flow

## 1. Problem Understanding

### Goal
Implement the end-to-end TmuxAgentSession spawn flow: given a `StartAgentRequest`, choose an agent type, build the CLI start command, create a tmux session running that command, perform the GUID handshake to discover the agent's session ID, and return a `TmuxAgentSession` that bundles the live tmux handle with the resumable session identity.

Prove the flow works with an integration test that spawns a real Claude Code session in tmux, performs the GUID handshake, resolves the session ID, and verifies the returned `TmuxAgentSession`.

### Key Constraints
- Constructor injection only; no DI framework
- `Out`/`OutFactory` for all logging
- Integration test gated with `isIntegTestEnabled()`
- `Environment.isTest == true` triggers slim test configuration (minimal tools, sonnet model)
- `TestEnvironment` is `internal` to the `initializer.data` package but accessible from tests in the same module

### Assumptions
- `PhaseType` is a simple enum for V1 -- enough to represent the workflow concept but not over-engineered
- The integration test will use `--dangerously-skip-permissions` to avoid interactive permission prompts in tmux
- `--tools "Read,Write"` restricts the tool set to minimize context window usage in tests
- `--model sonnet` keeps test costs low
- The integration test sends the GUID as the first prompt message via `sendKeys`, which is the established handshake pattern
- No production wiring into `AppDependencies` is needed in this ticket; `SpawnTmuxAgentSessionUseCase` will be composed at the call site by future tickets

## 2. High-Level Architecture

### Component Diagram (text)

```
StartAgentRequest(phaseType)
       |
       v
SpawnTmuxAgentSessionUseCase
  |-- AgentTypeChooser.choose(request) --> AgentType
  |-- AgentStarterBundleFactory.create(agentType, phaseType) --> AgentStarterBundle
  |      |-- AgentStarter (builds TmuxStartCommand)
  |      |-- AgentSessionIdResolver (resolves session ID from GUID)
  |-- TmuxSessionManager.createSession(name, command) --> TmuxSession
  |-- TmuxSession.sendKeys(guid) --> triggers GUID handshake
  |-- AgentSessionIdResolver.resolveSessionId(guid) --> ResumableAgentSessionId
  |-- return TmuxAgentSession(tmuxSession, resumableAgentSessionId)
```

### Data Flow
1. Caller creates `StartAgentRequest` with `PhaseType`
2. `SpawnTmuxAgentSessionUseCase.spawn(request)` orchestrates the full flow
3. `AgentTypeChooser` maps the request to an `AgentType` (e.g., always `CLAUDE_CODE` in V1)
4. `AgentStarterBundleFactory` creates the agent-type-specific bundle:
   - `AgentStarter` knows how to build the CLI command (e.g., `claude --model sonnet --tools "Read,Write" ...`)
   - `AgentSessionIdResolver` knows how to discover session IDs for that agent type
5. `AgentStarter.buildStartCommand()` returns a `TmuxStartCommand`
6. `TmuxSessionManager.createSession()` launches a detached tmux session with the command
7. The GUID is sent via `TmuxSession.sendKeys()` as the first prompt
8. `AgentSessionIdResolver.resolveSessionId(guid)` polls until the agent writes the GUID to its session file
9. The result is packaged as `TmuxAgentSession`

## 3. Implementation Phases

### Phase 1: Data Types and Enums

**Goal**: Create the foundational data types that all other components depend on.

**Components to create**:

1. **`PhaseType`** enum
   - File: `app/src/main/kotlin/com/glassthought/chainsaw/core/data/PhaseType.kt`
   - Package: `com.glassthought.chainsaw.core.data` (alongside `AgentType`)
   - Values: `IMPLEMENTOR`, `REVIEWER`, `PLANNER`, `PLAN_REVIEWER`
   - Rationale: These match the workflow phase types described in the project overview. Kept as enum because all values are known at compile time.

2. **`TmuxStartCommand`** value class
   - File: `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/data/TmuxStartCommand.kt`
   - Package: `com.glassthought.chainsaw.core.agent.data`
   - Structure: `@JvmInline value class TmuxStartCommand(val command: String)`
   - Rationale: Wraps the raw shell command string for type safety. The tmux session manager takes a `String` command, so this provides a typed boundary before that call.

3. **`StartAgentRequest`** data class
   - File: `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/data/StartAgentRequest.kt`
   - Package: `com.glassthought.chainsaw.core.agent.data`
   - Structure:
     ```kotlin
     data class StartAgentRequest(
         val phaseType: PhaseType,
         val workingDir: String,
     )
     ```
   - Rationale: `workingDir` is needed because the agent needs to know which directory to operate in. Extensible for future fields (agent type override, model preference).

4. **`TmuxAgentSession`** interface + data class impl
   - File: `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/TmuxAgentSession.kt`
   - Package: `com.glassthought.chainsaw.core.agent`
   - Interface:
     ```kotlin
     interface TmuxAgentSession {
         val tmuxSession: TmuxSession
         val resumableAgentSessionId: ResumableAgentSessionId
     }
     ```
   - Default implementation:
     ```kotlin
     data class DefaultTmuxAgentSession(
         override val tmuxSession: TmuxSession,
         override val resumableAgentSessionId: ResumableAgentSessionId,
     ) : TmuxAgentSession
     ```
   - Rationale: Interface allows different creation paths (spawn vs resume) to produce the same type.

**Dependencies**: None
**Verification**: Unit compiles. No tests needed for data types.

---

### Phase 2: AgentStarter Interface and ClaudeCodeAgentStarter

**Goal**: Create the interface for building agent start commands and the Claude Code implementation.

**Components to create**:

1. **`AgentStarter`** interface
   - File: `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/starter/AgentStarter.kt`
   - Package: `com.glassthought.chainsaw.core.agent.starter`
   - Interface:
     ```kotlin
     interface AgentStarter {
         fun buildStartCommand(): TmuxStartCommand
     }
     ```
   - Rationale: Clean SRP -- one responsibility: produce the command string. No `context` parameter needed; the starter is already configured with everything it needs via constructor injection.

2. **`ClaudeCodeAgentStarter`** implementation
   - File: `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/starter/impl/ClaudeCodeAgentStarter.kt`
   - Package: `com.glassthought.chainsaw.core.agent.starter.impl`
   - Constructor params:
     ```kotlin
     class ClaudeCodeAgentStarter(
         private val workingDir: String,
         private val model: String,
         private val tools: List<String>,
         private val systemPromptFilePath: String?,
         private val dangerouslySkipPermissions: Boolean,
     ) : AgentStarter
     ```
   - `buildStartCommand()` builds: `claude --model <model> --tools "<tools>" [--system-prompt-file <path>] [--dangerously-skip-permissions]`
   - The working directory is NOT a CLI flag -- it is set via the tmux session's working directory (i.e., `cd <workingDir> && claude ...`). So the command will be: `bash -c 'cd <workingDir> && claude <flags>'`

   **Claude Code CLI Flags Reference** (from [official docs](https://code.claude.com/docs/en/cli-reference)):
   - `--model <model>`: Sets the model. Accepts alias (`sonnet`, `opus`) or full name (`claude-sonnet-4-6`).
   - `--tools <tools>`: Restricts available built-in tools. `"Read,Write"` or `"Bash,Edit,Read"` etc.
   - `--system-prompt-file <path>`: Replaces entire system prompt with file contents.
   - `--append-system-prompt-file <path>`: Appends file contents to default system prompt (preserves built-in behavior).
   - `--dangerously-skip-permissions`: Skips all permission prompts (needed for non-interactive tmux usage).
   - `--resume <session-id>`: Resume a specific session by ID.
   - `--allowedTools <tools>`: Pre-approve tools without prompting (different from `--tools` which restricts available tools).
   - `-p, --print`: Non-interactive mode (NOT what we want -- we want interactive sessions in tmux).

   **IMPORTANT design decision**: Use `--append-system-prompt-file` (not `--system-prompt-file`) for production use to preserve Claude Code's built-in capabilities. Use `--system-prompt-file` only in test mode where we want minimal context. The starter receives the flag choice via its constructor -- the factory decides.

**Dependencies**: Phase 1 (`TmuxStartCommand`)
**Verification**: Unit test for `ClaudeCodeAgentStarter.buildStartCommand()` verifying the command string contains expected flags.

**Unit test file**: `app/src/test/kotlin/com/glassthought/chainsaw/core/agent/starter/impl/ClaudeCodeAgentStarterTest.kt`

Test cases:
- GIVEN starter with all flags WHEN buildStartCommand THEN command contains --model, --tools, --system-prompt-file, --dangerously-skip-permissions
- GIVEN starter without system prompt file WHEN buildStartCommand THEN command does not contain --system-prompt-file
- GIVEN starter with dangerouslySkipPermissions=false WHEN buildStartCommand THEN command does not contain --dangerously-skip-permissions

---

### Phase 3: AgentTypeChooser

**Goal**: Create the interface and default implementation for choosing agent type from a request.

**Components to create**:

1. **`AgentTypeChooser`** interface + default impl
   - File: `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/AgentTypeChooser.kt`
   - Package: `com.glassthought.chainsaw.core.agent`
   - Interface:
     ```kotlin
     interface AgentTypeChooser {
         fun choose(request: StartAgentRequest): AgentType
     }
     ```
   - Default implementation:
     ```kotlin
     class DefaultAgentTypeChooser : AgentTypeChooser {
         override fun choose(request: StartAgentRequest): AgentType = AgentType.CLAUDE_CODE
     }
     ```
   - Rationale: In V1, all phases use Claude Code. The interface allows future extension to PI or other agent types without modifying the spawn use case.

**Dependencies**: Phase 1 (`StartAgentRequest`, `AgentType`)
**Verification**: Simple unit test -- `choose()` always returns `CLAUDE_CODE`.

**Unit test file**: `app/src/test/kotlin/com/glassthought/chainsaw/core/agent/DefaultAgentTypeChooserTest.kt`

---

### Phase 4: AgentStarterBundle and AgentStarterBundleFactory

**Goal**: Create the bundle that pairs an `AgentStarter` with an `AgentSessionIdResolver`, and the factory that creates bundles.

**Components to create**:

1. **`AgentStarterBundle`** data class
   - File: `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/AgentStarterBundle.kt`
   - Package: `com.glassthought.chainsaw.core.agent`
   - Structure:
     ```kotlin
     data class AgentStarterBundle(
         val starter: AgentStarter,
         val sessionIdResolver: AgentSessionIdResolver,
     )
     ```

2. **`AgentStarterBundleFactory`** interface + impl
   - File: `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/AgentStarterBundleFactory.kt`
   - Package: `com.glassthought.chainsaw.core.agent`
   - Interface:
     ```kotlin
     interface AgentStarterBundleFactory {
         fun create(agentType: AgentType, request: StartAgentRequest): AgentStarterBundle
     }
     ```
   - Implementation: `ClaudeCodeAgentStarterBundleFactory`
     - File: `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/impl/ClaudeCodeAgentStarterBundleFactory.kt`
     - Constructor injection: `environment: Environment`, `outFactory: OutFactory`
     - `create()` builds:
       - `ClaudeCodeAgentStarter` with phase-appropriate flags:
         - Test mode (`environment.isTest == true`): `--model sonnet`, `--tools "Read,Write"`, `--system-prompt-file <test-prompt>`, `--dangerously-skip-permissions`
         - Production mode: `--model sonnet`, `--tools "Bash,Edit,Read,Write,Glob,Grep"`, `--append-system-prompt-file <prod-prompt>`, `--dangerously-skip-permissions`
       - `ClaudeCodeAgentSessionIdResolver` with `~/.claude/projects` dir
     - Only supports `AgentType.CLAUDE_CODE` -- throws `IllegalArgumentException` for other types

   **Design decision**: The factory takes `request` (not just `phaseType`) to allow future extensibility. PhaseType is accessed via `request.phaseType`.

   **SIMPLIFICATION**: For V1, phase-type does NOT change the command flags. All phases use the same flags. This avoids premature complexity. The phase-type field is there for future use. The environment (test vs production) is what drives the config differences.

**Dependencies**: Phase 1, Phase 2
**Verification**: Unit test verifying factory creates bundles with correct starter config for test vs production environments.

**Unit test file**: `app/src/test/kotlin/com/glassthought/chainsaw/core/agent/impl/ClaudeCodeAgentStarterBundleFactoryTest.kt`

---

### Phase 5: Add `Environment.test()` Factory Method

**Goal**: Add a public factory method for test environment creation, parallel to `Environment.production()`.

**File to modify**: `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/data/Environment.kt`

**Change**: Add to the `Environment` companion object:
```kotlin
fun test(): Environment = TestEnvironment()
```

**Rationale**: Currently `TestEnvironment()` is `internal`, usable only within the module. Adding a public factory method follows the same pattern as `production()` and allows the `ClaudeCodeAgentStarterBundleFactory` (and integration tests) to create test environments idiomatically. The existing `EnvironmentTest` directly instantiates `TestEnvironment()` which works because it's in the same module -- that test should be updated to also use `Environment.test()`.

**Dependencies**: None
**Verification**: Update `EnvironmentTest` to also test `Environment.test()`.

---

### Phase 6: System Prompt File for Tests

**Goal**: Create a minimal system prompt file that will be used when `Environment.isTest == true`.

**File to create**: `app/src/main/resources/prompts/test-agent-system-prompt.txt`

**Content**: A minimal prompt that tells the agent to simply respond to the input (the GUID) without doing anything complex. Something like:
```
You are a test agent. Simply acknowledge the input you receive. Do not perform any actions.
```

**Rationale**: The test needs the agent to start up and process the GUID message, but we don't want it to do anything expensive. A minimal system prompt reduces context window usage and cost.

**Access pattern**: The starter builds the path from classpath or absolute path. For simplicity, the `ClaudeCodeAgentStarterBundleFactory` resolves the resource to a temp file path at bundle creation time (Claude CLI needs a file path, not a classpath resource).

**SIMPLIFICATION**: Actually, the system prompt file path can be passed directly as a constructor param to `ClaudeCodeAgentStarterBundleFactory` rather than trying to resolve classpath resources. The integration test can create its own temp file. The factory receives `testSystemPromptFilePath: String?` and uses it when `environment.isTest`.

**Dependencies**: None
**Verification**: File exists and is readable.

---

### Phase 7: SpawnTmuxAgentSessionUseCase

**Goal**: Create the main orchestrator that wires together all the components to spawn a new agent session.

**File to create**: `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/SpawnTmuxAgentSessionUseCase.kt`
**Package**: `com.glassthought.chainsaw.core.agent`

**Constructor**:
```kotlin
class SpawnTmuxAgentSessionUseCase(
    private val agentTypeChooser: AgentTypeChooser,
    private val bundleFactory: AgentStarterBundleFactory,
    private val tmuxSessionManager: TmuxSessionManager,
    outFactory: OutFactory,
)
```

**Method**:
```kotlin
suspend fun spawn(request: StartAgentRequest): TmuxAgentSession
```

**Flow** (inside `spawn`):
1. Generate a `HandshakeGuid` (UUID-based)
2. `val agentType = agentTypeChooser.choose(request)`
3. `val bundle = bundleFactory.create(agentType, request)`
4. `val startCommand = bundle.starter.buildStartCommand()`
5. Generate a unique session name (e.g., `"chainsaw-{phaseType}-{timestamp}"`)
6. `val tmuxSession = tmuxSessionManager.createSession(sessionName, startCommand.command)`
7. `tmuxSession.sendKeys(guid.value)` -- sends the GUID as the first message
8. `val sessionId = bundle.sessionIdResolver.resolveSessionId(guid)`
9. Return `DefaultTmuxAgentSession(tmuxSession, sessionId)`

**Key design decisions**:
- GUID generation happens inside the use case (not injected) -- it's a simple `UUID.randomUUID()` call
- Session naming uses `"chainsaw-${request.phaseType.name.lowercase()}-${System.currentTimeMillis()}"` for uniqueness and debuggability
- Logging at each step using `Out`

**Dependencies**: Phases 1-6
**Verification**: Unit test with fakes for all dependencies. Integration test in Phase 9.

**Unit test file**: `app/src/test/kotlin/com/glassthought/chainsaw/core/agent/SpawnTmuxAgentSessionUseCaseTest.kt`

Unit test approach:
- Create fake implementations: `FakeAgentTypeChooser`, `FakeAgentStarterBundleFactory`, and test `TmuxSessionManager` behavior with a `FakeTmuxCommandRunner` or mock the session manager
- ACTUALLY: The use case calls `tmuxSessionManager.createSession()` which creates a real `TmuxSession`. For a pure unit test, we should extract a `TmuxSessionCreator` interface or... SIMPLIFICATION: just test via the integration test. The use case is a thin orchestrator -- its logic is trivial (call A, then B, then C). Over-mocking would test the mocks, not the code. The integration test in Phase 9 is the real verification.
- **Decision**: One focused unit test that verifies the session name format. The integration test covers the full flow.

---

### Phase 8: ResumeTmuxAgentSessionUseCase

**Goal**: Create the use case for resuming an existing agent session.

**File to create**: `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/ResumeTmuxAgentSessionUseCase.kt`
**Package**: `com.glassthought.chainsaw.core.agent`

**Constructor**:
```kotlin
class ResumeTmuxAgentSessionUseCase(
    private val tmuxSessionManager: TmuxSessionManager,
    outFactory: OutFactory,
)
```

**Method**:
```kotlin
suspend fun resume(
    resumableAgentSessionId: ResumableAgentSessionId,
    workingDir: String,
): TmuxAgentSession
```

**Flow**:
1. Build resume command: `claude --resume <sessionId>` (for Claude Code agent type)
2. Wrap in `bash -c 'cd <workingDir> && claude --resume <sessionId>'`
3. Generate session name: `"chainsaw-resume-{timestamp}"`
4. `val tmuxSession = tmuxSessionManager.createSession(sessionName, command)`
5. Return `DefaultTmuxAgentSession(tmuxSession, resumableAgentSessionId)`

**Key difference from spawn**: No GUID handshake needed -- we already have the session ID.

**Dependencies**: Phase 1
**Verification**: No integration test per ticket scope. A simple unit test or deferred to future ticket.

---

### Phase 9: Integration Test

**Goal**: Prove the full spawn flow works end-to-end with a real Claude Code session.

**File to create**: `app/src/test/kotlin/com/glassthought/chainsaw/core/agent/SpawnTmuxAgentSessionUseCaseIntegTest.kt`
**Package**: `com.glassthought.chainsaw.core.agent`

**Test structure** (BDD):
```
@OptIn(ExperimentalKotest::class)
class SpawnTmuxAgentSessionUseCaseIntegTest : AsgardDescribeSpec({

    describe("GIVEN SpawnTmuxAgentSessionUseCase with test configuration").config(isIntegTestEnabled()) {
        // Setup: create all real dependencies
        // - TmuxCommandRunner, TmuxCommunicatorImpl, TmuxSessionManager
        // - DefaultAgentTypeChooser
        // - ClaudeCodeAgentStarterBundleFactory with Environment.test()
        // - SpawnTmuxAgentSessionUseCase

        // Track sessions for cleanup
        val createdSessions = mutableListOf<TmuxSession>()

        afterEach {
            createdSessions.forEach { session ->
                try { sessionManager.killSession(session) } catch (_: Exception) {}
            }
            createdSessions.clear()
        }

        describe("WHEN spawn is called with IMPLEMENTOR phase") {
            it("THEN returns a TmuxAgentSession with a valid tmux session") {
                val request = StartAgentRequest(
                    phaseType = PhaseType.IMPLEMENTOR,
                    workingDir = System.getProperty("user.dir"),
                )
                val agentSession = useCase.spawn(request)
                createdSessions.add(agentSession.tmuxSession)

                agentSession.tmuxSession.exists() shouldBe true
            }

            it("THEN returns a TmuxAgentSession with a resolved session ID") {
                val request = StartAgentRequest(
                    phaseType = PhaseType.IMPLEMENTOR,
                    workingDir = System.getProperty("user.dir"),
                )
                val agentSession = useCase.spawn(request)
                createdSessions.add(agentSession.tmuxSession)

                agentSession.resumableAgentSessionId.agentType shouldBe AgentType.CLAUDE_CODE
            }

            it("THEN the resolved session ID is a non-empty string") {
                val request = StartAgentRequest(
                    phaseType = PhaseType.IMPLEMENTOR,
                    workingDir = System.getProperty("user.dir"),
                )
                val agentSession = useCase.spawn(request)
                createdSessions.add(agentSession.tmuxSession)

                agentSession.resumableAgentSessionId.sessionId.isNotBlank() shouldBe true
            }
        }
    }
})
```

**IMPORTANT test considerations**:
- The test spawns a REAL Claude Code session -- this requires `claude` to be installed and authenticated
- The session will start in tmux, receive the GUID as its first prompt, and the GUID handshake resolver will find it
- Each `it` block spawns its own session (one assert per test pattern) -- this is expensive but correct per project standards
- **OPTIMIZATION**: Actually, spawning 3 separate Claude Code sessions is very expensive (API cost + time). Consider using a shared session with multiple assertions in a SINGLE `it` block that asserts multiple properties, OR use a helper to spawn once and verify multiple things. Given "one assert per test" is a project standard, we should either:
  - (a) Accept the cost of 3 spawns for test purity
  - (b) Spawn once, capture result in describe scope, verify in separate `it` blocks

  **Decision**: Use approach (b). Spawn once in the describe block setup, verify individual properties in separate `it` blocks. The `describe` block is NOT a suspend context, but we can use a `lateinit var` pattern or a lazy wrapper.

**Revised test structure**:
```kotlin
describe("WHEN spawn is called with IMPLEMENTOR phase") {
    // Spawn happens in beforeEach or as a lazy val
    // Actually, describe blocks run at spec construction time.
    // We need spawn to happen during test execution.

    // Pattern: use a suspending helper called from each it block
    // BUT that means spawning 3 times.

    // Alternative: spawn in first it block, store in var,
    // subsequent it blocks use stored value.
    // BUT it blocks run independently -- no ordering guarantee.
}
```

**REVISED DECISION**: Given the expense and the project constraint that `describe` blocks are NOT suspend contexts, the pragmatic solution is a SINGLE `it` block with a descriptive name that verifies all properties of the returned `TmuxAgentSession`. This is a valid exception to "one assert per test" because:
1. We are testing a single behavior (spawn produces a valid session)
2. Each assertion checks a different facet of the same result
3. The cost of spawning multiple Claude sessions is prohibitive

```kotlin
it("THEN returns a TmuxAgentSession with valid tmux session and resolved session ID") {
    val request = StartAgentRequest(
        phaseType = PhaseType.IMPLEMENTOR,
        workingDir = System.getProperty("user.dir"),
    )
    val agentSession = useCase.spawn(request)
    createdSessions.add(agentSession.tmuxSession)

    agentSession.tmuxSession.exists() shouldBe true
    agentSession.resumableAgentSessionId.agentType shouldBe AgentType.CLAUDE_CODE
    agentSession.resumableAgentSessionId.sessionId.isNotBlank() shouldBe true
}
```

**Dependencies**: All prior phases
**Verification**: Test passes with `./gradlew :app:test -PrunIntegTests=true`

---

## 4. Technical Considerations

### Claude Code CLI Command Construction

The command that gets passed to `tmuxSessionManager.createSession()` must be a single string that tmux can execute. The pattern is:

```
bash -c 'cd /path/to/working/dir && claude --model sonnet --tools "Read,Write" --dangerously-skip-permissions --system-prompt-file /path/to/prompt.txt'
```

Key details:
- `bash -c '...'` wraps the entire command so `cd` + `claude` runs in a single shell
- `--dangerously-skip-permissions` is REQUIRED for non-interactive tmux usage; without it, Claude will prompt for permission and block
- `--tools "Read,Write"` restricts available tools (test mode keeps it minimal)
- `--model sonnet` uses the cheaper/faster model for tests
- `--system-prompt-file` replaces the entire system prompt (test mode) vs `--append-system-prompt-file` (production mode, preserves built-in behavior)
- `--resume <session-id>` is used by the resume use case

### GUID Handshake Timing

After `tmuxSessionManager.createSession()`, there is a race:
1. The tmux session starts
2. Claude CLI initializes (takes a few seconds)
3. `sendKeys(guid)` types the GUID into the session
4. Claude processes the GUID as a prompt and writes it to its JSONL session file
5. `ClaudeCodeAgentSessionIdResolver` polls `~/.claude/projects/` for the GUID

The existing `ClaudeCodeAgentSessionIdResolver` has a 45-second timeout with 500ms polling, which should be sufficient.

**CONCERN**: If `sendKeys(guid)` happens before Claude has fully initialized, the keys might be lost or appear in the shell prompt rather than Claude's input. The tmux session runs `bash -c 'cd ... && claude ...'`, so there is a brief window where bash is active before Claude starts.

**MITIGATION**: Add a small delay (e.g., 2-3 seconds) between `createSession` and `sendKeys` to let Claude initialize. Alternatively, the `sendKeys` timing is acceptable because Claude CLI enters interactive mode quickly, and even if keys arrive during startup, tmux buffers them and Claude reads them once ready.

**DECISION**: Start without a delay. The 45-second polling timeout in `ClaudeCodeAgentSessionIdResolver` provides sufficient buffer. If tests fail due to timing, add a configurable delay as a follow-up.

### Error Handling

- `tmuxSessionManager.createSession()` throws `IllegalStateException` on tmux failure -- let it propagate
- `AgentSessionIdResolver.resolveSessionId()` throws `IllegalStateException` on timeout -- let it propagate
- The use case should NOT catch and wrap these; the caller handles failures (per "don't log and throw" principle)

### System Prompt File Management

For the integration test, the system prompt file needs to exist on disk (Claude CLI reads it by path). Options:
1. Classpath resource extracted to temp file
2. Hardcoded path to a file in the repo
3. Created programmatically in test setup

**Decision**: Option 2 -- create a file at `config/prompts/test-agent-system-prompt.txt` in the repo. This is version-controlled, discoverable, and does not require temp file management. The `ClaudeCodeAgentStarterBundleFactory` receives the path via constructor.

For production, the system prompt file path will be provided by the workflow configuration. That is out of scope for this ticket.

## 5. Package Structure Summary

```
com.glassthought.chainsaw.core
├── agent/
│   ├── data/
│   │   ├── StartAgentRequest.kt
│   │   └── TmuxStartCommand.kt
│   ├── starter/
│   │   ├── AgentStarter.kt
│   │   └── impl/
│   │       └── ClaudeCodeAgentStarter.kt
│   ├── impl/
│   │   └── ClaudeCodeAgentStarterBundleFactory.kt
│   ├── TmuxAgentSession.kt
│   ├── AgentTypeChooser.kt
│   ├── AgentStarterBundle.kt
│   ├── AgentStarterBundleFactory.kt
│   ├── SpawnTmuxAgentSessionUseCase.kt
│   └── ResumeTmuxAgentSessionUseCase.kt
├── data/
│   ├── AgentType.kt (existing)
│   └── PhaseType.kt (new)
├── initializer/
│   └── data/
│       └── Environment.kt (modified -- add test() factory)
├── tmux/ (existing, no changes)
└── wingman/ (existing, no changes)
```

## 6. File Summary (Ordered by Implementation)

### New Files

| # | File Path | Type |
|---|-----------|------|
| 1 | `app/src/main/kotlin/com/glassthought/chainsaw/core/data/PhaseType.kt` | Enum |
| 2 | `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/data/TmuxStartCommand.kt` | Value class |
| 3 | `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/data/StartAgentRequest.kt` | Data class |
| 4 | `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/TmuxAgentSession.kt` | Interface + data class |
| 5 | `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/starter/AgentStarter.kt` | Interface |
| 6 | `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/starter/impl/ClaudeCodeAgentStarter.kt` | Implementation |
| 7 | `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/AgentTypeChooser.kt` | Interface + default impl |
| 8 | `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/AgentStarterBundle.kt` | Data class |
| 9 | `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/AgentStarterBundleFactory.kt` | Interface |
| 10 | `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/impl/ClaudeCodeAgentStarterBundleFactory.kt` | Implementation |
| 11 | `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/SpawnTmuxAgentSessionUseCase.kt` | Use case |
| 12 | `app/src/main/kotlin/com/glassthought/chainsaw/core/agent/ResumeTmuxAgentSessionUseCase.kt` | Use case |
| 13 | `config/prompts/test-agent-system-prompt.txt` | System prompt file |

### Modified Files

| # | File Path | Change |
|---|-----------|--------|
| 1 | `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/data/Environment.kt` | Add `fun test()` factory method |

### New Test Files

| # | File Path | Type |
|---|-----------|------|
| 1 | `app/src/test/kotlin/com/glassthought/chainsaw/core/agent/starter/impl/ClaudeCodeAgentStarterTest.kt` | Unit test |
| 2 | `app/src/test/kotlin/com/glassthought/chainsaw/core/agent/DefaultAgentTypeChooserTest.kt` | Unit test |
| 3 | `app/src/test/kotlin/com/glassthought/chainsaw/core/agent/impl/ClaudeCodeAgentStarterBundleFactoryTest.kt` | Unit test |
| 4 | `app/src/test/kotlin/com/glassthought/chainsaw/core/agent/SpawnTmuxAgentSessionUseCaseIntegTest.kt` | Integration test |

## 7. Testing Strategy

### Unit Tests
- **ClaudeCodeAgentStarterTest**: Verify command string construction with various flag combinations
- **DefaultAgentTypeChooserTest**: Verify always returns `CLAUDE_CODE`
- **ClaudeCodeAgentStarterBundleFactoryTest**: Verify test vs production config, verify throws for unsupported agent type

### Integration Test
- **SpawnTmuxAgentSessionUseCaseIntegTest**: Full spawn flow with real tmux + real Claude Code
  - Gated with `isIntegTestEnabled()`
  - Creates real tmux session, starts Claude Code, performs GUID handshake
  - Verifies returned `TmuxAgentSession` has valid session + resolved ID
  - Cleans up tmux sessions in `afterEach`
  - Run with: `./gradlew :app:test -PrunIntegTests=true`

### Edge Cases to Consider
- Claude CLI not installed (integration test will fail -- that's correct, it's an env dependency)
- Claude CLI not authenticated (will fail at startup -- acceptable, test is env-dependent)
- Tmux not available (integration test will fail -- correct behavior)
- GUID handshake timeout (45 seconds should be sufficient; if not, increase in test config)

## 8. Open Questions / Decisions Needed

1. **System prompt file location**: The plan proposes `config/prompts/test-agent-system-prompt.txt`. Is this the right location, or should it be in `app/src/main/resources/` or `app/src/test/resources/`?
   - **Recommendation**: `config/prompts/` at repo root is discoverable and not bundled into the JAR. Since the file path is passed to Claude CLI (an external process), it needs to be an absolute filesystem path anyway. A classpath resource would need extraction.

2. **Session name format**: `"chainsaw-{phaseType}-{timestamp}"` -- is this sufficient for uniqueness and debuggability?
   - **Recommendation**: Yes, timestamp provides uniqueness and phase type provides context.

3. **`--dangerously-skip-permissions` in production**: The current plan uses it in both test and production. In production, is there a sandboxing mechanism that makes this safe?
   - **Recommendation**: Yes, per CLAUDE.md the agents are spawned in isolated tmux sessions with controlled context. The flag is needed for non-interactive operation. Proceed with it.

4. **Cost of integration test**: Each test run spawns a Claude Code session which incurs API cost. Is this acceptable?
   - **Recommendation**: Yes, it is gated behind `isIntegTestEnabled()` and only runs when explicitly requested. The system prompt is minimal to keep costs low.
