# Implementation Private Notes

## Status: COMPLETE

All steps done. Tests pass. Detekt clean.

## Files Modified
- `gradle/libs.versions.toml` — added picocli version + library
- `app/build.gradle.kts` — added picocli implementation dependency
- `app/src/main/kotlin/com/glassthought/shepherd/cli/AppMain.kt` — full rewrite with picocli
- `app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializer.kt` — NEW
- `app/src/test/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializerTest.kt` — NEW

## Follow-up Items
- `iterationMax` from CLI is parsed but not yet threaded through to TicketShepherdCreator or PartExecutor. Will need wiring when PartExecutorFactory production implementation is built.
- Several TODOs remain in `TicketShepherdCreatorImpl` for production wiring (SetupPlanUseCaseFactory, PartExecutorFactory, FinalCommitUseCase, TicketStatusUpdater). These are expected per the task spec.
- The old `com.glassthought.shepherd.core.TicketShepherdCreator` (superseded) still exists for backward compat with existing tests.
