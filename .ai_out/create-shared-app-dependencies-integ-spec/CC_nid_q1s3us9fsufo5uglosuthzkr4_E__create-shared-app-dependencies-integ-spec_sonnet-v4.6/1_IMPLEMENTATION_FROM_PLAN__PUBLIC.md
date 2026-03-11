# Implementation: Shared App Dependencies Integration Spec

Ticket: `nid_q1s3us9fsufo5uglosuthzkr4_E`
Date: 2026-03-11
Status: COMPLETE (post-review iteration complete)

---

## Summary

All 5 phases from the plan implemented successfully. Post-review iteration addressed all IMPORTANT issues from the code review. Build passes, unit tests pass.

---

## Phase 1: `Initializer.initialize()` now accepts `OutFactory`

**Files modified:**
- `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/Initializer.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/cli/AppMain.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/cli/sandbox/CallGLMApiSandboxMain.kt` (additional caller found)

**Changes:**
- `Initializer.initialize()` signature changed from `initialize(environment)` to `initialize(outFactory, environment)`.
- `InitializerImpl.initialize()` no longer creates `SimpleConsoleOutFactory` internally.
- `InitializerImpl.initializeImpl()` widened parameter from `SimpleConsoleOutFactory` to `OutFactory`.
- `SimpleConsoleOutFactory` import removed from `Initializer.kt`.
- `AppMain.kt` now passes `outFactory = SimpleConsoleOutFactory.standard()`.
- `CallGLMApiSandboxMain.kt` also updated (was not in the plan but found during grep scan).

---

## Phase 2: `SharedAppDepIntegFactory` created

**File created:**
`app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedAppDepIntegFactory.kt`

**Key decision:** Dropped the explicit `getTestOutManager()` and `getAppDependencies()` methods since
Kotlin `object` `val` properties already generate JVM getter methods with the same signature,
causing a platform declaration clash. The `val testOutManager` and `val appDependencies` properties
are directly accessible in Kotlin (and via generated getters in Java). Only `buildDescribeSpecConfig()`
is exposed as an explicit method since there's no property clash there.

**Post-review change (S1):** `testOutManager`, `appDependencies`, and `buildDescribeSpecConfig()`
changed from `public` to `internal` to enforce the KDoc guidance that callers should use
`SharedAppDepDescribeSpec` instead of accessing the factory directly. `SharedAppDepDescribeSpec`
is in the same module (`app/src/test`), so `internal` visibility is fully accessible there.

---

## Phase 3: `SharedAppDepSpecConfig` and `SharedAppDepDescribeSpec` created

**File created:**
`app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedAppDepDescribeSpec.kt`

**Key decision — body lambda receiver type:**
`SharedAppDepDescribeSpec.() -> Unit` is used as the body type (not `AsgardDescribeSpec.() -> Unit`).
This allows test classes to access `appDependencies` directly without a qualified `this@ClassName`
reference in the lambda body. The body is cast to `AsgardDescribeSpec.() -> Unit` when passing
to the super constructor — safe because every `SharedAppDepDescribeSpec` IS an `AsgardDescribeSpec`.

The `@Suppress("UNCHECKED_CAST")` annotation is intentionally left as-is per the instructions
(Issue 2 from the review was noted as architecturally sound and acceptable).

`SharedAppDepSpecConfig` is kept per ticket requirement (reviewer suggestion to remove it was noted
but the ticket explicitly requires it).

---

## Phase 4: Integration test migration

**Files modified:**
- `app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt` (original ticket scope)
- `app/src/test/kotlin/org/example/TmuxCommunicatorIntegTest.kt` (post-review, IMPORTANT Issue 1)
- `app/src/test/kotlin/org/example/TmuxSessionManagerIntegTest.kt` (post-review, IMPORTANT Issue 1)

**Changes in all three tests:**
- Extends `SharedAppDepDescribeSpec` instead of `AsgardDescribeSpec`.
- Removed inline construction of `TmuxCommandRunner`, `TmuxCommunicatorImpl`, `TmuxSessionManager`.
- Uses `appDependencies.tmuxSessionManager` for session management.
- Removed now-unused imports.

**Logging violation** in `SpawnTmuxAgentSessionUseCaseIntegTest` (embedded values in message string,
redundant `println`) was NOT fixed in this iteration per task instructions — follow-up ticket created
(`nid_nwg1em2siekphpqeeuhrtl5wk_E`).

---

## Phase 5: Auto-load memory updated

**File modified:**
`ai_input/memory/auto_load/4_testing_standards.md`

Added "Integration Test Base Class (with AppDependencies)" section after the existing
"Integration Tests (environment-dependent)" section, referencing `ref.ap.20lFzpGIVAbuIXO5tUTBg.E`.

---

## Follow-up Tickets Created

| Ticket ID | Title |
|---|---|
| `nid_nwg1em2siekphpqeeuhrtl5wk_E` | Fix logging violations in SpawnTmuxAgentSessionUseCaseIntegTest |
| `nid_g3z2de5zpq5dz608l9c651tam_E` | Fix resource leak in CallGLMApiSandboxMain - AppDependencies not closed |

---

## Verification

- `./gradlew :app:build` — PASSED
- `bash test.sh` (unit tests, no integ flag) — PASSED

---

## Deviations from Plan

1. **`CallGLMApiSandboxMain.kt` was an additional caller** not mentioned in the plan — updated to pass `outFactory`.
2. **`getTestOutManager()` and `getAppDependencies()` methods removed** from `SharedAppDepIntegFactory` due to JVM platform declaration clash with auto-generated Kotlin property getters. The `val` properties provide direct access, so the methods were redundant.
3. **`body` lambda receiver type changed to `SharedAppDepDescribeSpec.() -> Unit`** in `SharedAppDepDescribeSpec` (not `AsgardDescribeSpec.() -> Unit` as in the plan) — enables natural access to `appDependencies` in test lambda bodies.
4. **`TmuxCommunicatorIntegTest` and `TmuxSessionManagerIntegTest` migrated** in post-review iteration (not in original plan scope, but required by review IMPORTANT Issue 1).
5. **`SharedAppDepIntegFactory` properties made `internal`** in post-review iteration (review Suggestion S1).
