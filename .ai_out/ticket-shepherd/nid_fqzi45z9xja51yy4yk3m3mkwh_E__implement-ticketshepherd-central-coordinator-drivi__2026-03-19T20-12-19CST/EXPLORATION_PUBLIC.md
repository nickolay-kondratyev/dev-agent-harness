# Exploration: TicketShepherd Implementation

## Spec
- `doc/core/TicketShepherd.md` (ap.P3po8Obvcjw4IXsSUSU91.E)

## Key Dependencies (all exist)

| Dependency | File | Interface |
|---|---|---|
| `AgentFacade` | `app/src/main/kotlin/.../agent/facade/AgentFacade.kt` | `spawnAgent`, `sendPayloadAndAwaitSignal`, `readContextWindowState`, `killSession` |
| `SetupPlanUseCase` | `app/src/main/kotlin/.../usecase/planning/SetupPlanUseCase.kt` | `suspend fun setup(): List<Part>` |
| `PartExecutor` | `app/src/main/kotlin/.../executor/PartExecutor.kt` | `suspend fun execute(): PartResult` |
| `PartExecutorImpl` | `app/src/main/kotlin/.../executor/PartExecutorImpl.kt` | Takes `doerConfig`, `reviewerConfig?`, `deps`, `iterationConfig` |
| `PartResult` | `app/src/main/kotlin/.../state/PartResult.kt` | Sealed: `Completed`, `FailedWorkflow`, `FailedToConverge`, `AgentCrashed` |
| `FailedToExecutePlanUseCase` | `app/src/main/kotlin/.../usecase/healthmonitoring/FailedToExecutePlanUseCase.kt` | `suspend fun handleFailure(failedResult: PartResult): Nothing` |
| `InterruptHandler` | `app/src/main/kotlin/.../interrupt/InterruptHandler.kt` | `fun install()` |
| `CurrentState` | `app/src/main/kotlin/.../state/CurrentState.kt` | `parts: MutableList<Part>`, queries: `isPartCompleted`, `isPartFailed` |
| `GitCommitStrategy` | `app/src/main/kotlin/.../supporting/git/GitCommitStrategy.kt` | `suspend fun onSubPartDone(context: SubPartDoneContext)` |
| `AllSessionsKiller` | `app/src/main/kotlin/.../usecase/healthmonitoring/AllSessionsKiller.kt` | `suspend fun killAllSessions()` |
| `ConsoleOutput` | `app/src/main/kotlin/.../infra/ConsoleOutput.kt` | Currently only `printlnRed` — need to add `printlnGreen` |
| `ProcessExiter` | `app/src/main/kotlin/.../infra/ProcessExiter.kt` | `fun exit(code: Int): Nothing` |

## Key Observations

1. **`removeAllForPart`** exists on `SessionsState` but NOT on `AgentFacade`. The spec says "kill TMUX sessions for part" — we need to add a `removeAllForPart(partName: String)` method to `AgentFacade` interface.

2. **ConsoleOutput** only has `printlnRed`. Need to add `printlnGreen` for success message.

3. **TicketShepherd class does NOT exist yet**. Needs to be created.

4. **TicketShepherdCreator** is partially implemented — currently only wires InterruptHandler. Needs expansion to wire TicketShepherd.

5. **Part has subParts list** — 2 subParts means doer+reviewer, 1 subPart means doer-only.

6. **Ticket status update** — spec says update YAML frontmatter. Need a mechanism for this (could be a simple use case or utility).

7. **Final commit** — spec says `git add -A && git commit`, skip if clean. Similar to `CommitPerSubPart` logic.

## Test Patterns
- BDD with `AsgardDescribeSpec`, GIVEN/WHEN/THEN
- Fakes: `FakeAgentFacade`, `FakeConsoleOutput`, `FakeProcessExiter`, `FakeAllSessionsKiller`
- FakeProcessExiter throws `FakeProcessExitException` to capture exit code
- One assert per `it` block

## Package Location
- TicketShepherd: `app/src/main/kotlin/com/glassthought/shepherd/core/TicketShepherd.kt`
- Test: `app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdTest.kt`
