# Exploration: Git Branch Manager

## TicketData Class
**Location:** `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/TicketData.kt`
```kotlin
data class TicketData(
    val id: String,
    val title: String,
    val status: String?,
    val description: String,
    val additionalFields: Map<String, String> = emptyMap(),
)
```

## Package Structure
`com.glassthought.chainsaw.core/` has: `directLLMApi/`, `filestructure/`, `initializer/`, `processRunner/`, `ticket/`, `tmux/`, `wingman/`
- New code goes in `com.glassthought.chainsaw.core.git/`

## ProcessRunner
- From asgardCore library (not in this repo). Used via `implementation("com.asgard:asgardCore:1.0.0")`
- `InteractiveProcessRunner` exists in `core/processRunner/` as an example of process execution

## Test Patterns
- Extend `AsgardDescribeSpec` (from asgardTestTools)
- BDD: nested `describe("GIVEN ...")` / `describe("WHEN ...")` / `it("THEN ...")`
- One assertion per `it()` block
- Integration tests gated with `.config(isIntegTestEnabled())`
- `outFactory` inherited from `AsgardDescribeSpec`

## Logging
- Constructor-inject `OutFactory`, get logger via `outFactory.getOutForClass(Class::class)`
- `out.info("snake_case_message", Val(value, ValType.TYPE))`
- All Out methods are suspend

## Git Branch Naming (from reference ticket)
Format: `{TICKET_ID}__{slugified_title}__try-{N}`
- Delimiter: `__` (double underscore)
- V1: truncate long slugs (no LLM compression)

## Build Dependencies
All needed deps already in `app/build.gradle.kts` — no changes needed.
