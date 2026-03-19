# Implementation: SetupPlanUseCase & StraightforwardPlanUseCase

## What was done

Implemented two use cases following the established `DetailedPlanningUseCase` pattern (fun interface + Impl class):

### StraightforwardPlanUseCase (ref.ap.6iySKY6iakspLNi3WenRO.E)
- Returns static `List<Part>` from `WorkflowDefinition.parts!!`
- Simple, no planning loop needed

### SetupPlanUseCase (ref.ap.VLjh11HdzC8ZOhNCDOr2g.E)
- Routes based on `WorkflowDefinition`:
  - `isStraightforward` -> `StraightforwardPlanUseCase.execute()`
  - `isWithPlanning` -> `DetailedPlanningUseCase.execute()`
  - else -> `IllegalStateException` (unreachable due to init validation, safety net)

## Files created

| File | Description |
|------|-------------|
| `app/src/main/kotlin/.../usecase/planning/StraightforwardPlanUseCase.kt` | Fun interface + impl returning static parts |
| `app/src/main/kotlin/.../usecase/planning/SetupPlanUseCase.kt` | Fun interface + routing impl |
| `app/src/test/kotlin/.../usecase/planning/StraightforwardPlanUseCaseImplTest.kt` | Unit tests (4 test cases) |
| `app/src/test/kotlin/.../usecase/planning/SetupPlanUseCaseImplTest.kt` | Unit tests (8 test cases) |

## Tests
- All existing tests pass (`./gradlew :app:test` BUILD SUCCESSFUL)
- New tests cover: straightforward routing, with-planning routing, correct delegation, correct results

## Decisions
- Followed exact same pattern as `DetailedPlanningUseCase` (fun interface + Impl class, constructor injection, OutFactory logging)
- Used fake implementations in tests (FakeStraightforwardPlanUseCase, FakeDetailedPlanningUseCase) rather than mocking
- The `else` branch in SetupPlanUseCaseImpl throws IllegalStateException with explanatory message (WHY comment explains it is unreachable)
