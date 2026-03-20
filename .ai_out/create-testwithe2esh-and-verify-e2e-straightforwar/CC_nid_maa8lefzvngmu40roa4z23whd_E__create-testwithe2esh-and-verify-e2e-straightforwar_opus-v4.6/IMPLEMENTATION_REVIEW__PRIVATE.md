# Review Private Notes

## Methodology

1. Read context files (EXPLORATION_PUBLIC.md, IMPLEMENTATION_PUBLIC.md)
2. Read full diff (b44b74f8..HEAD, excluding .ai_out/_change_log/_tickets)
3. Read all 14 modified production source files in full
4. Read the E2E test file in full
5. Read related files: AgentFacadeImpl (spawn flow), SessionsState, CommitMessageBuilder, IterationConfig, AppMain
6. Verified no other production callers create SessionsState() independently
7. Ran `./test.sh` -- all unit tests pass (BUILD SUCCESSFUL)
8. Verified existing test files for ShepherdServer, ShepherdInitializer, PartExecutorImpl still exist and pass
9. Compared test_with_e2e.sh against test_with_integ.sh pattern

## Key Verification Points

### SessionsState Singleton Verification
- Searched all production code for `SessionsState()` -- only one construction in `ShepherdContext` default parameter
- All three consumers (ShepherdInitializer, ProductionPartExecutorFactoryCreator, ProductionPlanningPartExecutorFactory) now use `shepherdContext.sessionsState`
- Test files create their own instances (correct for isolation)

### AgentSignal.Started Flow Verification
- Traced the full spawn flow in AgentFacadeImpl:
  1. registerPlaceholderEntry creates deferred and registers in sessionsState
  2. createTmuxSession starts the agent
  3. awaitStartupOrCleanup awaits the deferred
  4. ShepherdServer.handleStarted completes the deferred with Started
  5. After await returns, registerRealEntry replaces placeholder with fresh deferred
- The flow is correct: Started only lives during spawn phase, lifecycle signals use the fresh deferred

### Iteration Math Verification
- IterationConfig defaults: current=0, max comes from workflow JSON
- With reviewer: current=0+1=1, max=4+1=5
- Budget check: after processNeedsIteration increments, checks `currentIteration >= maxIterations`
- Allowed rounds: iteration goes 1->2->3->4, then 5>=5 triggers budget exhaustion = 4 NEEDS_ITERATION rounds
- Without shift: 0->1->2->3, then 4>=4 = 4 NEEDS_ITERATION rounds -- same count, correct

### Exhaustiveness Verification
- All `when(signal)` branches in PartExecutorImpl (4 locations), SubPartStateTransition (1), ReInstructAndAwait (1) have `AgentSignal.Started -> error(...)`
- Sealed class guarantees compile-time exhaustiveness

## Items NOT Flagged (and why)

- The `advancePastOnboardingPrompts` approach is a workaround for Claude CLI behavior. It's well-documented with WHY comments and scoped to the test. Not production code.
- `preTrustWorkspace` modifies `~/.claude/.claude.json` -- flagged as IMPORTANT but not blocking since entries are harmless and this is test infrastructure.
- No new unit tests for the 5 bug fixes were added. The E2E test exercises them end-to-end. I flagged the ShepherdServer test gap as a suggestion. The other fixes (SessionsState wiring, iteration math) are wiring changes that are hard to unit test in isolation without significant test infrastructure that would be low ROI.
