# Plan Review: Remove Composite Build, Switch to Maven Local

**Reviewed:** 2026-03-10
**Plan:** `DETAILED_PLANNING__PUBLIC.md`
**Reviewer verdict at bottom.**

---

## Executive Summary

The overall approach is sound: adding `maven-publish` to the KMP convention plugin, registering an aggregate publish task in the submodule, removing the composite build from chainsaw, and delegating publish to the submodule via exec. Two issues require attention before implementation: (1) the plan contains a wrong "Before" snippet for `buildlogic.kotlin-jvm.gradle.kts` — the shadow plugin is NOT in that file; (2) `GradleException` requires an explicit import in `.gradle.kts` files — the plan's inline note says otherwise. Both are minor fixable-inline issues. No major architectural problems found.

---

## Verification Results by Check Item

### 1. KMP maven-publish — does `id("maven-publish")` alone create publishToMavenLocal tasks?

**CONFIRMED CORRECT.**

When `id("maven-publish")` is applied to a KMP project (via the convention plugin), Gradle's Kotlin Multiplatform plugin integration automatically creates per-target publication tasks (`publishJvmPublicationToMavenLocal`, `publishKotlinMultiplatformPublicationToMavenLocal`, etc.) plus the aggregate `publishToMavenLocal`. No manual `publishing {}` block is required. The plan's explanation in File 1 is accurate.

### 2. asgardCoreShared and asgardCoreNodeJS — do they use `buildlogic.kotlin-multiplatform`?

**INDIRECT: via the js-base chain. Effect is the same.**

- `asgardCoreShared/asgardCoreShared.build.gradle.kts` applies `buildlogic.kotlin-multiplatform-with-shared-js`
- `asgardCoreNodeJS/asgardCoreNodeJS.build.gradle.kts` applies `buildlogic.kotlin-multiplatform-with-node-js`
- Both of these delegate to `buildlogic.kotlin-multiplatform-js-base`, which itself applies `id("buildlogic.kotlin-multiplatform")`

Therefore adding `id("maven-publish")` to `buildlogic.kotlin-multiplatform.gradle.kts` WILL cascade to `asgardCoreShared` and `asgardCoreNodeJS` through this chain. The plan's claim that these libraries will get `publishToMavenLocal` via the File 1 change is correct.

**Risk note preserved from plan:** Both libraries have JS targets (NodeJS + Browser for Shared, NodeJS for NodeJS). `publishToMavenLocal` will attempt to publish JS artifacts too. The plan's mitigation (fall back to `publishJvmPublicationToMavenLocal` + `publishKotlinMultiplatformPublicationToMavenLocal` if JS publish fails) is the right approach.

### 3. asgardCoreJVM — does it use `buildlogic.kotlin-jvm`?

**CONFIRMED CORRECT.**

`submodules/thorg-root/source/libraries/kotlin-mp/kotlin-jvm/asgardCoreJVM/build.gradle.kts` line 9: `id("buildlogic.kotlin-jvm")`. Adding `maven-publish` to `buildlogic.kotlin-jvm.gradle.kts` will give `asgardCoreJVM` its `publishToMavenLocal` task.

### 4. Task registration — `dependsOn(":asgardCoreShared:publishToMavenLocal")` pattern validity

**CONFIRMED VALID.**

Referencing subproject tasks by path string in `dependsOn` is standard Gradle. The existing `thorgKotlinMP.build.gradle.kts` already uses this pattern (e.g., it uses `gradle.includedBuild(...).task(...)` at line 339). The `dependsOn(":kotlin-jvm:asgardCoreJVM:publishToMavenLocal")` format for a nested project is also correct Gradle syntax.

### 5. `kotlinMpDir = file(...)` at root build.gradle.kts top level

**CONFIRMED CORRECT.**

The existing `build.gradle.kts` already uses `file("submodules/thorg-root")` at top level (outside any task) in the `idea` block. `file()` is always relative to the project directory at configuration time. The pattern `val kotlinMpDir = file("submodules/thorg-root/source/libraries/kotlin-mp")` is correct.

### 6. GradleException import — available without import in .gradle.kts?

**PLAN IS WRONG — IMPORT REQUIRED.**

The plan states in File 6: "GradleException is available in buildscript scope without an import in `.gradle.kts` files — it is part of the Gradle API automatically in scope."

This is incorrect. The actual `thorgKotlinMP.build.gradle.kts` in the submodule has:
```kotlin
import org.gradle.api.GradleException
```
at line 3, and uses `GradleException` at line 404 only because of that import.

`GradleException` is NOT auto-imported in `.gradle.kts` files. The chainsaw root `build.gradle.kts` will need:
```kotlin
import org.gradle.api.GradleException
```
at the top.

**Fix applied inline below.**

### 7. Shadow plugin + maven-publish conflict for asgardCoreJVM

**NOT APPLICABLE — plan's "Before" snippet is wrong, but conclusion is safe.**

The plan's File 2 "Before" shows:
```kotlin
plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.gradleup.shadow")   // <-- shown as existing
  id("io.gitlab.arturbosch.detekt")
}
```

