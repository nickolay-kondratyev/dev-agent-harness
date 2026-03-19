# Implementation: InstructionSection Sealed Class + InstructionPlanAssembler

## What Was Done

Implemented the `InstructionSection` sealed class hierarchy (7 shared subtypes) and the `InstructionPlanAssembler` rendering engine, as specified in the "Internal Design: Data-Driven Assembly" section of `doc/core/ContextForAgentProvider.md`.

### Files Created

1. **`app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt`** (ap.YkR8mNv3pLwQ2xJtF5dZs.E)
   - Sealed class with `render(request: AgentInstructionRequest): String?` method
   - 7 subtypes: `RoleDefinition`, `PrivateMd`, `PartContext`, `Ticket`, `OutputPathSection`, `WritingGuidelines`, `CallbackHelp`

2. **`app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionPlanAssembler.kt`** (ap.Xk7mPvR3nLwQ9tJsF2dYh.E)
   - Walks plan list, calls `render()` on each section, filters nulls, joins with `"\n\n---\n\n"` separator
   - Writes to `request.outputDir/instructions.md`

3. **`app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt`**
   - 14 test cases covering all 7 subtypes in isolation

4. **`app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionPlanAssemblerTest.kt`**
   - 5 test scenarios: multi-section ordering, null-skipping, empty plan, OutputPathSection, PartContext null for planner

## Design Decisions

1. **`OutputPathSection(label: String, path: Path)`** — Takes concrete label + path (not a lambda). The plan is constructed per-request anyway, so the caller provides the concrete path. This avoids lambdas in data classes (bad for equality/toString) and fragile string-matching.

2. **`data object` for stateless subtypes, `data class` for parameterized ones** — RoleDefinition, PrivateMd, PartContext, Ticket, WritingGuidelines are `data object`; OutputPathSection and CallbackHelp are `data class`.

3. **`render()` is non-suspend** — File reads (PrivateMd, RoleDefinition) are synchronous. The assembler wraps the entire walk in `withContext(dispatcherProvider.io())`.

4. **PartContext dispatches on sealed type** — Returns null for PlannerRequest/PlanReviewerRequest (no execution context). Uses exhaustive `when` — no `else` branch.

5. **Did NOT modify `ContextForAgentProviderImpl.kt`** — The wiring ticket (nid_zseecydaikj0f2i2l14nwcfax_E) handles replacing the buildList approach with assembleFromPlan calls.

## Tests

All tests pass: `./gradlew :app:test` — PASS
