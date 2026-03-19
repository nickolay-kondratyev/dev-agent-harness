# Implementation Review -- TryNResolver

## Summary

`TryNResolver` interface + `TryNResolverImpl` correctly implements the try-N resolution algorithm specified in `doc/core/git.md` (lines 79-92). The implementation is clean, minimal, and follows project conventions (interface + impl in same file, constructor injection, BDD tests with `AsgardDescribeSpec`). Tests pass. All required scenarios from the ticket are covered plus a useful extra case (different ticket directories are ignored).

**Overall assessment: APPROVE with one IMPORTANT item (DRY violation) and minor suggestions.**

## No CRITICAL Issues

No security, correctness, or data-loss issues found.

## IMPORTANT Issues

### 1. DRY violation: `AI_OUT_DIR` constant duplicated

The string `".ai_out"` is defined as a private constant in two places:

- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-4/app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/AiOutputStructure.kt` (line 151)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-4/app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/TryNResolver.kt` (line 46)

This is a knowledge duplication -- the name of the `.ai_out/` directory is a business rule that should live in one place. If it ever changes, both files need to be updated independently.

**Suggested fix**: Extract `AI_OUT_DIR` to a shared constant (e.g., a top-level `object AiOutConstants` or make the existing one in `AiOutputStructure` `internal`/public) and reference it from both `AiOutputStructure` and `TryNResolverImpl`. Alternatively, `TryNResolverImpl` could take an `aiOutDirName: String` constructor parameter, but a shared constant is simpler here.

## Suggestions

### 1. Test package does not match source package

The implementation lives in `com.glassthought.shepherd.core.supporting.git` but the test is in `com.glassthought.shepherd.core.git`:

- Source: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-4/app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/TryNResolver.kt`
- Test: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-4/app/src/test/kotlin/com/glassthought/shepherd/core/git/TryNResolverTest.kt`

The test should be in `com.glassthought.shepherd.core.supporting.git` to match the source, which is the standard convention and would matter if any members become `internal`.

### 2. Hardcoded branch name strings in tests

The test constructs directory names like `".ai_out/TK-001__my-feature__try-1"` as raw strings (line 32, 43, etc.). If `BranchNameBuilder.build()` format ever changes, these tests will silently pass while testing against stale directory names.

Consider using `BranchNameBuilder.build(ticketData, 1)` to construct the directory names in the test setup, so that test setup stays in sync with the actual implementation:

```kotlin
val branchForTry1 = BranchNameBuilder.build(ticketData, 1)
Files.createDirectories(repoRoot.resolve(".ai_out/$branchForTry1"))
```

This makes the tests more robust against format changes while also eliminating the implicit knowledge of branch name format from the test code.

## Correctness Verification

| Spec requirement | Implementation | Test coverage |
|---|---|---|
| Start at candidateN=1 | `FIRST_TRY_NUMBER = 1` | "no .ai_out/ directory exists" -> returns 1 |
| Build via BranchNameBuilder | `BranchNameBuilder.build(ticketData, candidateN)` | All tests implicitly verify this |
| Check directory existence | `Files.isDirectory(candidateDir)` | All tests use real filesystem |
| Increment on exists | `candidateN++` in while loop | "try-1 exists" -> returns 2 |
| Return on not-exists | `return candidateN` | All THEN assertions |
| Gap detection | Sequential scan finds first gap | "try-1 and try-3 exist" -> returns 2 |
| Unrelated dirs ignored | Branch name includes ticket ID | "different ticket" -> returns 1 |

## Documentation Updates Needed

None required.
