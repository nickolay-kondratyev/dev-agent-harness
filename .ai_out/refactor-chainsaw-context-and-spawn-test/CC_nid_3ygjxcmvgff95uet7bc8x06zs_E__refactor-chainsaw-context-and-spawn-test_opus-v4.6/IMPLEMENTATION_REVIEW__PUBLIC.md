# Implementation Review: Refactor ChainsawContext

## Verdict: PASS (with issues to address)

## Summary

The refactoring successfully groups `ChainsawContext`'s flat fields into logical sub-groups (`TmuxInfra`, `DirectLlmInfra`, `Infra`, `UseCases`), wires `SpawnTmuxAgentSessionUseCase` into the context, and simplifies `SpawnTmuxAgentSessionUseCaseIntegTest` to use the shared context. All unit tests pass. No test files were deleted. No functionality was removed. The diff is clean, well-structured, and the commit messages are clear.

## Build & Tests

- `./sanity_check.sh` -- PASS
- `./gradlew :app:test` -- BUILD SUCCESSFUL, all unit tests pass
- No deleted test files (verified via `git diff --diff-filter=D`)
- No stale references to old flat API (`chainsawContext.tmuxSessionManager`, `deps.tmuxSessionManager`, etc.) -- all migrated

---

## MAJOR Issues

### 1. MAJOR: Stale KDoc and comments in `SharedContextDescribeSpec.kt` reference old names

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedContextDescribeSpec.kt`

The "When to use" section was updated to use new names (`SharedContextDescribeSpec`, `chainsawContext`), but three references to the OLD names remain:

- **Line 38**: KDoc example uses `SharedAppDepDescribeSpec` instead of `SharedContextDescribeSpec`
  ```kotlin
  * class MyIntegTest : SharedAppDepDescribeSpec({  // <-- should be SharedContextDescribeSpec
  ```

- **Lines 61-63**: Internal cast comments reference `SharedAppDepDescribeSpec` and `appDependencies`:
  ```kotlin
  // Safe cast: every SharedAppDepDescribeSpec IS an AsgardDescribeSpec.
  // Using SharedAppDepDescribeSpec as the receiver type allows subclass tests to access
  // `appDependencies` directly in their body lambda without a qualified `this` reference.
  ```
  These should reference `SharedContextDescribeSpec` and `chainsawContext`.

This violates the "Behavior MUST thoroughly match Naming" principle from CLAUDE.md. A developer copying the KDoc example will write code that does not compile.

### 2. MAJOR: `ai_input/memory/auto_load/4_testing_standards.md` not updated

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/ai_input/memory/auto_load/4_testing_standards.md`

Lines 26-31 in the "Integration Test Base Class" section still reference:
- `SharedAppDepDescribeSpec` (should be `SharedContextDescribeSpec`)
- `SharedAppDepIntegFactory` (should be `SharedContextIntegFactory`)
- `appDependencies` property (should be `chainsawContext`)
- File path `SharedAppDepDescribeSpec.kt` (should be `SharedContextDescribeSpec.kt`)

This is the canonical documentation that agents and developers read. The stale names will mislead anyone following these instructions. Per CLAUDE.md: "Keep related docs up-to-date."

**Note:** This may have been stale before this PR (the rename likely happened in a prior change). However, since this PR explicitly touched `SharedContextDescribeSpec.kt` and updated the KDoc "When to use" section, the remaining stale references should also be fixed -- especially since the CLAUDE.md instructions state to keep docs up-to-date.

### 3. MAJOR: `AppDependenciesCloseTest` has significant DRY violation with `InitializerImpl`

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/test/kotlin/com/glassthought/initializer/AppDependenciesCloseTest.kt`

The `buildDepsWithHttpClient` function (lines 27-76) manually constructs the full `ChainsawContext` object graph -- including `TmuxInfra`, `DirectLlmInfra`, `Infra`, `UseCases`, `ClaudeCodeAgentStarterBundleFactory`, `DefaultAgentTypeChooser`, and `SpawnTmuxAgentSessionUseCase`. This is a near-exact duplication of `InitializerImpl.initializeImpl()`.

Before this PR, the duplication was smaller (just the flat fields). The refactoring significantly expanded the construction code, making the duplication worse. Every time a new use case or infra component is added to `ChainsawContext`, this test must be manually updated in lockstep.

**Suggested fix:** The test's intent is to verify OkHttpClient cleanup. Consider extracting a test-friendly factory method or accepting the OkHttpClient as a parameter in `Initializer.initialize()` (or a test-only overload) to avoid duplicating the entire wiring.

---

## SUGGESTION Issues

### 4. SUGGESTION: Naming inconsistency -- `TmuxInfra`, `DirectLlmInfra`, `Infra`, `UseCases` are generic names in global scope

These data classes are defined at the package-level in `Initializer.kt`:
- `TmuxInfra`, `DirectLlmInfra`, `Infra`, `UseCases`

While they are in the `com.glassthought.chainsaw.core.initializer` package (which provides some scoping), names like `Infra` and `UseCases` are very generic. If the project grows, these could collide or confuse. Consider prefixing with `Chainsaw` (e.g., `ChainsawInfra`, `ChainsawUseCases`) or nesting them inside `ChainsawContext` as inner classes. This is a minor point though -- the package scoping is adequate for now.

### 5. SUGGESTION: `data class` semantics may be unnecessary for the grouping types

`TmuxInfra`, `DirectLlmInfra`, `Infra`, and `UseCases` are declared as `data class`. The `data class` generates `equals()`, `hashCode()`, `copy()`, `toString()`, and `componentN()` functions. For dependency grouping containers:
- `equals()`/`hashCode()` comparing mutable service instances by reference is meaningless
- `copy()` on a context sub-graph is potentially dangerous (sharing mutable state)
- `toString()` would print internal state (e.g., API tokens in `DirectLlmInfra.httpClient`)

Plain classes would be more appropriate since these are structural groupings, not value objects. This is a low-priority suggestion.

### 6. SUGGESTION: Pre-existing resource leak in `CallGLMApiSandboxMain.kt`

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/chainsaw/cli/sandbox/CallGLMApiSandboxMain.kt`

The `ChainsawContext` is never closed (no `.use{}` block). This is a pre-existing issue not introduced by this PR, but worth noting as a follow-up since the implementer touched this file.

---

## What Was Done Well

1. **Backward compatible interface change**: New parameters (`systemPromptFilePath`, `claudeProjectsDir`) on `Initializer.initialize()` have sensible defaults, preserving backward compatibility for existing callers like `AppMain.kt`.

2. **Complete migration**: All references to the old flat API were updated across all files. Verified via grep -- zero stale references to `chainsawContext.tmuxSessionManager`, `deps.outFactory`, etc.

3. **No tests removed**: All existing test classes are preserved. The `SpawnTmuxAgentSessionUseCaseIntegTest` was simplified without losing any test behavior.

4. **Good test simplification**: Moving `resolveSystemPromptFilePath()` and `findGitRepoRoot()` from the test into `SharedContextIntegFactory` is the right call -- the factory is responsible for context configuration.

5. **Anchor point preserved**: `ap.TkpljsXvwC6JaAVnIq02He98.E` on `ChainsawContext` is intact.

6. **Clean grouping hierarchy**: `ChainsawContext -> infra/useCases -> tmux/directLlm -> individual services` provides good discoverability via IDE autocomplete.

7. **`internal` visibility on `httpClient`**: Good pragmatic choice given data class constraints. Well-documented with KDoc explaining why.
