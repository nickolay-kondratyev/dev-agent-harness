# Implementation Review: Wire PartExecutorFactory Production Implementation

## Summary

The implementation replaces the `TODO` stub for `PartExecutorFactory` in `TicketShepherdCreatorImpl` with production wiring. It introduces a factory-of-factory pattern (`PartExecutorFactoryCreator`) consistent with existing patterns (`SetupPlanUseCaseFactory`, `AllSessionsKillerFactory`), a `SubPartConfigBuilder` for mapping `Part`/`SubPart` to `SubPartConfig`, and a `ProductionPartExecutorFactoryCreator` that constructs the full dependency graph (`AgentFacadeImpl`, `GitCommitStrategy`, etc.).

**Overall assessment**: Solid implementation. The architecture follows existing patterns well, tests are thorough and BDD-style, no tests were removed, and all tests pass (including detekt). There are two SHOULD_FIX items and a few suggestions.

---

## MUST_FIX

None.

---

## SHOULD_FIX

### S1. Hardcoded `System.getProperty("user.dir")` in `ProductionPartExecutorFactoryCreator`

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/executor/ProductionPartExecutorFactoryCreator.kt` (line 194)

```kotlin
gitDir = Path.of(System.getProperty("user.dir"), ".git"),
```

This is not injectable and makes it impossible to test `buildGitCommitStrategy` in isolation. `TicketShepherdCreatorImpl` already has a `repoRoot: Path` constructor param for this purpose. The git dir should come from the `PartExecutorFactoryContext` (which has access to `ShepherdContext`), or be a constructor parameter of `ProductionPartExecutorFactoryCreator`.

**Suggested fix**: Add `repoRoot` to `PartExecutorFactoryContext` (it is already known in `TicketShepherdCreatorImpl.setupStateAndStructure`) and use `context.repoRoot.resolve(".git")` instead.

---

### S2. `DOER_INDEX` constant duplicated across two files

**File 1**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/executor/SubPartConfigBuilder.kt` (line 133)
**File 2**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorFactoryCreator.kt` (line 48-49)

Both define `private const val DOER_INDEX = 0` (and `REVIEWER_INDEX = 1` in the creator). The canonical source for index-to-role mapping is `SubPartRole.fromIndex()`. Consider extracting shared constants to `SubPartRole.companion` (which already exists and defines the mapping) to avoid knowledge duplication.

---

## NICE_TO_HAVE

### N1. `buildFactory` and `resolveIterationConfig` as companion methods on `PartExecutorFactoryCreator`

These are static utility methods placed on the `PartExecutorFactoryCreator` companion object, but they have no intrinsic relationship to the interface itself. `buildFactory` is a factory method that constructs a `PartExecutorFactory` -- it could equally live on `PartExecutorFactory.companion` or in a standalone builder class. This is not blocking, but if the companion grows further, consider splitting.

### N2. `PartExecutorFactoryCreatorTest` could verify `priorPublicMdPaths` accumulation

The `buildFactory` tests verify that `PartExecutorImpl` is returned, but do not test the stateful accumulation of `priorPublicMdPaths` across sequential `create()` calls. A test that calls `factory.create()` twice and verifies the second part's `priorPublicMdPaths` contains the first part's public MD would increase confidence in this critical wiring logic. This is already indirectly tested through the `SubPartConfigBuilderTest` prior-paths test, but the integration of the accumulation logic in `buildFactory` itself is not directly exercised.

### N3. `DefaultUserInputReader()` is constructed inline

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/executor/ProductionPartExecutorFactoryCreator.kt` (line 83)

```kotlin
val failedToConvergeUseCase = FailedToConvergeUseCaseImpl(
    consoleOutput = consoleOutput,
    userInputReader = DefaultUserInputReader(),  // not injectable
    config = shepherdContext.timeoutConfig,
)
```

This reads from stdin and is not injectable for testing. Low priority since `ProductionPartExecutorFactoryCreator` itself is not unit-tested (it's the production wiring), but could be a constructor param for consistency with the other seams.

---

## POSITIVE

1. **Pattern consistency**: The factory-of-factory pattern mirrors `SetupPlanUseCaseFactory` and `AllSessionsKillerFactory` exactly, making the codebase predictable.

2. **Clean separation**: `SubPartConfigBuilder` is stateless and focused solely on Part/SubPart-to-SubPartConfig mapping. `ProductionPartExecutorFactoryCreator` handles all infrastructure wiring. `TicketShepherdCreatorImpl` stays focused on orchestration. Good SRP.

3. **Thorough tests**: `SubPartConfigBuilderTest` covers all 16 config fields for the doer-only case, reviewer-specific fields, planning phase paths, prior public MD propagation, null planMdPath, error cases (unknown role, invalid agent type), and case-insensitive parsing. Each `it` block has one assertion. BDD structure is clean.

4. **Good error messages**: Both `resolveRoleDefinition` and `parseAgentType` provide actionable error messages with the invalid value and the list of valid alternatives.

5. **No tests removed**: The existing `TicketShepherdCreatorTest` was updated mechanically (wrapping `PartExecutorFactory` in `PartExecutorFactoryCreator`) without removing any behavior-capturing tests.

6. **Constructor injectability**: `ProductionPartExecutorFactoryCreator` exposes seams for `clock`, `consoleOutput`, `processExiter`, `roleCatalogLoader`, `processRunnerProvider`, and `envProvider` -- making it possible to test components in isolation without real environment dependencies.

7. **Phase-aware path resolution**: The `when(part.phase)` pattern in `SubPartConfigBuilder` with exhaustive matching on the sealed `Phase` enum ensures the compiler will catch any new phases added in the future.
