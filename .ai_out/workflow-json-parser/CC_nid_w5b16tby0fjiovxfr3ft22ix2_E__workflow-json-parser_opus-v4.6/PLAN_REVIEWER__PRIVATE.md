# Plan Reviewer Private Notes

## Review Approach

1. Read the detailed plan, exploration notes, existing TicketParser source, TicketParserTest, TicketData, build.gradle.kts, and the design doc (lines 243-390).
2. Verified field names in JSON match Kotlin property names (camelCase alignment confirmed).
3. Checked for existing Jackson usage in the codebase -- none found. This is a fresh introduction.
4. Confirmed `org.json` is used elsewhere (GLMHighestTierApi) but for a different purpose (imperative JSON construction vs. declarative deserialization).
5. Confirmed TicketParser does NOT do explicit file-existence checks -- it lets IO exceptions propagate.
6. Verified the test pattern in TicketParserTest matches what the plan proposes.

## Key Decisions Validated

- **No sealed class/enum for workflow type**: Design doc explicitly says nullable fields differentiate. Plan follows this.
- **Jackson over org.json for deserialization**: Correct choice -- org.json has no data class mapping capability.
- **No `@JsonIgnoreProperties(ignoreUnknown = true)`**: Plan chooses strict mode. Correct for config files where unexpected fields likely indicate errors.
- **All data classes in one file**: Acceptable since they form a cohesive domain unit and are small.
- **Interface + companion factory**: Matches TicketParser exactly.

## Potential Implementation Pitfalls (for implementor awareness)

1. **Jackson Kotlin Module registration**: Must use `KotlinModule.Builder().build()`, NOT the deprecated `KotlinModule()` constructor. The plan gets this right.
2. **Non-nullable fields in data classes**: Jackson Kotlin module will throw `MissingKotlinParameterException` for missing non-nullable fields. This is desirable behavior.
3. **Post-deserialization validation order**: Validate `name.isNotBlank()` before the mutual exclusivity check. If `name` is missing entirely, Jackson will throw first (since it is non-nullable). The `isNotBlank()` check catches the edge case of `"name": ""`.
4. **Thread safety**: `ObjectMapper` is thread-safe after configuration. Storing as `private val` is correct.

## Assessment Summary

The plan is solid. No architectural issues, no over-engineering, no missing critical pieces. The gap (missing "both present" test case) is truly minor. PLAN_ITERATION can be skipped. The implementor should proceed.
