# Testing Infrastructure Exploration

## Overview
This document captures the key testing infrastructure, patterns, and related architectural components for the TICKET_SHEPHERD project.

---

## 1. FakeAgentFacade

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-4/app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacade.kt`

**Package**: `com.glassthought.shepherd.core.agent.facade`

**Purpose**: Programmable fake for unit testing the orchestration layer. Each facade method delegates to a user-supplied handler lambda. Handlers default to throwing `IllegalStateException` so missing setup is caught immediately (fail-hard pattern). Only `killSession` defaults to a no-op.

### Constructor
```kotlin
class FakeAgentFacade : AgentFacade {
    private val _spawnCalls = mutableListOf<SpawnAgentConfig>()
    private val _sendPayloadCalls = mutableListOf<SendPayloadCall>()
    private val _readContextWindowStateCalls = mutableListOf<SpawnedAgentHandle>()
    private val _killSessionCalls = mutableListOf<SpawnedAgentHandle>()
}
```

### Key Methods
```kotlin
fun onSpawn(handler: suspend (SpawnAgentConfig) -> SpawnedAgentHandle)
fun onSendPayloadAndAwaitSignal(handler: suspend (SpawnedAgentHandle, AgentPayload) -> AgentSignal)
fun onReadContextWindowState(handler: suspend (SpawnedAgentHandle) -> ContextWindowState)
fun onKillSession(handler: suspend (SpawnedAgentHandle) -> Unit)

// Recording accessors
val spawnCalls: List<SpawnAgentConfig>
val sendPayloadCalls: List<SendPayloadCall>
val readContextWindowStateCalls: List<SpawnedAgentHandle>
val killSessionCalls: List<SpawnedAgentHandle>
```

### Supporting Data Class
```kotlin
data class SendPayloadCall(
    val handle: SpawnedAgentHandle,
    val payload: AgentPayload,
)
```

---

## 2. AsgardDescribeSpec Usage Patterns

**File**: Not found in project directly (from `testImplementation("com.asgard:asgardTestTools:1.0.0")`)

**Key Usage Pattern** - from `/app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt`:

```kotlin
class PartExecutorImplTest : AsgardDescribeSpec({
    describe("GIVEN context") {
        describe("WHEN action") {
            it("THEN expected result") {
                // single assertion per it block
            }
        }
    }
})
```

### Key Characteristics
- Uses Kotest `DescribeSpec` with BDD style: GIVEN/WHEN/THEN
- **One assertion per test** (per `it` block)
- `describe` blocks for grouping; `it` blocks for individual assertions
- Constructor receives: test body lambda and optional `AsgardDescribeSpecConfig`
- `beforeEach` and `afterEach` blocks available for setup/teardown

### OutFactory Access
```kotlin
// Inherited from AsgardDescribeSpec
val outFactory: OutFactory  // Available directly in test classes
```

---

## 3. Integration Test Support

**Base Class**: `/app/src/test/kotlin/com/glassthought/shepherd/integtest/SharedContextDescribeSpec.kt`

**Anchor**: `ap.20lFzpGIVAbuIXO5tUTBg.E`

**Package**: `com.glassthought.shepherd.integtest`

```kotlin
@AnchorPoint("ap.20lFzpGIVAbuIXO5tUTBg.E")
abstract class SharedContextDescribeSpec(
    body: SharedContextDescribeSpec.() -> Unit,
    config: SharedContextSpecConfig = SharedContextSpecConfig(),
) : AsgardDescribeSpec(...)
```

### Constructor Signature
```kotlin
data class SharedContextSpecConfig(
    val asgardConfig: AsgardDescribeSpecConfig = SharedContextIntegFactory.buildDescribeSpecConfig(),
)
```

### Key Property
```kotlin
val shepherdContext: ShepherdContext = SharedContextIntegFactory.shepherdContext
```

### Usage Pattern
```kotlin
@OptIn(ExperimentalKotest::class)
class MyIntegTest : SharedContextDescribeSpec({
    describe("GIVEN my use case").config(isIntegTestEnabled()) {
        val sessionManager = shepherdContext.infra.tmux.sessionManager
        // test body
    }
})
```

### Integration Test Enablement
**File**: `/app/src/test/kotlin/com/glassthought/bucket/integTestSupport.kt`

```kotlin
fun isIntegTestEnabled(): Boolean =
    System.getProperty("runIntegTests") == "true" || TestEnvUtil.isRunningInIntelliJ
```

Entire `describe` blocks are gated with `.config(isIntegTestEnabled())`.

---

## 4. IterationConfig Structure

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/state/IterationConfig.kt`

**Package**: `com.glassthought.shepherd.core.state`

