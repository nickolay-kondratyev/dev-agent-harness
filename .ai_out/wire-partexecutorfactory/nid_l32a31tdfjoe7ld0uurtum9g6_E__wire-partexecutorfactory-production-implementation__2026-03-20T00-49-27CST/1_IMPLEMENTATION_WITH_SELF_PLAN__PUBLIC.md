# Wire PartExecutorFactory Production Implementation

## Summary

Replaced the TODO stub in `TicketShepherdCreatorImpl` with a fully wired production `PartExecutorFactory`.
Introduced the factory-of-factory pattern (`PartExecutorFactoryCreator`) to handle ticket-scoped dependency
construction that can only happen inside `wireTicketShepherd()`.

## What Was Done

### New Files

1. **`app/src/main/kotlin/com/glassthought/shepherd/core/executor/SubPartConfigBuilder.kt`**
   - Maps `Part` + `SubPart` (by index) to a fully populated `SubPartConfig`.
   - Phase-aware path resolution (Planning vs Execution) via `AiOutputStructure`.
   - Resolves role definitions, parses `AgentType`, derives `SubPartRole` from index.

2. **`app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorFactoryCreator.kt`**
   - `fun interface PartExecutorFactoryCreator` with `suspend fun create(context): PartExecutorFactory`.
   - `PartExecutorFactoryContext` data class groups ticket-scoped inputs.
   - Companion object has `buildFactory()` helper and `resolveIterationConfig()`.

3. **`app/src/main/kotlin/com/glassthought/shepherd/core/executor/ProductionPartExecutorFactoryCreator.kt`**
   - Full production wiring: `AgentFacadeImpl`, `ContextForAgentProvider`, `GitCommitStrategy`,
     `FailedToConvergeUseCaseImpl`, role catalog loading, `SubPartConfigBuilder`.
   - Reads env vars `TICKET_SHEPHERD_AGENTS_DIR` and `HOST_USERNAME`.
   - Constructor-injectable seams for `clock`, `consoleOutput`, `processExiter`, `roleCatalogLoader`,
     `processRunnerProvider`, `envProvider`.

### Modified Files

4. **`app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`**
   - Constructor param changed from `partExecutorFactory: PartExecutorFactory` to
     `partExecutorFactoryCreator: PartExecutorFactoryCreator`.
   - Default: `ProductionPartExecutorFactoryCreator(clock = clock)`.
   - `wireTicketShepherd()` now `suspend` (creator.create is suspend).
   - Resolves `planMdPath` from `workflowDefinition.isWithPlanning`.

5. **`app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt`**
   - Updated to use `PartExecutorFactoryCreator { _ -> PartExecutorFactory { ... } }`.

### New Test Files

6. **`app/src/test/kotlin/com/glassthought/shepherd/core/executor/SubPartConfigBuilderTest.kt`**
   - Doer-only execution part: all 16 config fields verified.
   - Doer+reviewer part: reviewer-specific fields (doerPublicMdPath, feedbackDir).
   - Planning phase path resolution.
   - Prior public MD paths propagation.
   - Null planMdPath scenario.
   - Unknown role -> IllegalArgumentException.
   - Invalid agent type -> IllegalArgumentException.
   - Case-insensitive agent type parsing.

7. **`app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorFactoryCreatorTest.kt`**
   - `resolveIterationConfig` for doer+reviewer and doer-only.
   - `buildFactory` creates `PartExecutorImpl` for both part configurations.

## Design Decisions

1. **Factory-of-factory pattern**: `PartExecutorFactory` needs ticket-scoped deps (AgentFacade, etc.)
   only available inside `wireTicketShepherd()`. The `PartExecutorFactoryCreator` receives a context
   and constructs the factory, keeping the constructor clean and tests simple.

2. **`PartExecutorFactoryContext` data class**: Groups ticket-scoped inputs so tests can provide a
   simple lambda `PartExecutorFactoryCreator { _ -> ... }` ignoring context entirely.

3. **All production dep construction in `ProductionPartExecutorFactoryCreator`**: Keeps
   `TicketShepherdCreatorImpl` focused on orchestration, not infrastructure wiring.

4. **`buildFactory` as companion helper**: Shared between production and test code. Takes
   `PartExecutorDeps` + `SubPartConfigBuilder` and returns `PartExecutorFactory`.

## Test Results

All tests pass. Detekt static analysis passes. No regressions.
