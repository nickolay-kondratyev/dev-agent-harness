# Implementation Review: Use AiOutputStructure in ContextForAgentProviderImpl

## Summary

This change moves PRIVATE.md path resolution from callers (`PartExecutorImpl`, `InnerFeedbackLoop`) into `ContextForAgentProviderImpl`, using the already-injected `AiOutputStructure`. The `privateMdPath: Path?` field on `AgentInstructionRequest` is replaced with `subPartName: String`, and `InstructionSection.PrivateMd` changes from a `data object` to a `data class` carrying the resolved path. The `@Suppress("UnusedPrivateProperty")` annotation is removed.

**Overall assessment: APPROVE.** Clean, focused change. Correct dispatch logic. Good test coverage. No regressions found.

## CRITICAL Issues

None.

## IMPORTANT Issues

None.

## Suggestions

### 1. Minor DRY opportunity in `resolvePrivateMdPath` (OPTIONAL)

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt` (lines 64-75)

The first three branches of `resolvePrivateMdPath` are identical -- they all call `executionPrivateMd(request.executionContext.partName, request.subPartName)`. The last two branches are also identical. This could be simplified using the existing `executionContextOrNull` extension:

```kotlin
private fun resolvePrivateMdPath(request: AgentInstructionRequest): Path {
    val executionContext = request.executionContextOrNull
    return if (executionContext != null) {
        aiOutputStructure.executionPrivateMd(executionContext.partName, request.subPartName)
    } else {
        aiOutputStructure.planningPrivateMd(request.subPartName)
    }
}
```

**Counter-argument:** The explicit `when` gives compiler-enforced exhaustiveness and will surface new sealed subtypes at compile time. Both approaches are valid. This is purely a style preference -- the current approach is acceptable.

### 2. Test isolation note (OPTIONAL)

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/src/test/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderAssemblyTest.kt` (lines 369-401)

The "execution vs planning requests" test uses a shared `tempDir` and `aiOutputStructure` across two `describe` blocks. If the doer test writes a file at a planning-like path by accident, the planner test could still pass. In practice this is unlikely given how `AiOutputStructure` constructs paths (they are structurally distinct: `execution/` vs `planning/`), so this is a non-issue in practice.

## Verification Checklist

- [x] `@Suppress("UnusedPrivateProperty")` removed from `ContextForAgentProviderImpl`
- [x] `privateMdPath: Path?` removed from all 5 `AgentInstructionRequest` subtypes
- [x] `subPartName: String` added to all 5 subtypes (non-nullable, no default)
- [x] `InstructionSection.PrivateMd` changed from `data object` to `data class(val resolvedPath: Path?)`
- [x] `resolvePrivateMdPath` correctly dispatches execution vs planning
- [x] All callers updated: `PartExecutorImpl` (2 sites), `InnerFeedbackLoop` (1 site)
- [x] `SubPartConfig.privateMdPath` correctly kept for compaction (separate concern)
- [x] Test fixtures updated with `subPartName`
- [x] Removed test (privateMdPath=null) is covered by InstructionSectionTest (resolvedPath=null) and assembly test (non-existent file)
- [x] New test added verifying execution vs planning path resolution
- [x] `./test.sh` passes (exit 0)
- [x] `./sanity_check.sh` passes (exit 0)

## Documentation Updates Needed

None.
