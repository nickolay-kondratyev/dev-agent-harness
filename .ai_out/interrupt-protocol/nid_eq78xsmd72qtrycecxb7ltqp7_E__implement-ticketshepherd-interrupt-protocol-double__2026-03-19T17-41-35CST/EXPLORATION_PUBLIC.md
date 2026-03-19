# Exploration: Interrupt Protocol (Double Ctrl+C)

## Spec
- `doc/core/TicketShepherd.md` lines 102-121 — the interrupt protocol section

## Key Dependencies (all exist)
| Component | File | Purpose |
|-----------|------|---------|
| Clock | `app/src/main/kotlin/com/glassthought/shepherd/core/time/Clock.kt` | 2-second window timing |
| TestClock | `app/src/test/kotlin/com/glassthought/shepherd/core/time/TestClock.kt` | Deterministic time in tests |
| AllSessionsKiller | `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AllSessionsKiller.kt` | Kill all TMUX sessions |
| CurrentState | `app/src/main/kotlin/com/glassthought/shepherd/core/state/CurrentState.kt` | In-memory state with `updateSubPartStatus()` |
| CurrentStatePersistence | `app/src/main/kotlin/com/glassthought/shepherd/core/state/CurrentStatePersistence.kt` | Flush state to JSON |
| SubPartStatus | `app/src/main/kotlin/com/glassthought/shepherd/core/state/SubPartStatus.kt` | Has FAILED status |
| SubPartStateTransition | `app/src/main/kotlin/com/glassthought/shepherd/core/state/SubPartStateTransition.kt` | Valid: IN_PROGRESS→FAILED |
| ProcessExiter | `app/src/main/kotlin/com/glassthought/shepherd/core/infra/ProcessExiter.kt` | Exit with code |
| ConsoleOutput | `app/src/main/kotlin/com/glassthought/shepherd/core/infra/ConsoleOutput.kt` | Print red message |

## Key Design Decisions
- Use `AllSessionsKiller` (not per-session kill) — same pattern as `FailedToExecutePlanUseCaseImpl`
- Signal handlers run on special thread, `killAllSessions()` is suspend → `runBlocking` acceptable at shutdown
- Only mark `IN_PROGRESS` sub-parts as FAILED (NOT_STARTED/COMPLETED/FAILED are left untouched)
- `Part` has `.subParts` list, `SubPart` has `.status` field

## Test Pattern
- BDD with `AsgardDescribeSpec`, `TestClock` for time control
- One assert per `it` block
- `outFactory` inherited from `AsgardDescribeSpec`
