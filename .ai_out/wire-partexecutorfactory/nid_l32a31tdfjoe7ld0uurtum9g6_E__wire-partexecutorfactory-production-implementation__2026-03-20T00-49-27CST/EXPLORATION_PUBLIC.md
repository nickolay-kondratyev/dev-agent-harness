# Exploration: Wire PartExecutorFactory Production Implementation

## Task
Replace the TODO stub in `TicketShepherdCreatorImpl` (lines 135-137) with a production `PartExecutorFactory` that creates `PartExecutorImpl` instances from `Part`.

## Key Files

| Component | Path |
|-----------|------|
| PartExecutorFactory interface | `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorFactory.kt` |
| PartExecutorImpl | `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt` |
| SubPartConfig | `app/src/main/kotlin/com/glassthought/shepherd/core/executor/SubPartConfig.kt` |
| PartExecutorDeps | `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt` (lines 32-44) |
| TicketShepherdCreator | `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt` |
| Part model | `app/src/main/kotlin/com/glassthought/shepherd/core/state/Part.kt` |
| SubPart model | `app/src/main/kotlin/com/glassthought/shepherd/core/state/SubPart.kt` |
| SubPartRole | `app/src/main/kotlin/com/glassthought/shepherd/core/state/SubPartRole.kt` |
| IterationConfig | `app/src/main/kotlin/com/glassthought/shepherd/core/state/IterationConfig.kt` |
| AiOutputStructure | `app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructure.kt` |
| RoleDefinition | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/rolecatalog/RoleDefinition.kt` |
| RoleCatalogLoader | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/rolecatalog/RoleCatalogLoader.kt` |
| AgentFacade | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacade.kt` |
| AgentFacadeImpl | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacadeImpl.kt` |
| ContextForAgentProvider | `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` |
| GitCommitStrategy | `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommitStrategy.kt` |
| FailedToConvergeUseCase | `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToConvergeUseCase.kt` |
| ShepherdContext | `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/data/ShepherdContext.kt` |
| ExecutionContext | `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProvider.kt` (lines 51-56) |
| TicketData | `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/ticket/TicketData.kt` |
| Test fakes | `app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdTest.kt` |
| PartExecutorImplTest helpers | `app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt` (lines 96-134) |

## Current State

### TODO Stub (TicketShepherdCreator.kt:135-137)
```kotlin
private val partExecutorFactory: PartExecutorFactory = PartExecutorFactory {
    TODO("PartExecutorFactory not yet wired for production")
},
```

### PartExecutorImpl Constructor
```kotlin
class PartExecutorImpl(
    private val doerConfig: SubPartConfig,
    private val reviewerConfig: SubPartConfig?,
    private val deps: PartExecutorDeps,
    private val iterationConfig: IterationConfig,
) : PartExecutor
```

### PartExecutorDeps (required fields, no defaults)
- `agentFacade: AgentFacade`
- `contextForAgentProvider: ContextForAgentProvider`
- `gitCommitStrategy: GitCommitStrategy`
- `failedToConvergeUseCase: FailedToConvergeUseCase`
- `outFactory: OutFactory`

### SubPartConfig Fields
- identity: partName, subPartName, subPartIndex, subPartRole
- agent spawn: agentType (enum), model, systemPromptPath, bootstrapMessage
- instruction assembly: roleDefinition, ticketContent, outputDir, publicMdOutputPath, privateMdPath, executionContext
- reviewer-specific: doerPublicMdPath, feedbackDir

### Part → SubPartConfig Mapping
- `Part.subParts[0]` = doer (SubPartRole.DOER)
- `Part.subParts[1]` = optional reviewer (SubPartRole.REVIEWER)
- SubPart has: name, role (String), agentType (String), model (String)
- Need to map SubPart.agentType (String) → AgentType enum
- Need to resolve SubPart.role (String) → RoleDefinition via RoleCatalogLoader
- Path resolution via AiOutputStructure (execution vs planning Phase)

### AiOutputStructure Key Methods
- `executionPublicMd(partName, subPartName)` → public MD path
- `executionPrivateMd(partName, subPartName)` → private MD path
- `executionSubPartDir(partName, subPartName)` → output dir (comm/out)
- `executionCommOutDir(partName, subPartName)` → comm out dir
- `feedbackDir(partName)` → feedback dir for part
- Planning equivalents: `planningPublicMd`, `planningPrivateMd`, etc.

## Design Considerations

### Key Challenge: Ticket-Scoped Dependencies
`PartExecutorFactory` is a constructor param of `TicketShepherdCreatorImpl`, but needs ticket-scoped deps (AgentFacade, ContextForAgentProvider, etc.) only available inside `wireTicketShepherd()`.

### Existing Pattern: Factory-of-Factory
`SetupPlanUseCaseFactory` and `AllSessionsKillerFactory` follow the same pattern — constructor param is a factory that takes ticket-scoped inputs and produces the final object.

### Recommended Approach
1. Replace `PartExecutorFactory` constructor param with a `PartExecutorFactoryCreator` fun interface
2. `PartExecutorFactoryCreator.create(deps)` returns a `PartExecutorFactory`
3. Inside `wireTicketShepherd()`, call the creator to get the factory
4. Create a `SubPartConfigBuilder` class that maps `Part/SubPart` → `SubPartConfig` using `AiOutputStructure` and role definitions

### AgentFacadeImpl Wiring
The spec says `AgentFacadeImpl` should be constructed in `TicketShepherdCreator`. It requires:
- `SessionsState` (new instance)
- `AgentTypeAdapter` (from `shepherdContext.infra.claudeCode.agentTypeAdapter`)
- `TmuxSessionCreator` (from tmux infra)
- `SingleSessionKiller` (from tmux infra)
- `ContextWindowStateReader` (needs impl)
- `Clock` (from constructor param)
- `HarnessTimeoutConfig` (from `shepherdContext.timeoutConfig`)
- `AckedPayloadSender` (needs impl)
- `AgentUnresponsiveUseCase` (needs impl)
- `QaDrainer` (needs impl)
- `OutFactory` (from `shepherdContext.infra.outFactory`)

**Note**: Full AgentFacadeImpl wiring is complex and may need to be a separate TODO if some deps aren't available yet. The factory itself can accept AgentFacade as a parameter.

### RoleCatalog Loading
Roles loaded via `RoleCatalogLoader.load(dir)` returning `List<RoleDefinition>`. Need the roles dir path (likely from config or ShepherdContext).

### Bootstrap Message
No existing builder/constant found — tests use string literals like "bootstrap". Production may need a real bootstrap message format per the handshake protocol.
