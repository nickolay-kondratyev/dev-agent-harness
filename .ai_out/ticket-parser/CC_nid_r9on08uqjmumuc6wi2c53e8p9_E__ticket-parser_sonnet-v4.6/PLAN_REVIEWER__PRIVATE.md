# Plan Reviewer Private Notes

## Review Date
2026-03-09

## Files Read
- DETAILED_PLANNING__PUBLIC.md
- EXPLORATION_PUBLIC.md
- CLARIFICATION__PUBLIC.md
- _tickets/ticket-parser.md
- app/build.gradle.kts
- app/src/main/kotlin/com/glassthought/chainsaw/core/tmux/TmuxCommunicator.kt (pattern reference)
- app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiTest.kt (test pattern reference)
- submodules/thorg-root/source/libraries/kotlin-mp/asgardCore/src/commonMain/kotlin/com/asgard/core/data/value/ValType.kt

## Key Findings

### ValType for file path
- `ValType.FILE_PATH_STRING` exists — USER_SPECIFIC (ticket path is user-specific, correct)
- Plan says "use ValType.FILE_PATH if it exists... otherwise ValType.STRING_USER_AGNOSTIC"
- `FILE_PATH_STRING` is the right one for `path.toString()` logging
- Plan guidance is a bit vague — a minor inline note added to review

### snakeyaml thread safety
- `Yaml()` default constructor is NOT thread-safe for concurrent use
- Since `YamlFrontmatterParser` is a Kotlin `object`, the `Yaml()` instance must NOT be shared as a field
- Creating `Yaml()` per call is correct (and what the plan implies with `Yaml().load()`)
- Plan correctly specifies `Yaml().load()` inline which avoids the thread-safety issue
- This is fine as-is — just worth noting

### Anchor Point cross-linking target
- Plan specifies modifying `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`
- This file is referenced from the ticket spec — correct
- Plan says "add the AP identifier on the line immediately after it" — format is fine

### TicketData.additionalFields type
- `Map<String, Any>` — correct. snakeyaml returns Any for values.
- The filter step for additionalFields is clean.

### Test structure
- Tests follow BDD GIVEN/WHEN/THEN with one assertion per `it` — correct
- `describe` bodies are NOT suspend — plan correctly notes `parse()` must be inside `it` block
- `shouldThrow<IllegalArgumentException>` usage — correct Kotest idiom

### Edge case in plan: body containing `---`
- Plan explicitly calls this out in Section 7 edge cases. Good.
- The line-based algorithm handles this correctly since it stops at first `---` after opening.

### Missing edge case: empty body
- Ticket files could have no body text after closing `---`
- Plan does not explicitly address this, but the algorithm handles it (bodyLines is empty → body = "")
- Not a blocker, but worth a minor test case addition

### `resourcePath` helper function
- Plan defines it as `fun resourcePath` — it should be a local function inside the spec body block or
  a private companion object function. Since AsgardDescribeSpec is a class, it can be a local fun inside
  the spec body lambda. Fine.

### Phase ordering anomaly
- Plan Section 5 says: Phase 6 (tests for YamlFrontmatterParser) runs BEFORE Phase 4 (TicketParser implementation).
  This is actually correct — YamlFrontmatterParser tests don't depend on TicketParser.
  Phase numbering in section 5 is slightly confusing (steps 3-4 skip from "Phase 5" to "Phase 6" before "Phase 4"),
  but the actual ordering is sound.

### IllegalArgumentException vs AsgardBaseException
- CLAUDE.md says "Extend AsgardBaseException hierarchy for structured exceptions"
- The plan uses `IllegalArgumentException` for fail-fast validation
- However, ticket requirements specifically say "fail-fast (IllegalArgumentException)" and this is an input
  validation, not a domain exception. The existing codebase also uses `IllegalStateException` directly
  in TmuxCommunicatorImpl without extending AsgardBaseException.
- This is a MINOR concern — not a blocker for this scope, but worth flagging.

## Verdict
APPROVED — plan is solid, well-structured, and aligns with project standards.
One minor concern about exception hierarchy, one minor guidance improvement for ValType.
Both are non-blocking inline adjustments.
