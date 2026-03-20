# Review Feedback Implementation — FinalCommitUseCaseImpl

## Changes Made

### 1. Removed unused import (FinalCommitUseCaseImpl.kt)
Removed `java.nio.file.Path` import from `app/src/main/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCaseImpl.kt` — it was not used anywhere in the class.

### 2. Created `ProcessRunnerFactory` fun interface
- Added `fun interface ProcessRunnerFactory` in `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt` alongside other factory interfaces (`SetupPlanUseCaseFactory`, `AllSessionsKillerFactory`, `FinalCommitUseCaseFactory`).
- Updated `TicketShepherdCreatorImpl` constructor parameter from raw lambda `(OutFactory) -> ProcessRunner` to `ProcessRunnerFactory`.
- Updated call site from `processRunnerFactory(outFactory)` to `processRunnerFactory.create(outFactory)` for consistency with other factories.
- **Also updated `ContextInitializerImpl`** (same pattern) to use `ProcessRunnerFactory` for consistency across the codebase.
- Updated `ContextInitializerTest` to use `ProcessRunnerFactory { ... }` instead of raw lambda.

### 3. Deferred: DRY violation (stageAll/hasStagedChanges/commit duplication)
Created follow-up ticket: `nid_ao50d028h0kp9vypejprl6iq7_E` — "Extract shared git staging/commit helper to DRY up FinalCommitUseCaseImpl and CommitPerSubPart". Priority 3 (low). The duplication is contained to 2 places and the code is correct, so this is a reasonable follow-up.

## Files Modified
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/finalcommit/FinalCommitUseCaseImpl.kt` — removed unused import
- `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt` — added `ProcessRunnerFactory` fun interface, updated parameter type
- `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt` — updated to use `ProcessRunnerFactory`
- `app/src/test/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializerTest.kt` — updated to use `ProcessRunnerFactory`

## Verification
- `./gradlew :app:test` — BUILD SUCCESSFUL, all tests pass.