```kotlin
data class IterationConfig(
    val max: Int,
    val current: Int = 0,  // incremented by executor on each NEEDS_ITERATION
)
```

- Used for reviewer sub-parts only
- `current` starts at 0, incremented by executor each iteration
- Validation: `current < max` before incrementing (throws on overflow)

---

## 5. TmuxSessionManager and Session Cleanup

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxSessionManager.kt`

**Package**: `com.glassthought.shepherd.core.agent.tmux`

### Constructor
```kotlin
class TmuxSessionManager(
    outFactory: OutFactory,
    private val commandRunner: TmuxCommandRunner,
    private val communicator: TmuxCommunicator,
) : SessionExistenceChecker
```

### Key Methods
```kotlin
suspend fun createSession(sessionName: String, startCommand: TmuxStartCommand): TmuxSession
suspend fun killSession(session: TmuxSession)
override suspend fun exists(sessionName: TmuxSessionName): Boolean
```

### TmuxSession
**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxSession.kt`

**Package**: `com.glassthought.shepherd.core.agent.tmux`

**Anchor**: `ap.7sZveqPcid5z1ntmLs27UqN6.E`

```kotlin
class TmuxSession(
    val name: TmuxSessionName,
    val paneTarget: String,  // e.g., "shepherd_main_impl:0.0"
    private val communicator: TmuxCommunicator,
    private val existsChecker: SessionExistenceChecker,
)
```

### Methods
```kotlin
suspend fun sendKeys(text: String)
suspend fun sendRawKeys(keys: String)  // e.g., "C-c", "Escape"
suspend fun exists(): Boolean
```

### Session Registry Cleanup
**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionsState.kt`

**Anchor**: `ap.7V6upjt21tOoCFXA7nqNh.E`

```kotlin
class SessionsState(
    private val map: MutableSynchronizedMap<HandshakeGuid, SessionEntry> = MutableSynchronizedMap()
)

suspend fun removeAllForPart(partName: String): List<SessionEntry> {
    return map.removeAll { _, entry -> entry.partName == partName }
}
```

---

## 6. Plan Flow JSON Processing

### PlanFlowConverter

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/state/PlanFlowConverter.kt`

**Package**: `com.glassthought.shepherd.core.state`

**Anchor**: `ap.bV7kMn3pQ9wRxYz2LfJ8s.E`

```kotlin
fun interface PlanFlowConverter {
    suspend fun convertAndAppend(currentState: CurrentState): List<Part>
}

class PlanFlowConverterImpl(
    private val aiOutputStructure: AiOutputStructure,
    private val currentStatePersistence: CurrentStatePersistence,
    outFactory: OutFactory,
) : PlanFlowConverter
```

### Processing Steps
1. Read and deserialize `plan_flow.json`
2. Validate all parts have `phase=EXECUTION`
3. Validate at least one part exists
4. Initialize runtime fields (`status=NOT_STARTED`, `iteration.current=0`)
5. Append to in-memory `CurrentState`
6. Ensure directory structure for new parts
7. Flush to disk via `CurrentStatePersistence.flush()`
8. Delete `plan_flow.json`
9. Return list of initialized execution parts

### PlanFlowConverterTest

**File**: `/app/src/test/kotlin/com/glassthought/shepherd/core/state/PlanFlowConverterTest.kt`

**Package**: `com.glassthought.shepherd.core.state`

**Test Infrastructure**:
```kotlin
data class PlanFlowTestContext(
    val tempDir: Path,
    val aiOutputStructure: AiOutputStructure,
    val persistence: CurrentStatePersistence,
    val converter: PlanFlowConverterImpl,
)

class PlanFlowConverterTest : AsgardDescribeSpec({
    fun createTestContext(): PlanFlowTestContext { ... }
    // tests
})
```

**Test Coverage**:
- Valid `plan_flow.json` with single and multiple parts
- Runtime field initialization and overwriting
- Error cases: missing file, empty parts, non-execution phases, malformed JSON, mixed phases
- Directory structure creation

---

