# FailedToConvergeUseCase Implementation

## What was done

Implemented `FailedToConvergeUseCase` per the spec at `doc/use-case/HealthMonitoring.md` (lines 216-234).

### Changes

1. **Added `failedToConvergeIterationIncrement`** to `HarnessTimeoutConfig` with default value of 2, in a new section with spec reference comment.

2. **Created `UserInputReader` interface** (`fun interface`) with `DefaultUserInputReader` implementation that delegates to `readlnOrNull()`. Follows the same pattern as `ConsoleOutput`/`DefaultConsoleOutput`.

3. **Created `FailedToConvergeUseCase` interface + `FailedToConvergeUseCaseImpl`**:
   - Displays red prompt with iteration counts and configurable increment
   - Reads operator input via `UserInputReader`
   - Returns `true` only for "y"/"Y", `false` for everything else (including null/empty/"N")

4. **Created comprehensive unit tests** with 7 test cases following BDD GIVEN/WHEN/THEN style, one assert per `it` block.

## Files

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/data/HarnessTimeoutConfig.kt` | Added `failedToConvergeIterationIncrement: Int = 2` |
| `app/src/main/kotlin/com/glassthought/shepherd/core/infra/UserInputReader.kt` | New `fun interface` + `DefaultUserInputReader` |
| `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToConvergeUseCase.kt` | New `fun interface` + `FailedToConvergeUseCaseImpl` |
| `app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/FailedToConvergeUseCaseImplTest.kt` | Unit tests |

## Tests

All tests pass (`BUILD SUCCESSFUL`). Test cases cover: "y", "Y", "N", empty string, null input, prompt content verification, and custom increment configuration.
