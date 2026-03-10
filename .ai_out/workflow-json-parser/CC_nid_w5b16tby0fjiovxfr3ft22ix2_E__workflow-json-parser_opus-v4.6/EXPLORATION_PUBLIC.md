# Exploration: Workflow JSON Parser

## Build Configuration
- `app/build.gradle.kts` already has: guava, asgardCore, coroutines, okhttp, org.json, snakeyaml
- Jackson + Kotlin module NOT yet added — needed for this ticket
- No version catalog entries for Jackson; will add directly

## Existing Parser Pattern (TicketParser)
- Interface with `companion object { fun standard(outFactory): TicketParser }` factory
- Implementation class `TicketParserImpl(outFactory: OutFactory)`
- Suspend function for I/O: `suspend fun parse(path: Path): TicketData`
- Uses `withContext(Dispatchers.IO)` for file reads
- Structured logging: `out.debug("message") { listOf(Val(...)) }`
- Fail-fast with `IllegalArgumentException` for missing fields
- Data class for result (`TicketData`)

## Test Patterns
- Extend `AsgardDescribeSpec`
- `outFactory` inherited from AsgardDescribeSpec
- BDD: `describe("GIVEN...") { describe("WHEN...") { it("THEN...") { } } }`
- One assertion per `it` block
- Test resources loaded via `SomeTest::class.java.getResource(...)` under matching package path
- Kotest matchers: `shouldBe`, `shouldThrow`, `shouldContain`

## Design Doc (Workflow Definition — Kotlin + JSON)
Located at: `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md` line 243

### Key Schema
- **Parts schema** shared between static (straightforward) and dynamic (planner-generated)
- A `Part` has: name, description, phases (list of {role}), iteration ({max})
- **Straightforward**: `{ "name": "...", "parts": [...] }`
- **With-planning**: `{ "name": "...", "planningPhases": [...], "planningIteration": {max}, "executionPhasesFrom": "plan.json" }`
- No sealed class/enum for workflow types — optional fields differentiate

### Workflow Resolution
- `--workflow <name>` → loads `./config/workflows/<name>.json`
- Fail-fast if not found

## Package Structure
Existing under `core/`:
- ticket/ (TicketParser, TicketData, YamlFrontmatterParser)
- filestructure/ (AiOutputStructure)
- wingman/ (Wingman, ClaudeCodeWingman)
- directLLMApi/ constants/ initializer/ processRunner/ tmux/

New package: `com.glassthought.chainsaw.core.workflow`

## No config/workflows/ directory yet
Needs to be created with sample JSON files.

## Exception Pattern
Codebase currently uses `IllegalArgumentException`/`IllegalStateException` — no custom AsgardBaseException subclasses yet.
