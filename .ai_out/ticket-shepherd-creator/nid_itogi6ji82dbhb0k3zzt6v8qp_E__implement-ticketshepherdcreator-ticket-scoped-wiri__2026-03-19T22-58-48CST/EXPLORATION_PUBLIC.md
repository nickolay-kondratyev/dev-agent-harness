# Exploration Summary — TicketShepherdCreator

## All Dependencies Exist
Every dependency referenced by the ticket has a working implementation:

| Dependency | Location | Key API |
|---|---|---|
| WorkflowParser | `core/workflow/WorkflowParser.kt` | `suspend fun parse(workflowName, workingDirectory): WorkflowDefinition` |
| TicketParser | `core/supporting/ticket/TicketParser.kt` | `suspend fun parse(path): TicketData` |
| WorkingTreeValidator | `core/supporting/git/WorkingTreeValidator.kt` | `suspend fun validate()` |
| TryNResolver | `core/supporting/git/TryNResolver.kt` | `suspend fun resolve(ticketData): Int` |
| GitBranchManager | `core/supporting/git/GitBranchManager.kt` | `createAndCheckout(branchName)`, `getCurrentBranch()` |
| BranchNameBuilder | `core/supporting/git/BranchNameBuilder.kt` | `object`, `fun build(ticketData, tryNumber): String` |
| AiOutputStructure | `core/filestructure/AiOutputStructure.kt` | `class(repoRoot, branch)`, `fun ensureStructure(parts)` |
| CurrentStateInitializer | `core/state/CurrentStateInitializer.kt` | `fun createInitialState(workflowDefinition): CurrentState` |
| CurrentStatePersistence | `core/state/CurrentStatePersistence.kt` | `suspend fun flush(state)` |
| AgentFacadeImpl | `core/agent/facade/AgentFacadeImpl.kt` | 11 constructor params |
| SessionsState | `core/session/SessionsState.kt` | no-arg constructor (has default MutableSynchronizedMap) |
| Clock/SystemClock | `core/time/Clock.kt` | `fun now(): Instant` |
| ContextForAgentProvider | `core/context/ContextForAgentProvider.kt` | `companion.standard(outFactory, aiOutputStructure)` |
| AgentTypeAdapter | `core/agent/adapter/AgentTypeAdapter.kt` | From `ShepherdContext.infra.claudeCode.agentTypeAdapter` |
| ContextWindowStateReader | `core/agent/contextwindow/ContextWindowStateReader.kt` | `suspend fun read(agentSessionId): ContextWindowState` |
| UserQuestionHandler | `core/question/UserQuestionHandler.kt` | `suspend fun handleQuestion(context): String` |
| StdinUserQuestionHandler | `core/question/StdinUserQuestionHandler.kt` | impl of UserQuestionHandler |
| ShepherdContext | `core/initializer/data/ShepherdContext.kt` | `infra: Infra`, `nonInteractiveAgentRunner`, `timeoutConfig` |
| TicketShepherd | `core/TicketShepherd.kt` | `class(deps: TicketShepherdDeps, currentState, originatingBranch, tryNumber)` |
| InterruptHandler | `core/interrupt/InterruptHandler.kt` | `fun install()` |

## TicketShepherd Constructor
```kotlin
class TicketShepherd(
    private val deps: TicketShepherdDeps,
    private val currentState: CurrentState,
    val originatingBranch: String,
    val tryNumber: Int,
)
```

## TicketShepherdDeps
```kotlin
data class TicketShepherdDeps(
    val setupPlanUseCase: SetupPlanUseCase,
    val failedToExecutePlanUseCase: FailedToExecutePlanUseCase,
    val interruptHandler: InterruptHandler,
    val allSessionsKiller: AllSessionsKiller,
    val partExecutorFactory: PartExecutorFactory,
    val consoleOutput: ConsoleOutput,
    val processExiter: ProcessExiter,
    val finalCommitUseCase: FinalCommitUseCase,
    val ticketStatusUpdater: TicketStatusUpdater,
    val aiOutputStructure: AiOutputStructure,
    val out: Out,
    val ticketId: String,
)
```

## AgentFacadeImpl Constructor
```kotlin
class AgentFacadeImpl(
    sessionsState: SessionsState,
    agentTypeAdapter: AgentTypeAdapter,
    tmuxSessionCreator: TmuxSessionCreator,
    sessionKiller: SingleSessionKiller,
    contextWindowStateReader: ContextWindowStateReader,
    clock: Clock,
    harnessTimeoutConfig: HarnessTimeoutConfig,
    ackedPayloadSender: AckedPayloadSender,
    agentUnresponsiveUseCase: AgentUnresponsiveUseCase,
    qaDrainAndDeliverUseCase: QaDrainer,
    outFactory: OutFactory,
)
```

## Infrastructure from ShepherdContext
- `infra.outFactory` — OutFactory
- `infra.tmux.sessionManager` — TmuxSessionManager (implements TmuxSessionCreator)
- `infra.tmux.communicator` — TmuxCommunicator
- `infra.claudeCode.agentTypeAdapter` — AgentTypeAdapter
- `timeoutConfig` — HarnessTimeoutConfig

## No Existing TicketShepherdCreator Implementation
Package `com.glassthought.shepherd.core.creator` does not exist yet. This is a greenfield implementation.
