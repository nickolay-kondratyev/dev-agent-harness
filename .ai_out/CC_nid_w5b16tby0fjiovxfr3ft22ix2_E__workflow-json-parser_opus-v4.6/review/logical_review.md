# Logical Review: Workflow JSON Parser

**Verdict**: NOT READY — one correctness bug (wrong anchor point reference) and two missing validations that will cause silent bad behavior at runtime.

---

## Issues Found

### MEDIUM — Wrong Anchor Point in `WorkflowDefinition.kt` KDoc

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/workflow/WorkflowDefinition.kt`, line 10

```kotlin
 * See design doc: ref.ap.mmcagXtg6ulznKYYNKlNP.E
```

`ap.mmcagXtg6ulznKYYNKlNP.E` is the "CLI Entry Point" section anchor point — it belongs to `TicketParser.kt`. The correct AP for the workflow definition design section is `ap.Wya4gZPW6RPpJHdtoJqZO.E` (which `WorkflowParser.kt` already references correctly).

This looks like a copy-paste error from `TicketParser.kt`. It misdirects readers of the code to the wrong design section.

**Fix**: Replace the reference in `WorkflowDefinition.kt`:
```kotlin
 * See design doc: ref.ap.Wya4gZPW6RPpJHdtoJqZO.E (Workflow Definition — Kotlin + JSON)
```

---

### MEDIUM — Empty `parts` List Passes Validation

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/workflow/WorkflowParser.kt`, lines 90–96

```kotlin
if (hasParts) {
    definition.parts!!.forEachIndexed { index, part ->
        require(part.phases.isNotEmpty()) { ... }
    }
}
```

`hasParts` is `true` when `parts` is non-null. A JSON like `{"name": "x", "parts": []}` passes the `hasParts || hasPlanning` check (parts is non-null), passes the `hasParts && hasPlanning` mutual exclusivity check, and then `forEachIndexed` on an empty list is a no-op — so validation succeeds. The result is a `WorkflowDefinition` with zero parts, which will cause a silent no-op workflow execution downstream.

By contrast, `planningPhases` correctly validates non-empty. The `parts` case is inconsistent.

**Fix**: Add `require(definition.parts!!.isNotEmpty())` before the per-part loop:
```kotlin
if (hasParts) {
    require(definition.parts!!.isNotEmpty()) {
        "parts must not be empty in: $path"
    }
    definition.parts.forEachIndexed { index, part ->
        require(part.phases.isNotEmpty()) { ... }
    }
}
```

---

### MEDIUM — `executionPhasesFrom` Accepts Blank String

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/workflow/WorkflowParser.kt`, line 107

```kotlin
require(definition.executionPhasesFrom != null) {
    "executionPhasesFrom is required when planningPhases is present in: $path"
}
```

Only null is rejected. `"executionPhasesFrom": ""` or `"executionPhasesFrom": "   "` passes validation. The field is a file path reference — a blank string will silently produce a bogus path and fail later at runtime (potentially in an obscure way, far from the root cause).

The same applies to `Phase.role` — a blank role string passes deserialization and validation and would only fail when resolving against the role catalog, rather than at parse time.

**Fix**: Validate that `executionPhasesFrom` is not blank:
```kotlin
require(definition.executionPhasesFrom!!.isNotBlank()) {
    "executionPhasesFrom must not be blank in: $path"
}
```

---

## Notable Observations

**ObjectMapper thread safety**: `objectMapper` is a private field on `WorkflowParserImpl`. `ObjectMapper` is thread-safe after configuration, so this is fine — just worth noting since it is shared across concurrent `parse()` calls.

**`FAIL_ON_UNKNOWN_PROPERTIES` is enabled by default** in Jackson 2.x ObjectMapper. Unknown JSON fields will cause a parse error, which is the correct behavior for a configuration file parser. No action needed, but it is worth being aware of — adding future fields to the JSON without updating the domain model will break parsing immediately.

**No `PlanningConfig` wrapper class**: The ticket scope mentioned `PlanningConfig` as a possible sub-type, but the key decision was "just optional fields." The flat structure on `WorkflowDefinition` is consistent with the decided approach. No issue.

**Test resources are a duplicate of production config files**: `app/src/test/resources/.../straightforward.json` and `config/workflows/straightforward.json` are byte-identical. This is intentional (tests must not depend on the live config files), but if the production files evolve and tests do not follow, the tests may lag behind. This is an acceptable tradeoff for unit testing isolation — just noting it.

**32 tests all passing**: Verified by running `./gradlew :app:test --no-configuration-cache`. Test coverage of the happy paths and error paths is solid, with BDD structure per project standards.
