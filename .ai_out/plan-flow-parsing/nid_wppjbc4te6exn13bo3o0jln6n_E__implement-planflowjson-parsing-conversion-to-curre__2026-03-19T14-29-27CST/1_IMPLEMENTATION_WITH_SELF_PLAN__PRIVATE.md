# Private Notes: PlanFlowConverter Implementation

## Status: COMPLETED

## Implementation Notes

- `AsgardBaseException` is NOT in the asgardCore jar (jar appears nearly empty). Used `RuntimeException` as base for `PlanConversionException`. Follow-up: migrate when `AsgardBaseException` becomes available.
- Detekt caught `SwallowedException` on first pass — fixed by passing cause to `PlanConversionException` constructor.
- Test structure: each `it` block creates its own `PlanFlowTestContext` with a fresh temp directory. This avoids shared mutable state between tests at the cost of some duplication, which is acceptable in test code.
- `PlanFlowTestContext` data class is declared outside the spec class (top-level in file) to avoid Kotest lambda scoping issues.

## Files

- `app/src/main/kotlin/com/glassthought/shepherd/core/state/PlanFlowConverter.kt` — ap.bV7kMn3pQ9wRxYz2LfJ8s.E
- `app/src/main/kotlin/com/glassthought/shepherd/core/state/PlanConversionException.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/state/PlanFlowConverterTest.kt`