The **actual** `buildlogic.kotlin-jvm.gradle.kts` does NOT have `id("com.gradleup.shadow")`. The shadow plugin is absent from the convention plugin. The "After" shown in the plan is also missing shadow, so the intended change (add `maven-publish`) remains correct — just the "Before" documentation in the plan is inaccurate. This does not affect implementation correctness.

No shadow+maven-publish conflict exists for `asgardCoreJVM`.

### 8. Verification steps — complete and correct?

**SUBSTANTIALLY COMPLETE with one gap.**

The 6 verification steps cover the critical paths. One gap: Step 1 uses `cd` twice without unsetting it — the second `cd` redundantly re-enters the same directory. Minor scripting noise, not a correctness issue.

A more meaningful gap: Step 1 does not verify that `asgardBuildConfig` was published (only lists all expected directories). The `ls ~/.m2/repository/com/asgard/` check covers it implicitly, which is sufficient.

The step order also correctly enforces the "publish first, then remove composite build" constraint, which is the critical ordering rule.

### 9. Documentation change — anchor point preservation

**CORRECTLY SPECIFIED.**

The plan explicitly requires that `ap.MKHNCkA2bpT63NAvjCnvbvsb.E` stays on the `## Environment Prerequisites` heading line, unchanged. The "After" content shows:
```markdown
## Environment Prerequisites (ap.MKHNCkA2bpT63NAvjCnvbvsb.E)
```
This is correct. The anchor point is preserved in its original position.

---

## Issues Found

### Issue 1: GradleException requires explicit import (MINOR — fix inline)

**File:** `build.gradle.kts` (File 6, chainsaw root)

**Problem:** Plan states `GradleException` needs no import. This is false. The submodule's own `thorgKotlinMP.build.gradle.kts` line 3 has `import org.gradle.api.GradleException`.

**Fix:** Add `import org.gradle.api.GradleException` to the top of the chainsaw root `build.gradle.kts` in the "After" code block for File 6. The plan's inline note claiming it doesn't need an import should be corrected.

### Issue 2: File 2 "Before" snippet is inaccurate (MINOR — documentation only)

**File:** Plan's description of `buildlogic.kotlin-jvm.gradle.kts`

**Problem:** The plan shows `id("com.gradleup.shadow")` as an existing entry in the "Before" block. The actual file does not contain `id("com.gradleup.shadow")`. The shadow plugin is absent from the convention plugin.

**Impact:** None on implementation — the plan's "After" block is correct (adds `maven-publish`, doesn't add shadow). The inaccuracy is only in the "Before" documentation.

**Fix:** Correct the "Before" snippet to match reality. Applied inline below.

---

## Inline Corrections Applied to Plan

### Correction 1: File 2 "Before" snippet

**Original plan "Before":**
```kotlin
plugins {
  // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin
  id("org.jetbrains.kotlin.jvm")
  id("com.gradleup.shadow")
  id("io.gitlab.arturbosch.detekt")
}
```

**Corrected "Before" (matches actual file):**
```kotlin
plugins {
  // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin
  id("org.jetbrains.kotlin.jvm")
  id("io.gitlab.arturbosch.detekt")
}
```

**"After" (unchanged — still correct):**
```kotlin
plugins {
  // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin
  id("org.jetbrains.kotlin.jvm")
  id("io.gitlab.arturbosch.detekt")
  id("maven-publish")
}
```

### Correction 2: File 6 — GradleException import

The plan's "After" code block for `build.gradle.kts` must include an import at the top:

```kotlin
import org.gradle.api.GradleException

plugins {
    idea
}
// ... rest of file
```

And the inline note: _"GradleException is available in buildscript scope without an import in `.gradle.kts` files — it is part of the Gradle API automatically in scope."_ is **incorrect and should be removed**. Replace with: _"GradleException requires `import org.gradle.api.GradleException` — verified against the submodule's own usage in `thorgKotlinMP.build.gradle.kts` line 3."_

---

## Strengths

- **Ordering discipline**: The explicit "publish first, then remove composite build" constraint with the mid-sequence VERIFY step prevents the most likely foot-gun.
- **JS target risk identified proactively**: The plan correctly identifies that KMP with JS targets may fail during publish and provides a concrete mitigation (use JVM + KMP metadata publications only).
- **Correctly scopes the task**: The `tasks.withType<PublishToMavenLocal> { enabled = false }` concern is addressed — understanding that root-level disable does not affect subproject tasks is correct.
- **Anchor point preservation**: Explicit and correct handling of `ap.MKHNCkA2bpT63NAvjCnvbvsb.E`.
- **exec delegation pattern**: Using `exec {}` to delegate from chainsaw root to the submodule's Gradle wrapper is clean and avoids coupling the two build systems.
- **checkAsgardInMavenLocal**: Good DX addition — gives developers a clear diagnostic path without requiring THORG_ROOT.

---

## Verdict

`VERDICT: READY`

Both issues are minor and have been identified with exact fixes. Neither is a major structural problem or wrong approach. The implementor should apply:
1. The corrected "Before" snippet for File 2 (shadow plugin was never there).
2. Add `import org.gradle.api.GradleException` to the top of the chainsaw root `build.gradle.kts`.

The plan can proceed to implementation.
