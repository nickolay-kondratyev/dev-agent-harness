# Implementation Review: Wire NonInteractiveAgentRunner into ShepherdContext

## Summary

This change wires `NonInteractiveAgentRunner` into `ShepherdContext` through `ContextInitializerImpl`. It adds the `AI_MODEL_ZAI_FAST` env var constant, reads the ZAI API key from file with fail-hard semantics, and introduces injectable seams for testability. The implementation is clean, follows project patterns, and all tests pass (unit + sanity check).

**Overall assessment: PASS -- well-implemented, minor items below.**

---

## Requirement Checklist

| # | Requirement | Status | Notes |
|---|-------------|--------|-------|
| 1 | `nonInteractiveAgentRunner` as top-level property on `ShepherdContext` (NOT infra) | PASS | Added as constructor param directly on `ShepherdContext` |
| 2 | Create and wire `NonInteractiveAgentRunnerImpl` in `ContextInitializerImpl` | PASS | `createNonInteractiveAgentRunner()` in `initializeImpl()` |
| 3 | Read `ZAI_API_KEY` from `${MY_ENV}/.secrets/Z_AI_GLM_API_TOKEN`, fail hard if missing/empty | PASS | Reads file, trims, checks empty, catches IOException |
| 4 | Add `AI_MODEL__ZAI__FAST` to `Constants.REQUIRED_ENV_VARS.ALL` with named constant | PASS | `AI_MODEL_ZAI_FAST` added to both object and `ALL` list |
| 5 | Tests: verify wiring, fail-hard scenarios | PASS | 5 test scenarios covering happy path + 4 failure modes |
| 6 | BDD style, one assert per `it` block, Kotest DescribeSpec | PASS | Follows GIVEN/WHEN/THEN structure |

---

## Criteria Assessment

| Criterion | Grade | Detail |
|-----------|-------|--------|
| Satisfies all ticket requirements | PASS | All 6 requirements met |
| SRP / DRY / KISS | PASS | `createNonInteractiveAgentRunner` is a focused private method; no duplication |
| Explicit naming | PASS | Clear injectable seam names, descriptive error messages |
| Injectable seams for testability | PASS | Follows `EnvironmentValidator` pattern with `envVarReader`, `fileReader`, `processRunnerFactory` |
| Test quality (BDD, one-assert) | PASS | Each `it` block has one logical assertion |
| Kotlin standards | PASS | Constructor injection, no singletons, proper exception hierarchy |
| No hacks / no over-engineering | PASS | Lean and direct implementation |
| Existing test integrity | PASS | `EnvironmentValidatorTest` updated to include new env var in `allEnvVarsPresent` map |

---

## Issues Found

### None at CRITICAL or IMPORTANT severity.

---

## Suggestions

### 1. `FileNotFoundException` catch granularity (MINOR)

In `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializer.kt`, line 140:

```kotlin
} catch (e: java.io.IOException) {
```

The catch on `IOException` is correct because `FileNotFoundException` extends `IOException`, so both "file not found" and "permission denied" are handled. This is appropriate. No change needed -- just noting the design choice is sound.

### 2. Test re-initializes for each `it` block (MINOR)

In `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/initializer/ContextInitializerTest.kt`, lines 56-69:

The "GIVEN valid configuration" block calls `initializer.initialize(outFactory)` independently in each of 3 `it` blocks. This creates separate `ShepherdContext` instances per assertion which is fine for correctness (tests are isolated). However, if initialization ever becomes expensive, a shared `lateinit` with a `beforeEach` could DRY this up. For now this is acceptable -- keeps tests simple and independent.

### 3. Integration test path relies on real file system (NOTE)

`SharedContextIntegFactory` calls `ContextInitializer.standard()` which uses the real `System::getenv` and real file reader. This means integration tests will now also fail hard if `${MY_ENV}/.secrets/Z_AI_GLM_API_TOKEN` is missing. This is the correct "fail hard" behavior per project standards -- just worth noting for anyone running integration tests for the first time.

### 4. Missing newline at end of ShepherdContext.kt (NIT)

The diff shows `No newline at end of file` for `ShepherdContext.kt`. This is a minor hygiene issue.

---

## Documentation Updates Needed

None. The KDoc on `Constants.REQUIRED_ENV_VARS.AI_MODEL_ZAI_FAST` and the `ContextInitializerImpl` constructor params are sufficient.

---

## Verdict

**APPROVE** -- The implementation is clean, well-tested, follows project patterns (injectable seams, fail-hard, BDD tests), and satisfies all ticket requirements. No blocking issues found.
