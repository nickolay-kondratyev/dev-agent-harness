# Plan Review: TicketParser

**Reviewed**: `DETAILED_PLANNING__PUBLIC.md`
**Ticket**: `nid_r9on08uqjmumuc6wi2c53e8p9_E` — Ticket Parser
**Reviewer**: PLAN_REVIEWER sub-agent

---

## Executive Summary

The plan is thorough, well-structured, and correctly aligned with project standards. It covers all ticket requirements, correctly decomposes responsibilities (pure parser object / file-reading impl / typed data class), and specifies a complete test suite with proper BDD structure and one-assert-per-`it` discipline. Two minor concerns are noted below — neither is a blocker.

---

## Critical Issues (BLOCKERS)

None.

---

## Major Concerns

None.

---

## Minor Suggestions

### 1. ValType for path logging — use `FILE_PATH_STRING` not `STRING_USER_AGNOSTIC`

**Location**: Phase 4, "ValType guidance" paragraph.

The plan says: "use `ValType.FILE_PATH` if it exists in the library, otherwise `ValType.STRING_USER_AGNOSTIC`."

`ValType.FILE_PATH` does NOT exist. The correct type is `ValType.FILE_PATH_STRING` (confirmed in `ValType.kt`). It has `UserSpecificity.USER_SPECIFIC` which is semantically correct — a ticket file path is user-specific data.

**Inline correction applied directly to the plan** (see below).

---

### 2. Exception hierarchy: `IllegalArgumentException` vs `AsgardBaseException`

**Location**: Phases 3 and 4 — all throw sites.

CLAUDE.md states: "Extend `AsgardBaseException` hierarchy for structured exceptions." The plan uses `IllegalArgumentException` for fail-fast validation on missing required fields and malformed frontmatter.

Two mitigating factors make this non-blocking:
- The ticket itself explicitly says "fail-fast (`IllegalArgumentException`)".
- Existing code in `TmuxCommunicatorImpl` uses `IllegalStateException` directly without extending `AsgardBaseException`.

**Recommendation**: If `AsgardBaseException` has a natural subtype for input validation errors (e.g., `AsgardUserCausedException`), prefer it. If the hierarchy does not have a clean fit, `IllegalArgumentException` is acceptable for this scope since the ticket explicitly specifies it. A follow-up ticket to address the exception hierarchy inconsistency across the codebase is appropriate.

---

### 3. Missing edge case test: empty body

**Location**: Phase 6 (`YamlFrontmatterParserTest`), Phase 7 (`TicketParserTest`).

The plan calls out the edge case of "body containing `---`" (good). It does not explicitly include a test for a ticket file with NO body text after the closing `---`. The algorithm handles it correctly (empty `bodyLines` → `body = ""`), but an explicit test for `description = ""` or `description.isBlank()` when there is no body would strengthen correctness coverage.

**This is non-blocking.** Recommend adding one `it` to `TicketParserTest`:
```
describe("GIVEN a ticket file with no body after closing ---") {
    describe("WHEN parse is called") {
        it("THEN description is empty")
    }
}
```

---

## Inline Corrections Applied

### Phase 4 — ValType guidance (corrected)

**Original text**:
> **ValType guidance** for path logging: use `ValType.FILE_PATH` if it exists in the library, otherwise `ValType.STRING_USER_AGNOSTIC`. Check the existing codebase's `ValType` usages for the correct constant name.

**Corrected text**:
> **ValType guidance** for path logging: use `ValType.FILE_PATH_STRING` (confirmed present in `ValType.kt`, `UserSpecificity.USER_SPECIFIC` — correct since a ticket path is user data).

---

## Strengths

- **Clean SRP decomposition**: `YamlFrontmatterParser` as a stateless `object` (pure string parsing, no I/O, no logging) is exactly right. `TicketParserImpl` owns file I/O and assembly. `TicketData` is a pure data holder. Each class has a single, clear reason to change.

- **Line-based delimiter algorithm**: The plan's choice of `content.lines()` over regex-based delimiter splitting is simpler, handles both `\n` and `\r\n` automatically, and is easy to reason about. This is the right KISS choice.

- **Test structure**: Correct BDD GIVEN/WHEN/THEN, one `it` per assertion, proper `it` as suspend context for `parse()` calls, `shouldThrow` idiom for exception tests. The plan demonstrates solid testing discipline.

- **Phase ordering**: TDD-oriented — test resource files created before tests, `YamlFrontmatterParser` tests run green before `TicketParser` is even implemented. Clean incremental verification.

- **Completeness**: All 8 completion criteria from the ticket are accounted for: `TicketData`, `YamlFrontmatterParser`, `TicketParser` + impl, snakeyaml dep, unit tests, test resources, AP creation, AP cross-linking.

- **Explicit additionalFields filtering**: The plan explicitly states `id`, `title`, `status` must NOT appear in `additionalFields` and specifies the `filterKeys` step. This prevents subtle duplicated-state bugs.

- **snakeyaml per-call instantiation**: `Yaml().load()` called inline (not stored as a field on the `object`) sidesteps snakeyaml's non-thread-safety concern at the cost of negligible per-call overhead. Correct tradeoff.

---

## Verdict

- [x] **APPROVED**

No blocking issues. Two non-blocking minor suggestions noted above (ValType name inline-corrected, exception hierarchy flag for awareness, empty-body test case recommendation). Implementation can proceed directly.
