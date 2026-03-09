# Exploration: Ticket Parser Feature

**Feature**: `ticket-parser`
**Branch**: `CC_nid_r9on08uqjmumuc6wi2c53e8p9_E__ticket-parser_sonnet-v4.6`

---

## Project Structure

- Main source: `app/src/main/kotlin/com/glassthought/chainsaw/`
- Test source: `app/src/test/kotlin/com/glassthought/chainsaw/`
- Core domain: `com.glassthought.chainsaw.core.*`
- Ticket to implement: `com.glassthought.chainsaw.core.ticket`

## Existing Dependencies (app/build.gradle.kts)

- Kotlin JVM 2.2.20, Java 21
- `com.asgard:asgardCore:1.0.0` — Out/OutFactory logging, ProcessRunner, AsgardBaseException
- `com.asgard:asgardTestTools:1.0.0` — AsgardDescribeSpec, Kotest base
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2`
- `com.squareup.okhttp3:okhttp:4.12.0`
- `org.json:json:20240303`
- Kotest 5.9.1 (`kotest-assertions-core`, `kotest-runner-junit5`)
- **NO YAML library yet** — need to add snakeyaml

## Existing Patterns

### Data Classes
```kotlin
data class TmuxSessionName(val sessionName: String)
```
- Immutable, self-documenting field names
- No Pair/Triple

### Interface + Implementation
```kotlin
interface TmuxCommunicator { suspend fun sendKeys(...) }
class TmuxCommunicatorImpl(...) : TmuxCommunicator { ... }
```
- Interface defines contract, Impl or idiomatic name for implementation
- Constructor injection only

### Logging
```kotlin
out.info("sending_keys_to_tmux_session",
    Val(session.sessionName, ValType.STRING_USER_AGNOSTIC))
```
- Always use `Out`/`OutFactory`, never `println`
- Structured `Val(value, ValType.*)`, no embedded strings

### Exceptions
- Fail-fast: throw `IllegalStateException` or extend `AsgardBaseException`
- Do NOT log and throw; let bubble up

### Anchor Points
```kotlin
// ref.ap.UUID.E  in KDoc
@AnchorPoint("ap.UUID.E")  // on class
```

## Test Patterns

```kotlin
class MyTest : AsgardDescribeSpec({
    describe("GIVEN ...") {
        describe("WHEN ...") {
            it("THEN ...") {
                result shouldBe expected
            }
        }
    }
})
```
- Extend `AsgardDescribeSpec`
- ONE assertion per `it` block
- `outFactory` inherited (do NOT construct NoOpOutFactory)
- Integration tests gated with `.config(isIntegTestEnabled())`

## Ticket Format (from existing tickets)

```markdown
---
id: nid_xxx_E
title: "Title Here"
status: open
deps: []
links: []
created_iso: 2026-03-09T23:05:48Z
status_updated_iso: 2026-03-09T23:05:48Z
type: feature
priority: 1
assignee: someone
tags: [tag1, tag2]
---

# Markdown body content
```

Required fields: `id`, `title`
Optional but common: `status`, `deps`, `links`, `created_iso`, `status_updated_iso`, `type`, `priority`, `assignee`, `tags`

## Design Decisions to Make

1. **YAML library**: snakeyaml (`org.yaml:snakeyaml:2.2`) is the ticket recommendation — stable, no extra setup
2. **TicketData.description**: The ticket spec says include `description` field; in practice, some tickets don't have a `description` in frontmatter — the body of the markdown IS the description. Recommend: parse body as `description` field.
3. **`parse()` suspend vs not**: Given the file I/O nature, `suspend fun` is appropriate

## Recommended File Structure

```
app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/
├── TicketData.kt
├── TicketParser.kt                 # interface + TicketParserImpl
└── YamlFrontmatterParser.kt       # object with parse(content: String): FrontmatterParseResult

app/src/test/kotlin/com/glassthought/chainsaw/core/ticket/
├── TicketParserTest.kt
└── YamlFrontmatterParserTest.kt

app/src/test/resources/com/glassthought/chainsaw/core/ticket/
├── valid-ticket.md
├── missing-id.md
├── missing-title.md
└── extra-fields-ticket.md
```
