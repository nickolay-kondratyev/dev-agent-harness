# Plan Review: AiOutputStructure

## Executive Summary

The plan is solid, well-structured, and appropriately simple for the task at hand. It correctly captures a deterministic utility class with pure path resolution methods and one I/O method. There are two notable gaps regarding completeness -- missing path methods for planning roles (PUBLIC.md, PRIVATE.md, session_ids under `planning/`) and the absence of `ensureStructure` handling for planning role directories. These are functional gaps that should be addressed before implementation.

## Critical Issues (BLOCKERS)

### 1. Missing planning role path methods for PUBLIC.md, PRIVATE.md, and session_ids

- **Issue:** The design ticket (`_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`, lines 423-431) shows that `planning/${ROLE}/` directories contain `PUBLIC.md`, `PRIVATE.md`, and `session_ids/${timestamp}.json` -- identical structure to `phases/${part}/${ROLE}/`. The plan provides `planningRoleDir(branch, role)` but does NOT provide methods to resolve:
  - `planning/${ROLE}/PUBLIC.md`
  - `planning/${ROLE}/PRIVATE.md`
  - `planning/${ROLE}/session_ids/`

  The existing `publicMd(branch, part, role)`, `privateMd(branch, part, role)`, and `sessionIdsDir(branch, part, role)` all route through `phaseRoleDir` and require a `part` parameter, so they cannot be reused for planning roles.

- **Impact:** The planning workflow (PLANNER/PLAN_REVIEWER iteration loop) will need to reference these files. Without dedicated methods, callers will have to manually construct paths via `planningRoleDir(branch, role).resolve("PUBLIC.md")`, which defeats the purpose of centralizing path logic in this class.

- **Recommendation:** Add planning-specific path resolution methods. Two options:
  - **Option A (Recommended -- simple overloads):** Add `planningPublicMd(branch, role)`, `planningPrivateMd(branch, role)`, `planningSessionIdsDir(branch, role)` that resolve under `planningRoleDir`.
  - **Option B (More abstract):** Generalize `publicMd`/`privateMd`/`sessionIdsDir` to work with any "role dir" base path. This adds unnecessary abstraction for now -- Option A is simpler and more explicit.

### 2. `ensureStructure` does not create planning directories

- **Issue:** The `ensureStructure(branch, parts)` method (plan section 3, Phase 1, step 7) only creates:
  - `harnessPrivateDir`
  - `sharedDir`
  - `planDir`
  - Per-part/role: `phaseRoleDir` and `sessionIdsDir`

  It does NOT create `planning/${ROLE}/` or `planning/${ROLE}/session_ids/` directories. The design ticket shows these as part of the directory tree.

- **Impact:** For `with-planning` workflows, the planning directories will not be created upfront. Either the caller has to create them manually or `ensureStructure` needs to handle them.

- **Recommendation:** Add an optional parameter to `ensureStructure` for planning roles, e.g.:
  ```kotlin
  fun ensureStructure(branch: String, parts: List<Part>, planningRoles: List<String> = emptyList())
  ```
  When `planningRoles` is non-empty, create `planningRoleDir(branch, role)` and `planningSessionIdsDir(branch, role)` for each role. This keeps the method backward-compatible and explicit about when planning directories are needed.

## Major Concerns

None beyond the blockers above.

## Simplification Opportunities (PARETO)

- **Current:** The `Part` data class is introduced as a new type.
  **Assessment:** This is the RIGHT call. It avoids `Pair<String, List<String>>` and is explicit. No simplification needed -- this is already the simple approach.

- **Current:** No interface, no `OutFactory`, no `suspend`.
  **Assessment:** Correct YAGNI application. All three decisions are well-reasoned and properly justified in the plan.

## Minor Suggestions

1. **Test package alignment:** The existing test files use mixed package conventions -- some under `org.example`, newer ones under `com.glassthought`. The plan places tests at `com.glassthought.chainsaw.core.filestructure` which matches the source package. This is correct. No action needed, just noting the observation.

2. **Assertion style for paths:** The plan mentions `shouldEndWith` or `toString().endsWith(...)`. Kotest does not have a built-in `shouldEndWith` for `Path`. The cleaner pattern is:
   ```kotlin
   path.toString() shouldEndWith ".ai_out/branch/harness_private"
   ```
   using Kotest's `shouldEndWith` string matcher. This is a minor implementation detail -- the implementor will figure it out. No action needed in the plan.

3. **`PLAN_DIR` constant naming:** The plan defines `PLAN_DIR = "plan"` and `PLANNING_DIR = "planning"`. These are close enough to cause cognitive confusion. Consider `PLAN_SUBDIR` vs `PLANNING_DIR`, or just leave it -- the constants are internal and the method names (`planDir` vs `planningRoleDir`) provide sufficient disambiguation.

## Strengths

1. **Right level of abstraction:** Pure path computation separated from I/O (`ensureStructure`) is a clean design. The class is testable without mocking.

2. **Thorough test plan:** BDD structure with one-assert-per-test, covering constructor validation, all path methods, `ensureStructure` side effects, idempotency, and empty-parts edge case. This is exactly what is expected.

3. **Good YAGNI discipline:** No interface, no logging, no suspend -- all justified with clear rationale. The plan explicitly addresses why each was excluded.

4. **Companion object constants:** Named constants for all directory/file names eliminates magic strings and makes the class self-documenting.

5. **Phase structure:** Two clear phases (implement, test) with well-defined acceptance criteria. The verification command is ready to go.

6. **Design ticket alignment:** The plan correctly maps to the file structure section of the design ticket. Path resolution methods cover all the directory tree nodes (modulo the planning role gap flagged above).

## Verdict

- [ ] APPROVED
- [x] APPROVED WITH MINOR REVISIONS
- [ ] NEEDS REVISION
- [ ] REJECTED

**Rationale:** The plan is fundamentally sound and well-designed. The two blockers (missing planning role path methods and `ensureStructure` not covering planning directories) are additive gaps -- they require adding to the plan, not changing the architecture. Once these are addressed, the plan is ready for implementation.

**PLAN_ITERATION can be SKIPPED** if the implementor addresses the two blockers inline during implementation:

1. Add `planningPublicMd(branch, role)`, `planningPrivateMd(branch, role)`, `planningSessionIdsDir(branch, role)` methods.
2. Add `planningRoles: List<String> = emptyList()` parameter to `ensureStructure` and create planning directories when non-empty.
3. Add corresponding test cases for the new methods.

These additions are straightforward and do not change the architecture. The implementor can incorporate them without a full plan iteration cycle.
