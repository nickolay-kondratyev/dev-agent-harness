# Planner Private Context: Workflow JSON Parser

## Key Files Reviewed

- `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/TicketParser.kt` -- primary pattern to follow (interface + companion factory, OutFactory injection, suspend, Dispatchers.IO)
- `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/TicketData.kt` -- data class pattern (simple, no annotations)
- `app/src/test/kotlin/com/glassthought/chainsaw/core/ticket/TicketParserTest.kt` -- test pattern (AsgardDescribeSpec, resourcePath helper, BDD, one assert per it)
- `app/build.gradle.kts` -- current deps: guava, asgardCore, coroutines, okhttp, org.json, snakeyaml. Jackson NOT yet present.
- `gradle/libs.versions.toml` -- version catalog has guava, kotest. No Jackson entries.
- `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md` lines 243-374 -- design doc with JSON schemas

## Decisions Made During Planning

1. **Jackson dependency version**: 2.17.2 -- latest stable 2.17.x line. Added directly to build.gradle.kts (not version catalog) to be consistent with how okhttp/snakeyaml/org.json are already declared.

2. **No Jackson annotations needed**: JSON field names match Kotlin property names exactly. KotlinModule handles data class constructors.

3. **No `@JsonIgnoreProperties(ignoreUnknown = true)`**: Prefer fail-fast on unexpected fields. This is a deliberate choice for safety.

4. **Post-deserialization validation**: Jackson's Kotlin module makes non-nullable fields required automatically. But we still need:
   - name not blank
   - mutual exclusivity (parts XOR planning fields)
   - non-empty phases lists

5. **Nullable fields approach**: `WorkflowDefinition` uses nullable fields for the two workflow type variants. Simple and matches design doc guidance of "no sealed class/enum."

6. **ObjectMapper as private val**: Created once in WorkflowParserImpl constructor. Thread-safe, avoids repeated creation overhead.

7. **`DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES`**: Ensures `IterationConfig.max` (Int, not Int?) fails if missing rather than defaulting to 0.

## Anchor Points Created

- `ap.U5oDohccLN3tugPzK9TJa.E` -- for WorkflowParser interface
- `ap.MyWV0mG6ZU8XaQOyo14l4.E` -- for WorkflowDefinition domain model

## Existing Anchor Points Referenced

- `ref.ap.mmcagXtg6ulznKYYNKlNP.E` -- design doc in the ticket (used by TicketParser already)

## Risks

- **Low risk**: Jackson Kotlin module version compatibility with Kotlin 2.2.20. Jackson 2.17.x supports Kotlin 1.8+, and Kotlin 2.x is backward compatible. Should be fine.
- **Low risk**: The `org.json` library is already a dependency. Jackson and org.json can coexist without issues -- different packages, different purposes.

## Things Explicitly Out of Scope

- Resolving `executionPhasesFrom` path (workflow engine concern)
- Role catalog validation of phase role names
- CLI integration (`--workflow` argument handling)
- `current_state.json` serialization/deserialization
- Plan.json parsing (uses same `Part` schema, but the parsing entrypoint is a separate concern)
