# Implementation Iteration: Address Review Should-Fix Items

## Fix 1: Remove dead `heading` parameter from `FeedbackDirectorySection`

**Problem:** `FeedbackDirectorySection` had both `heading` and `headerBody` parameters, but when `headerBody` was set, `heading` was silently ignored. All production callers passed both, making `heading` dead code. This violated POLS.

**Fix:** Removed the `heading` parameter entirely. Renamed `headerBody` to `header` and made it required (non-nullable). The render method now always uses `header` directly.

- Callers in `ContextForAgentProviderImpl` already passed full header text via `InstructionText.*_HEADER` constants, so they just drop the old `heading` param and rename `headerBody` to `header`.
- Test fixtures that only used `heading` now pass `"## Heading Text"` as `header` directly.

**Files changed:**
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt` -- `FeedbackDirectorySection` data class simplified
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` -- 3 callers updated
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt` -- 5 test fixtures updated

## Fix 2: Improve ordering test assertion diagnostics

**Problem:** Ordering tests used `indices.none { it == -1 } shouldBe true` which produces `false shouldBe true` on failure with no indication of which section is missing or misordered.

**Fix:** Extracted a shared `assertSectionsInOrder` helper function that uses `withClue` to produce actionable failure messages:
- Missing section: `Section '# Ticket' not found in rendered instructions`
- Misordered section: `'# Plan' (at index 450) should appear before '# Prior Agent Outputs' (at index 300)`

All four role ordering tests now delegate to this helper.

**Files changed:**
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionOrderingTest.kt` -- Added `assertSectionsInOrder` helper, replaced all 4 inline assertion blocks

## Verification

All tests pass: `./gradlew :app:test` exits 0.