## 7. PlanConversionException

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/state/PlanConversionException.kt`

**Package**: `com.glassthought.shepherd.core.state`

```kotlin
class PlanConversionException(
    message: String,
    cause: Throwable? = null,
) : AsgardBaseException(message, cause)
```

**Thrown when**:
- `plan_flow.json` is missing or malformed
- Parts array is empty
- Non-execution phase parts are present
- Parse errors occur

**Used by**: `DetailedPlanningUseCase` to inject context for planner on retry attempts.

---

## 8. Core UseCase Test Example

**File**: `/app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt`

**Package**: `com.glassthought.shepherd.core.executor`

### Test Pattern
```kotlin
class PartExecutorImplTest : AsgardDescribeSpec({
    // Helper functions for building test objects
    fun buildHandle(guidSuffix: String = "test-guid", sessionId: String = "session-1"): SpawnedAgentHandle { ... }
    fun buildDoerConfig(publicMdPath: Path): SubPartConfig { ... }
    fun buildReviewerConfig(publicMdPath: Path, feedbackDir: Path? = null): SubPartConfig { ... }
    fun buildExecutor(...): PartExecutorImpl { ... }
    
    describe("GIVEN doer-only executor") {
        describe("WHEN doer signals Done(COMPLETED)") {
            it("THEN result is PartResult.Completed") { ... }
        }
    }
})
```

### Key Test Setup Patterns
1. **Facade Configuration**: Use `onSpawn`, `onSendPayloadAndAwaitSignal`, `onReadContextWindowState` to program behavior
2. **Signal Queuing**: Use `ArrayDeque` to return different signals for successive calls
3. **Recording Verification**: Access `facade.spawnCalls`, `facade.sendPayloadCalls`, etc.
4. **SubPartConfig Building**: Provides all static configuration needed for spawn and instruction assembly

---

## 9. Agent Roles Directory Structure

**Test Resources Directory**: `/app/src/test/resources/com/glassthought/shepherd/core/context/fixtures/roles/`

**Role Files**:
- `IMPLEMENTOR.md` - "Implements features based on detailed plans" (Full-stack implementation agent)
- `PLANNER.md` - "Creates execution plans from tickets" (Analyzes tickets and creates structured plans)
- `PLAN_REVIEWER.md` - "Reviews execution plans for completeness" (Validates plan structure and completeness)
- `REVIEWER.md` - "Reviews code for correctness and style" (Checks for correctness, style, and test coverage)

### RoleDefinition Data Class

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/rolecatalog/RoleDefinition.kt`

**Package**: `com.glassthought.shepherd.core.agent.rolecatalog`

```kotlin
data class RoleDefinition(
    val name: String,                    // derived from filename without extension
    val description: String,             // from frontmatter `description` field (required)
    val descriptionLong: String?,        // from frontmatter `description_long` field (optional)
    val filePath: Path,                  // absolute path to source .md file
)
```

---

## 10. Health Monitoring UseCase Pattern

**File**: `/app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToConvergeUseCaseImplTest.kt`

**Package**: `com.glassthought.shepherd.usecase.healthmonitoring`

### Test Pattern
```kotlin
class FailedToConvergeUseCaseImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = { ... }
)
```

### Test Fakes
```kotlin
internal class FakeUserInputReader(private val response: String?) : UserInputReader {
    override suspend fun readLine(): String? = response
}
```

### Test Coverage
- User input handling (yes/no/empty/null)
- Prompt message validation
- Custom iteration increment configuration
- Case-insensitive input ("y", "Y", "N")
- Whitespace trimming

---

## 11. Rejection Negotiation UseCase Test

**File**: `/app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCaseImplTest.kt`

**Package**: `com.glassthought.shepherd.usecase.rejectionnegotiation`

### Test Infrastructure
```kotlin
// File reader fake that returns different content on successive reads
fun buildFileReader(first: String, second: String? = null): FeedbackFileReader { ... }

// ReInstructAndAwait fake with per-handle dispatch
class FakeReInstructAndAwait : ReInstructAndAwait {
    private val handlers = mutableMapOf<HandshakeGuid, suspend (String) -> ReInstructOutcome>()
    val calls = mutableListOf<Pair<SpawnedAgentHandle, String>>()
    
    fun onHandle(handle: SpawnedAgentHandle, handler: suspend (String) -> ReInstructOutcome)
    override suspend fun execute(handle: SpawnedAgentHandle, message: String): ReInstructOutcome
}
```

### Test Case Examples
- Rejection → reviewer accepts → Accepted
- Rejection → reviewer insists → doer addresses → AddressedAfterInsistence
- Interactive iteration scenarios with multiple agents

---

## 12. Core Data Structures

### AgentSignal

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentSignal.kt`

**Anchor**: `ap.uPdI6LlYO56c1kB5W0dpE.E`

```kotlin
sealed class AgentSignal {
    data class Done(val result: DoneResult) : AgentSignal()
    data class FailWorkflow(val reason: String) : AgentSignal()
    data class Crashed(val details: String) : AgentSignal()
    data object SelfCompacted : AgentSignal()
}

enum class DoneResult {
    COMPLETED,        // Doer finished this round's work
    PASS,             // Reviewer approves
    NEEDS_ITERATION,  // Reviewer requests changes
}
```

### CurrentState

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/state/CurrentState.kt`

