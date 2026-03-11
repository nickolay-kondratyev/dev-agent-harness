# Plan Review: Shared App Dependencies Integration Spec

Reviewer: Plan Reviewer Agent
Date: 2026-03-11

---

## Executive Summary

The plan is well-structured, technically accurate, and correctly scoped. All critical API signatures have been verified against actual source: `AsgardDescribeSpec` constructor, `AsgardDescribeSpecConfig.FOR_INTEG_TEST`, and the current `Initializer.initialize()` signature all match. Two issues require attention: one moderate concern about the `isIntegTestEnabled()` cross-package import being absent from the migration guidance, and one minor question about the `SharedAppDepSpecConfig` data class adding zero value. Neither is a blocker.

**PLAN_ITERATION can be skipped.**

---

## Critical Issues (BLOCKERS)

None.

---

## Major Concerns

**Concern: `SharedAppDepSpecConfig` data class is value-free wrapping**

The plan proposes:
```kotlin
data class SharedAppDepSpecConfig(
    val asgardConfig: AsgardDescribeSpecConfig = SharedAppDepIntegFactory.buildDescribeSpecConfig()
)
```

This class has exactly one field, one caller (`SharedAppDepDescribeSpec`), and no independent users. It is a wrapper that adds a layer without adding any semantics or extension points. The `SharedAppDepDescribeSpec` constructor could simply take `AsgardDescribeSpecConfig` directly with the default pulled from the factory:

```kotlin
abstract class SharedAppDepDescribeSpec(
    body: AsgardDescribeSpec.() -> Unit,
    config: AsgardDescribeSpecConfig = SharedAppDepIntegFactory.buildDescribeSpecConfig(),
) : AsgardDescribeSpec(body, config) { ... }
```

**Why:** Eliminates a type for zero gain. PARETO/KISS violation — the extra data class adds cognitive overhead and a file entry in the summary without solving any problem.

**Suggestion:** Flatten `SharedAppDepSpecConfig` into `SharedAppDepDescribeSpec` directly. Only keep `SharedAppDepSpecConfig` if there is a stated reason to have a typed "bag of settings" that callers might customize partially (e.g., overriding only `asgardConfig` while keeping the rest). In this plan there is no such use case.

Note: The plan already acknowledges this as "tightly coupled" — the logical conclusion is to skip the wrapper.

---

## Simplification Opportunities (PARETO)

**`buildDescribeSpecConfig()` method on factory is unnecessary indirection**

The plan exposes `SharedAppDepIntegFactory.buildDescribeSpecConfig()`. This is a method that constructs and returns a config. The same result can be achieved by having the default parameter expression in `SharedAppDepDescribeSpec` call `AsgardDescribeSpecConfig.FOR_INTEG_TEST.copy(testOutManager = SharedAppDepIntegFactory.testOutManager)` inline.

If `SharedAppDepSpecConfig` is removed (per above suggestion), then `buildDescribeSpecConfig()` on the factory is the only reason to have that method. If the config construction is inlined into the `SharedAppDepDescribeSpec` default parameter, `buildDescribeSpecConfig()` can be removed entirely.

**Value:** Reduces the surface area of `SharedAppDepIntegFactory`. Public methods on singletons are effectively a public API — prefer the minimal set.

---

## Minor Suggestions

**1. `isIntegTestEnabled()` cross-package import not addressed for new package**

The plan moves `SharedAppDepDescribeSpec` into `com.glassthought.chainsaw.integtest`. The updated `SpawnTmuxAgentSessionUseCaseIntegTest` will call `isIntegTestEnabled()` which is defined in `org.example.integTestSupport.kt`. This already works (confirmed by `GitBranchManagerIntegTest.kt` in `com.glassthought.chainsaw.core.git` which imports `org.example.isIntegTestEnabled`). The plan doesn't mention this import explicitly, but it's a non-issue since the pattern is already established. No action needed, but worth noting for the implementor so they are not surprised.

**2. `val` vs `get()` for `appDependencies` in `SharedAppDepDescribeSpec`**

The plan uses a property with a getter `get() = SharedAppDepIntegFactory.getAppDependencies()`. This is correct — the getter is a trivial delegation to a singleton which always returns the same instance. Consider making it a direct `val` assignment instead:

```kotlin
val appDependencies: AppDependencies = SharedAppDepIntegFactory.getAppDependencies()
```

Both are functionally equivalent (the singleton returns the same instance every call). `val` is marginally cleaner since it avoids unnecessary re-invocations of the getter across the spec body.

**3. `AsgardDescribeSpecConfig` data class has `enableStructuredTestReporter = true` as the default**

The plan correctly uses `FOR_INTEG_TEST.copy(testOutManager = ...)`. Verified: `FOR_INTEG_TEST` does NOT override `enableStructuredTestReporter`, so it will default to `true`. This is the correct behavior (structured test reports for integration tests). The plan doesn't mention this detail, but it is correct by omission.

**4. Memory file update format is correct**

