# Exploration Summary

## Spec
- Full directory tree spec: `doc/schema/ai-out-directory.md` (ref.ap.BXQlLDTec7cVVOrzXWfR7.E)

## Package Location
- Target package: `com.glassthought.shepherd.core.filestructure` — does NOT exist yet, needs to be created
- Previous implementation was in `com.glassthought.chainsaw.core.filestructure` (deleted in commit 072fa28)
- Main source: `app/src/main/kotlin/com/glassthought/shepherd/core/filestructure/`
- Test source: `app/src/test/kotlin/com/glassthought/shepherd/core/filestructure/`

## Test Patterns
- Tests extend `AsgardDescribeSpec` from `com.asgard.testTools.describe_spec`
- BDD style: `describe("GIVEN ...") { describe("WHEN ...") { it("THEN ...") { ... } } }`
- One logical assertion per `it` block
- Import: `io.kotest.matchers.shouldBe`
- Example pattern from `ProcessResultTest`, `TicketParserTest`, `ContextForAgentProviderAssemblyTest`

## Key Design Points from Ticket
- Pure path resolution (no I/O)
- Separate methods per phase (planning vs execution) for compile-time safety
- `__feedback/` only at execution part level, NOT planning
- Use "pending" per spec (NOT "unaddressed" from ProtocolVocabulary — tracked separately)
- Branch slashes create nested directories via `Path.resolve()` — this is correct behavior

## ProtocolVocabulary
- Located at `app/src/main/kotlin/com/glassthought/shepherd/core/context/ProtocolVocabulary.kt`
- Has `FeedbackStatus.UNADDRESSED = "unaddressed"` — known naming inconsistency, NOT our concern (separate ticket)
