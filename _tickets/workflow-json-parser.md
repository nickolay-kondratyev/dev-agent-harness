---
closed_iso: 2026-03-10T00:53:05Z
id: nid_w5b16tby0fjiovxfr3ft22ix2_E
title: "Workflow JSON Parser"
status: closed
deps: [nid_r9on08uqjmumuc6wi2c53e8p9_E]
links: []
created_iso: 2026-03-09T23:07:25Z
status_updated_iso: 2026-03-10T00:53:05Z
type: feature
priority: 1
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [wave1, workflow, parser]
---

Implement parsing of workflow JSON definitions into a domain model.

## Scope
- Create domain model:
  - `WorkflowDefinition`: top-level, contains name + list of parts (for straightforward) or planning config (for with-planning)
  - `Part`: name, description, list of phases, iteration config
  - `Phase`: role name (string matching a role catalog entry)
  - `IterationConfig`: max iterations count
  - `PlanningConfig` (for with-planning): planning phases + planning iteration + `executionPhasesFrom` path
- Create `WorkflowParser` interface + implementation: `parse(path: Path): WorkflowDefinition`
- Add **Jackson + Kotlin module** dependency to `app/build.gradle.kts`
- Workflow files live in `./config/workflows/<name>.json`
- Resolution: `--workflow <name>` → loads `./config/workflows/<name>.json`
- Package: `com.glassthought.shepherd.core.workflow`

## Dependencies
- Ticket Parser (nid_r9on08uqjmumuc6wi2c53e8p9_E) must be merged first to avoid build.gradle.kts merge conflicts (both add dependencies)

## Key Decisions
- Jackson + Kotlin module for deserialization (not kotlinx.serialization) — consistent with design doc
- Same `Part` schema used for both static workflows and planner-generated plan.json
- Fail-fast if workflow file not found
- Fail-fast if JSON is malformed or missing required fields
- Sealed class or enum NOT used for workflow types — just optional fields (planningPhases is null for straightforward)

## Testing
- Unit tests with sample workflow JSON (as test resources or inline strings)
- Test: parse straightforward workflow with static parts
- Test: parse with-planning workflow with planning config
- Test: missing required fields fail fast
- Test: file not found fails fast
- Create sample workflow files: `config/workflows/straightforward.json`, `config/workflows/with-planning.json`

## Files touched
- `app/build.gradle.kts` (add Jackson + kotlin-module dependency)
- New files under `app/src/main/kotlin/com/glassthought/shepherd/core/workflow/`
- New files under `app/src/test/kotlin/com/glassthought/shepherd/core/workflow/`
- New files under `config/workflows/` (sample workflow definitions)

## Reference
- See "Workflow Definition — Kotlin + JSON" section in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`

## Completion Criteria — Anchor Point
As part of closing this ticket:
1. Run `anchor_point.create` to generate a new AP for this component.
2. Add `ap.XXX.E` just below the `## Workflow Definition — Kotlin + JSON` heading in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`.
3. Add `ref.ap.XXX.E` in the KDoc of the `WorkflowParser` interface pointing back to that design ticket section.

## Resolution

Implemented `com.glassthought.shepherd.core.workflow` package with:
- `WorkflowDefinition` data class (ap.MyWV0mG6ZU8XaQOyo14l4.E): `name`, `parts?`, `planningPhases?`, `planningIteration?`, `executionPhasesFrom?`
- `Part` data class: `name`, `description`, `phases: List<Phase>`, `iteration: IterationConfig`
- `Phase` data class: `role: String`
- `IterationConfig` data class: `max: Int`
- `WorkflowParser` interface (ap.U5oDohccLN3tugPzK9TJa.E) + `WorkflowParserImpl`: `suspend fun parse(path: Path): WorkflowDefinition`
- Jackson + Kotlin module (`jackson-databind:2.17.2`, `jackson-module-kotlin:2.17.2`) added to `app/build.gradle.kts`
- Production workflow files: `config/workflows/straightforward.json`, `config/workflows/with-planning.json`
- Anchor point `ap.Wya4gZPW6RPpJHdtoJqZO.E` created, added below `## Workflow Definition — Kotlin + JSON` in design doc
- `ref.ap.Wya4gZPW6RPpJHdtoJqZO.E` in `WorkflowParser` KDoc pointing to design doc
- 32 BDD unit tests (11 straightforward + 7 with-planning + 5 multi-part + 9 error paths) all passing
- Post-deserialization validation: blank name, neither-nor, both, empty phases, incomplete planning config