**Anchor**: `ap.hcNCxgMidaquKohuHeVEU.E` (mutations), `ap.7mqCiP5Cr9i25k8NENYlR.E` (derived queries)

```kotlin
data class CurrentState(
    val parts: MutableList<Part>,
) {
    fun updateSubPartStatus(partName: String, subPartName: String, newStatus: SubPartStatus)
    fun incrementIteration(partName: String, subPartName: String)
    fun addSessionRecord(partName: String, subPartName: String, record: SessionRecord)
    fun appendExecutionParts(newParts: List<Part>)
    
    fun isPartCompleted(partName: String): Boolean
    fun isPartFailed(partName: String): Boolean
    fun findResumePoint(): Part?
}
```

### Part

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/state/Part.kt`

```kotlin
data class Part(
    val name: String,
    val phase: Phase,  // Phase.PLANNING or Phase.EXECUTION
    val description: String,
    val subParts: List<SubPart>,
)
```

### SessionEntry

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/session/SessionEntry.kt`

**Anchor**: `ap.igClEuLMC0bn7mDrK41jQ.E`

```kotlin
class SessionEntry(
    val tmuxAgentSession: TmuxAgentSession,
    val partName: String,
    val subPartName: String,
    val subPartIndex: Int,
    val signalDeferred: CompletableDeferred<AgentSignal>,
    val lastActivityTimestamp: AtomicReference<Instant>,
    val pendingPayloadAck: AtomicReference<PayloadId?> = AtomicReference(null),
    val questionQueue: ConcurrentLinkedQueue<UserQuestionContext>,
) {
    val isQAPending: Boolean get() = questionQueue.isNotEmpty()
    val role: SubPartRole get() = SubPartRole.fromIndex(subPartIndex)
}
```

### SessionRecord

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/state/SessionRecord.kt`

```kotlin
data class SessionRecord(
    val handshakeGuid: String,
    val agentSession: AgentSessionInfo,
    val agentType: String,
    val model: String,
    val timestamp: String,  // ISO-8601
)
```

---

## 13. SubPartConfig

**File**: `/app/src/main/kotlin/com/glassthought/shepherd/core/executor/SubPartConfig.kt`

**Package**: `com.glassthought.shepherd.core.executor`

```kotlin
data class SubPartConfig(
    // Identity
    val partName: String,
    val subPartName: String,
    val subPartIndex: Int,
    val subPartRole: SubPartRole,
    
    // Agent spawn
    val agentType: AgentType,
    val model: String,
    val systemPromptPath: Path,
    val bootstrapMessage: String,
    
    // Instruction assembly
    val roleDefinition: RoleDefinition,
    val ticketContent: String,
    val outputDir: Path,
    val publicMdOutputPath: Path,
    val privateMdPath: Path?,
    val executionContext: ExecutionContext,
    
    // Reviewer-specific
    val doerPublicMdPath: Path? = null,
    val feedbackDir: Path? = null,
) {
    fun toSpawnAgentConfig(): SpawnAgentConfig = ...
}
```

---

## 14. Testing Standards from CLAUDE.md

### Key Principles
1. **One Assert Per Test**: Each `it` block contains one logical assertion
2. **Fail Hard, Never Mask**: Tests must fail explicitly; no silent fallbacks
3. **BDD Style**: GIVEN/WHEN/THEN structure using `describe`/`it` blocks
4. **Structured Logging**: Use `Out` with `Val` and `ValType`, never embed values in message strings
5. **No Manual Synchronization**: Use proper await mechanisms, never `delay`

### Test Dependencies
```kotlin
testImplementation("com.asgard:asgardTestTools:1.0.0")  // AsgardDescribeSpec
testImplementation(libs.kotest.assertions.core)         // io.kotest:kotest-assertions-core
testImplementation(libs.kotest.runner.junit5)           // io.kotest:kotest-runner-junit5
```

### Running Tests
```bash
./test.sh                                    # Runs :app:test with self-healing for Asgard libs
./gradlew :app:test -PrunIntegTests=true   # Runs with integration tests enabled
./gradlew :app:detektBaseline               # Regenerate Detekt baseline after fixes
```

---

## Summary

The testing infrastructure is well-structured around:
1. **FakeAgentFacade**: Programmable orchestration test double with fail-hard semantics
2. **AsgardDescribeSpec**: BDD-style test framework with one assertion per test
3. **SharedContextDescribeSpec**: Integration test base for tests requiring ShepherdContext
4. **Test Fakes**: Pattern-based fakes (FakeReInstructAndAwait, FakeUserInputReader, etc.)
5. **Data-Driven Tests**: Use of `describe`/`it` nesting for self-documenting test cases
6. **Safety**: Validation, exception throwing, and explicit error handling throughout