The proposed addition to `ai_input/memory/auto_load/4_testing_standards.md` uses the same format as existing sections (plain markdown, no YAML frontmatter in this file). The section title and content are appropriate. The `ref.ap.20lFzpGIVAbuIXO5tUTBg.E` reference follows the established anchor point convention. No changes needed.

**5. Phase 4 missing: `afterEach` access pattern**

The plan (Phase 4, step 7) says "assign to a local `val sessionManager = appDependencies.tmuxSessionManager`". This is the right approach — `afterEach` is a suspend context and `appDependencies` is accessible via the inherited property. The plan correctly handles this case.

**6. Exploration report (Phase 2) suggests `SharedAppDepIntegFactory` in `src/main` — plan correctly overrides this**

The exploration report (Section 8) suggests placing `SharedAppDepIntegFactory` under `src/main`. The detailed plan correctly overrides this to `src/test`, which is right: test infrastructure has no place in production source sets. The exploration was exploring options; the plan made the correct call.

---

## Correctness Verification

All critical claims in the plan have been verified:

| Claim | Verified |
|-------|----------|
| `AsgardDescribeSpec` constructor signature matches plan description | CONFIRMED (from `.tmp/AsgardDescribeSpec.kt`) |
| `AsgardDescribeSpecConfig.FOR_INTEG_TEST` exists with exactly the described fields | CONFIRMED (from `AsgardDescribeSpecConfig.kt` in sources jar; javap output confirms `Companion.getFOR_INTEG_TEST()`) |
| `FOR_INTEG_TEST` has `shouldStopOnFirstFailure=true`, `overrideLogLevelProvider=DEBUG`, `afterTestLogLevelVerifyConfig=VerifyNoLinesAtOrAbove(DATA_ERROR)` | CONFIRMED |
| `Initializer.initialize()` currently creates `SimpleConsoleOutFactory` internally | CONFIRMED (line 60 of `Initializer.kt`) |
| `initializeImpl` currently takes `SimpleConsoleOutFactory` (not `OutFactory`) | CONFIRMED (line 66 of `Initializer.kt`) |
| `AppMain.kt` calls `Initializer.standard().initialize()` with no arguments | CONFIRMED (line 23 of `AppMain.kt`) |
| `runBlocking` is acceptable at test entry points per standards | CONFIRMED (CLAUDE.md: "acceptable only at main entry points, tests, and framework callbacks") |
| `isIntegTestEnabled()` is importable from other packages | CONFIRMED (e.g., `GitBranchManagerIntegTest.kt` imports `org.example.isIntegTestEnabled`) |

---

## Strengths

- **Phased approach is correct**: each phase is self-contained and can be compile-verified independently. The dependency chain (Phase 1 → 2 → 3 → 4 → 5) is right.
- **`runBlocking` at object initializer time** is correctly identified as the right approach — no lazy/mutex complexity for a process-scoped singleton.
- **Pitfall documentation in `PLANNER__PRIVATE.md`** is excellent: the `body` lambda receiver type mismatch, import relocation, and `initializeImpl` widening are all caught.
- **Scope discipline**: the plan explicitly calls out NOT fixing the multi-assertion `it` block in `SpawnTmuxAgentSessionUseCaseIntegTest`. This is correct — scope creep kills plans.
- **Lifecycle reasoning** for not closing `AppDependencies` is sound and explicitly justified.
- **Q3 on package placement** is correctly resolved: `com.glassthought.chainsaw.integtest` is the right choice.
- **`appDependencies` visibility as `val` (public)** over `protected` (Q1) is the right call for test infrastructure.
- **No test-of-test-infrastructure**: correctly argues that compile-time verification + existing integ tests passing is sufficient.

---

## Inline Corrections Applied

The following minor corrections are applied directly to this review rather than modifying the plan (plan is structurally sound):

1. Phase 3 `SharedAppDepSpecConfig` description says "can live in the same file" — this is correct and already recommended by the plan.
2. Phase 4, Step 2 says "lines 36-57" — verified against actual source. Line 36 starts `val commandRunner`, line 57 ends `outFactory = outFactory,` — accurate.
3. The plan note that `SimpleConsoleOutFactory` import must be removed from `Initializer.kt` and added to `AppMain.kt` is accurate — the import `com.asgard.core.out.impl.console.SimpleConsoleOutFactory` appears on line 5 of `Initializer.kt`.

---

## Verdict

- [ ] APPROVED
- [x] APPROVED WITH MINOR REVISIONS
- [ ] NEEDS REVISION
- [ ] REJECTED

**PLAN_ITERATION can be skipped.**

The one substantive revision to consider during implementation: flatten `SharedAppDepSpecConfig` into `SharedAppDepDescribeSpec` directly (removing the one-field wrapper data class). This is a simplification the implementor can apply autonomously without plan iteration. If the implementor has a reason to keep `SharedAppDepSpecConfig` (e.g., for future extensibility), that is an acceptable tradeoff — but the plan should acknowledge it is speculative extensibility rather than current need.
