# Implementation Review: Setup ai_input Folder

## Summary

The implementation sets up `ai_input/memory/` with 6 auto_load files and 5 deep memory files, adapted from thorg patterns for this CLI Kotlin Agent Harness project. The CLAUDE.md is regenerated correctly. Overall this is solid work -- well-structured, properly adapted, thorg-specific content correctly removed, and the auto_load-to-deep-memory layering (summary + pointer in auto_load, full detail in deep memory) is clean.

## Requirements Checklist

### 1. NOT thorg-specific -- PASS
- No thorg-specific patterns (thin client, S-VM-V, VSCode, webview, Obsidian, Dendron, PKM, etc.) anywhere in `ai_input/` or `CLAUDE.md`.
- The only occurrence of "thorg" is the intentional `NOT thorg-specific` statement in `1_core_description.md`, which serves as a clarifying boundary.
- Thorg-specific ValTypes (`NOTE_NAME`, `NOTE_ID`, `VAULT_NAME`, `Val.noteId()`) correctly omitted from `out_logging_patterns.md`.
- Thorg-specific tags (`thorgCore`, `editor.thorg`, `vscode`, etc.) correctly replaced with project-appropriate tags.

### 2. Depends on asgardCore -- PASS
- `1_core_description.md` references Out/OutFactory, ProcessRunner, AsgardCloseable, coroutines.
- `3_kotlin_standards.md` references AsgardBaseException, AsgardCloseable `.use{}` pattern.
- `4_testing_standards.md` references AsgardDescribeSpec.
- `out_logging_patterns.md` shows correct `com.asgard.core` import paths.

### 3. Keep CLAUDE.md to things NOT in code -- PASS
- No dependency versions, no build commands, no Gradle specifics, no file path details.
- Content focuses on principles, patterns, and conventions.
- Correctly excluded detekt configuration details (mentioned in exploration but not included).

### 4. Kotlin practices adapted from thorg -- PASS
- Constructor injection, structured logging, structured exceptions, coroutines, functional style, resource management, sealed when -- all present and correctly adapted.
- TypeScript-specific content (`Remember in typescript use beforeAll with describe.`) correctly removed from `in_tests__one_assert_per_test.md`.
- `dont_log_and_throw.md` changed from TypeScript example (thorg original) to Kotlin example.
- `in_tests__fail_hard_never_mask.md` generalized from thorg-specific Node.js/Kotlin examples to generic dependency examples.

### 5. Deep memories have proper frontmatter -- PASS
- All 5 deep memory files have `---\ndesc: "..."\n---` frontmatter.
- The `desc` values in frontmatter exactly match the descriptions in `z_deep_memory_pointers.md`.

### 6. Content quality -- PASS with one issue (see IMPORTANT below)
- Writing is clear, concise, and actionable.
- Good structure: auto_load files give rules/summaries, deep memories provide examples and rationale.
- Cross-references between files are correct (`See deep memory: X` in auto_load, `See Y.md` in deep memories).

### 7. No knowledge duplication between auto_load and deep memory -- PASS
- Auto_load files contain summary rules with "See deep memory" pointers.
- Deep memory files contain detailed patterns, examples, and rationale.
- The overlap is the rule statement itself, which is intentional and expected in the summary-to-detail layering.

### 8. Tags are project-appropriate -- PASS
- Tags: `harness`, `harness.workflow`, `harness.cli`, `asgardCore`, `agents`, `file-io`, `docs`.
- No thorg-specific tags. All tags are relevant to an agent harness project.

## Issues Found

### IMPORTANT: Knowledge duplication between 1_core_description.md and 3_kotlin_standards.md

**Severity: MEDIUM**

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness/ai_input/memory/auto_load/1_core_description.md` (lines 11-16)

The "Architecture Principles" section in `1_core_description.md` duplicates content from `3_kotlin_standards.md`:

| `1_core_description.md` Architecture Principles | `3_kotlin_standards.md` |
|---|---|
| Constructor injection (manual DI, no framework, no singletons) | Constructor injection only -- no DI framework, no singletons. |
| Structured logging via Out/OutFactory (never println) | Use Out / OutFactory for all logging (never println). |
| Structured exceptions extending AsgardBaseException | Extend AsgardBaseException hierarchy for structured exceptions. |
| Composition over inheritance | Composition over inheritance -- always. |
| Favor immutability | Favor immutability -- immutable data structures by default... |

**Recommendation:** The `1_core_description.md` "Architecture Principles" should be trimmed to just the project-level overview points that are NOT covered in detail by `3_kotlin_standards.md`. For example, keep it to a one-liner like "See `3_kotlin_standards.md` for development standards" or only list principles that are truly project-architectural (like "Agent coordination" and "file-based communication") rather than Kotlin coding standards. Alternatively, remove the "Architecture Principles" subsection entirely since `3_kotlin_standards.md` covers all of it more thoroughly.

This is a DRY violation within auto_load files, but it is a LOW-IMPACT one because auto_load files are all loaded together anyway and the duplication is between a summary list and a detailed section. It does not cause inconsistency risk since both are loaded simultaneously.

## Suggestions

### 1. Consider adding a `val_type_specificity` deep memory

The thorg source had a dedicated `val_type_in_out_logging_are_value_specific.md` deep memory. The new `out_logging_patterns.md` covers this briefly with the rule "ValType must be semantically specific to the value (e.g., ValType.FILE_PATH_STRING not ValType.STRING)." This is probably sufficient for now, but as the project grows and ValType misuse becomes a pattern, a dedicated deep memory could be useful.

### 2. The `in_tests__fail_hard_never_mask.md` uses JUnit-style annotations

The examples in `in_tests__fail_hard_never_mask.md` use `@BeforeAll` and `@Test` (JUnit-style), but `4_testing_standards.md` specifies Kotest `DescribeSpec` / `AsgardDescribeSpec` as the test framework. The examples in the deep memory are still clear and the principle is framework-agnostic, but for consistency the examples could use Kotest `beforeSpec` / `describe`/`it` style. This is a LOW priority concern since the anti-patterns being demonstrated are clear regardless of framework syntax.

## Verification Results

- **CLAUDE.generate.sh**: Runs successfully (exit 0), output matches committed CLAUDE.md.
- **No thorg-specific leaks**: Verified via grep across all new files.
- **No stale content**: No dependency versions, build commands, or file paths that would go stale.
- **Deep memory frontmatter**: All 5 files have proper `desc:` fields matching pointer table descriptions.

## Overall Verdict: APPROVED

The implementation is well-executed. The one MEDIUM-severity DRY issue (`1_core_description.md` architecture principles duplicating `3_kotlin_standards.md`) is worth fixing but does not block. The content is accurate, well-adapted from thorg originals, and correctly excludes all thorg-specific patterns.
