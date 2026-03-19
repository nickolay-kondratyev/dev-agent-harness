# Exploration: Clock Abstraction

## Package Structure
- `core/time/` package does NOT exist yet — this task creates it
- Existing core packages: agent/, data/, infra/, state/, context/, initializer/, supporting/, filestructure/, workflow/

## Spec Reference
- `ref.ap.whDS8M5aD2iggmIjDIgV9.E` defined in `doc/core/AgentFacade.md` lines 95–166 ("Virtual Time Strategy")
- Clock provides `fun now(): Instant` for health-aware await loop timeout comparisons
- Two timing axes: (1) coroutine delays via TestDispatcher, (2) wall-clock via Clock interface
- Injected into `AgentFacadeImpl`, NOT PartExecutor

## Patterns to Follow
- **Interface style**: Use `fun interface` or regular interface with companion factory (see `DispatcherProvider.kt`)
- **Test style**: `AsgardDescribeSpec`, nested describe GIVEN/WHEN/THEN, one assert per `it` block
- **Imports**: `com.asgard.testTools.describe_spec.AsgardDescribeSpec`, `io.kotest.matchers.*`
- **Duration**: `kotlin.time.Duration` already used in codebase (e.g., `HarnessTimeoutConfig`)

## Files to Create
1. `app/src/main/kotlin/com/glassthought/shepherd/core/time/Clock.kt` (interface + SystemClock)
2. `app/src/test/kotlin/com/glassthought/shepherd/core/time/TestClock.kt` (test double)
3. `app/src/test/kotlin/com/glassthought/shepherd/core/time/ClockTest.kt` (tests)

## No Dependencies Needed
- `java.time.Instant` and `kotlin.time.Duration` already available
