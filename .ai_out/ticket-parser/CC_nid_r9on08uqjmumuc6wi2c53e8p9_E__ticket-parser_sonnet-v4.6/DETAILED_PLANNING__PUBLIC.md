# Detailed Implementation Plan: TicketParser

**Ticket**: `nid_r9on08uqjmumuc6wi2c53e8p9_E` — Ticket Parser
**Branch**: `CC_nid_r9on08uqjmumuc6wi2c53e8p9_E__ticket-parser_sonnet-v4.6`

---

## 1. Problem Understanding

Chainsaw always operates on a ticket (a markdown file with YAML frontmatter). Currently there is no
typed Kotlin representation of that ticket. This feature introduces:

1. A typed `TicketData` data class to hold parsed ticket content.
2. A reusable `YamlFrontmatterParser` utility that splits any markdown file into YAML map + body text.
3. A `TicketParser` interface + `TicketParserImpl` that reads a file from disk and returns `TicketData`.
4. A snakeyaml dependency in `app/build.gradle.kts`.

The `YamlFrontmatterParser` will be reused later by the Role Catalog Loader (role `.md` files use the
same YAML frontmatter pattern).

**Constraints:**
- `id` and `title` are required — fail-fast (`IllegalArgumentException`) if absent.
- `description` = markdown body (everything after the closing `---` delimiter).
- All other frontmatter fields land in `additionalFields: Map<String, Any>`.
- Suspend I/O (`parse(path: Path)` is `suspend`).
- No DI framework — constructor injection only.
- Logging via `Out`/`OutFactory`; never `println`.

---

## 2. High-Level Architecture

```
TicketParser (interface)
    └── TicketParserImpl
            ├── reads file bytes  (kotlinx.coroutines / withContext(IO))
            └── delegates to YamlFrontmatterParser
                    ├── splits content into YAML block + body string
                    └── returns FrontmatterParseResult(yamlFields: Map<String,Any>, body: String)

TicketData (data class)
    id: String
    title: String
    status: String?
    description: String          ← body text
    additionalFields: Map<String, Any>   ← remaining frontmatter fields
```

`YamlFrontmatterParser` is a Kotlin `object` (stateless utility). It does not touch the filesystem.
Its single responsibility: parse a String and return a `FrontmatterParseResult`.

`TicketParserImpl` takes `outFactory: OutFactory` in its constructor (consistent with all other impls).
It reads the file, calls `YamlFrontmatterParser.parse()`, then assembles `TicketData` with
fail-fast validation.

---

## 3. Files to Create / Modify

### Files to modify

| File | Change |
|---|---|
| `app/build.gradle.kts` | Add snakeyaml dependency |

### Files to create — main sources

| File | Contents |
|---|---|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/TicketData.kt` | `TicketData` data class |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/YamlFrontmatterParser.kt` | `FrontmatterParseResult` data class + `YamlFrontmatterParser` object |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/TicketParser.kt` | `TicketParser` interface + `TicketParserImpl` class |

### Files to create — test sources

| File | Contents |
|---|---|
| `app/src/test/kotlin/com/glassthought/chainsaw/core/ticket/YamlFrontmatterParserTest.kt` | BDD unit tests for `YamlFrontmatterParser` |
| `app/src/test/kotlin/com/glassthought/chainsaw/core/ticket/TicketParserTest.kt` | BDD unit tests for `TicketParserImpl` |

### Files to create — test resources

| File | Purpose |
|---|---|
| `app/src/test/resources/com/glassthought/chainsaw/core/ticket/valid-ticket.md` | Full valid ticket with id, title, status, extra fields, and body |
| `app/src/test/resources/com/glassthought/chainsaw/core/ticket/missing-id.md` | Valid frontmatter but no `id` field |
| `app/src/test/resources/com/glassthought/chainsaw/core/ticket/missing-title.md` | Valid frontmatter but no `title` field |
| `app/src/test/resources/com/glassthought/chainsaw/core/ticket/extra-fields-ticket.md` | Has id + title + several extra frontmatter fields |

### Files to modify — AP cross-linking (Completion Criteria)

| File | Change |
|---|---|
| `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md` | Add `ap.XXX.E` on the line immediately after `## CLI Entry Point` heading |

---

## 4. Implementation Phases

### Phase 1: Add snakeyaml dependency

**Goal**: Gradle resolves the YAML library before writing any Kotlin code.

**File**: `app/build.gradle.kts`

**Key step**: Add inside the `dependencies {}` block:
```kotlin
// snakeyaml: YAML parsing for ticket and role markdown frontmatter
implementation("org.yaml:snakeyaml:2.2")
```

**Verification**: `./gradlew :app:dependencies | grep snakeyaml` resolves without error.

---

### Phase 2: Create `TicketData` data class

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/TicketData.kt`

**Package**: `com.glassthought.chainsaw.core.ticket`

**Structure**:

```kotlin
/**
 * Parsed representation of a Chainsaw ticket file.
 *
 * A ticket is a markdown file with YAML frontmatter.
 * [id] and [title] are required (parsed from frontmatter).
 * [description] is the markdown body (content after the closing --- delimiter).
 * [additionalFields] holds all other frontmatter fields for extensibility.
 */
data class TicketData(
    val id: String,
    val title: String,
    val status: String?,
    val description: String,
    val additionalFields: Map<String, Any>,
)
```

**Notes**:
- `status` is nullable — not all markdown files that `YamlFrontmatterParser` is used on will have it.
- `additionalFields` must NOT include `id`, `title`, or `status` (they are promoted to typed fields).
- Immutable data class.

---

### Phase 3: Create `YamlFrontmatterParser`

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/YamlFrontmatterParser.kt`

**Package**: `com.glassthought.chainsaw.core.ticket`

**Contains two declarations** (co-located in the same file):

#### 3a. `FrontmatterParseResult` data class

```kotlin
/**
 * Result of splitting a markdown document into its YAML frontmatter and body.
 *
 * @param yamlFields parsed YAML key→value map; keys are String, values are Any (snakeyaml output).
 * @param body the markdown body text after the closing --- delimiter (trimmed of leading newline).
 */
data class FrontmatterParseResult(
    val yamlFields: Map<String, Any>,
    val body: String,
)
```

#### 3b. `YamlFrontmatterParser` object

**Parsing algorithm** (the only non-trivial logic):

```
1. Verify content starts with "---\n" or "---\r\n".
   If not → throw IllegalArgumentException("Content does not start with YAML frontmatter delimiter")

2. Find the closing "---" delimiter:
   - Search for "\n---\n" or "\n---\r\n" or "\n---" at end-of-string after the opening delimiter.
   - If not found → throw IllegalArgumentException("Missing closing YAML frontmatter delimiter")

3. Extract the YAML block: text between the two --- delimiters.

4. Parse YAML block using snakeyaml Yaml().load<Map<String,Any>>(yamlBlock)
   - If result is null or not a Map → throw IllegalArgumentException("Frontmatter is not a YAML mapping")

5. Extract body: text after the closing --- line (strip leading newline).

6. Return FrontmatterParseResult(yamlFields = yamlMap, body = body)
```

**Snakeyaml usage note**: Use `Yaml()` from `org.yaml.snakeyaml`. The `load()` call returns `Any?`.
Cast safely, throw `IllegalArgumentException` if not a `Map<*, *>`.

**Signature**:
```kotlin
object YamlFrontmatterParser {
    /**
     * Parses a markdown string with YAML frontmatter into fields and body.
     *
     * @param content Full markdown file content as a string.
     * @throws IllegalArgumentException if frontmatter delimiters are missing or YAML is invalid.
     */
    fun parse(content: String): FrontmatterParseResult
}
```

**No logging here** — pure data transformation, no side effects. Stateless utility.

---

### Phase 4: Create `TicketParser` interface + `TicketParserImpl`

**File**: `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/TicketParser.kt`

**Package**: `com.glassthought.chainsaw.core.ticket`

#### 4a. `TicketParser` interface

```kotlin
/**
 * Reads a ticket markdown file and returns its structured [TicketData].
 *
 * A ticket is a markdown file with YAML frontmatter.
 * Required frontmatter fields: `id`, `title`.
 *
 * See design doc: ref.ap.XXX.E (CLI Entry Point section — ticket as required input)
 */
interface TicketParser {
    /**
     * Parses the ticket file at [path] and returns [TicketData].
     *
     * @throws IllegalArgumentException if [path] does not contain valid frontmatter,
     *   or if required fields `id` or `title` are missing.
     */
    suspend fun parse(path: Path): TicketData

    companion object {
        fun standard(outFactory: OutFactory): TicketParser = TicketParserImpl(outFactory)
    }
}
```

#### 4b. `TicketParserImpl` class

**Constructor**: `class TicketParserImpl(outFactory: OutFactory) : TicketParser`

**Implementation steps in `parse(path)`**:
1. Log at DEBUG level: reading ticket at path.
2. Read file content: `withContext(Dispatchers.IO) { path.readText() }` — use kotlinx.coroutines IO dispatcher.
3. Call `YamlFrontmatterParser.parse(content)` to get `FrontmatterParseResult`.
4. Extract `id`: `yamlFields["id"]?.toString() ?: throw IllegalArgumentException("Ticket is missing required field: id")`
5. Extract `title`: same pattern for `title`.
6. Extract `status`: `yamlFields["status"]?.toString()` (nullable, no throw).
7. Build `additionalFields`: `yamlFields.filterKeys { it !in setOf("id", "title", "status") }.mapValues { it.value }` (cast to `Map<String, Any>` — snakeyaml already gives `Any` values).
8. Log at INFO: ticket parsed successfully, include id and title as structured `Val`.
9. Return `TicketData(id, title, status, description = result.body, additionalFields)`.

**Logging**:
- DEBUG log before file read: message `"reading_ticket"`, Val for path.
- INFO log after success: message `"ticket_parsed"`, `Val(id, ValType.STRING_USER_AGNOSTIC)`, `Val(title, ValType.STRING_USER_AGNOSTIC)`.
- Do NOT log and throw — let exceptions bubble up.

**ValType guidance** for path logging: use `ValType.FILE_PATH_STRING` (confirmed present in `ValType.kt`, `UserSpecificity.USER_SPECIFIC` — correct since a ticket file path is user-specific data).

---

### Phase 5: Create test resource files

All under `app/src/test/resources/com/glassthought/chainsaw/core/ticket/`.

#### `valid-ticket.md`
```markdown
---
id: nid_test_valid_ticket_001
title: "My Test Ticket"
status: open
priority: 2
tags: [kotlin, test]
---

This is the ticket description body.

## Details

More detail text here.
```

#### `missing-id.md`
```markdown
---
title: "Ticket Without ID"
status: open
---

Body content here.
```

#### `missing-title.md`
```markdown
---
id: nid_test_no_title_001
status: open
---

Body content here.
```

#### `extra-fields-ticket.md`
```markdown
---
id: nid_test_extra_fields_001
title: "Extra Fields Ticket"
status: in_progress
type: feature
priority: 1
assignee: engineer-one
tags: [wave1, backend]
created_iso: 2026-03-09T23:05:48Z
---

Body with extra fields.
```

---

### Phase 6: Write `YamlFrontmatterParserTest`

**File**: `app/src/test/kotlin/com/glassthought/chainsaw/core/ticket/YamlFrontmatterParserTest.kt`

**Class**: `class YamlFrontmatterParserTest : AsgardDescribeSpec({ ... })`

**Test cases** (one `it` per assertion):

```
describe("GIVEN markdown content with valid YAML frontmatter") {
    describe("WHEN parse is called") {
        it("THEN yamlFields contains the id")
        it("THEN yamlFields contains the title")
        it("THEN body contains the expected text")
        it("THEN body does NOT contain the frontmatter delimiters")
    }
}

describe("GIVEN markdown content without leading --- delimiter") {
    describe("WHEN parse is called") {
        it("THEN throws IllegalArgumentException")
    }
}

describe("GIVEN markdown content with opening --- but no closing ---") {
    describe("WHEN parse is called") {
        it("THEN throws IllegalArgumentException")
    }
}

describe("GIVEN markdown content with extra frontmatter fields") {
    describe("WHEN parse is called") {
        it("THEN yamlFields contains all extra fields")
    }
}

describe("GIVEN markdown content where body has multiple paragraphs") {
    describe("WHEN parse is called") {
        it("THEN body contains all paragraphs")
    }
}
```

**No file I/O** — tests pass raw strings to `YamlFrontmatterParser.parse()`. No test resources needed here; inline strings are clearer and faster.

---

### Phase 7: Write `TicketParserTest`

**File**: `app/src/test/kotlin/com/glassthought/chainsaw/core/ticket/TicketParserTest.kt`

**Class**: `class TicketParserTest : AsgardDescribeSpec({ ... })`

**Test resource loading helper** (defined as a private function in the test class body):
```kotlin
fun resourcePath(name: String): Path =
    Path.of(
        TicketParserTest::class.java
            .getResource("/com/glassthought/chainsaw/core/ticket/$name")!!
            .toURI()
    )
```

**Test cases** (one `it` per assertion):

```
describe("GIVEN a valid ticket file") {
    describe("WHEN parse is called") {
        it("THEN id is parsed correctly")
        it("THEN title is parsed correctly")
        it("THEN status is parsed correctly")
        it("THEN description contains the body text")
        it("THEN description does NOT contain --- delimiters")
    }
}

describe("GIVEN a ticket file missing the id field") {
    describe("WHEN parse is called") {
        it("THEN throws IllegalArgumentException")
    }
}

describe("GIVEN a ticket file missing the title field") {
    describe("WHEN parse is called") {
        it("THEN throws IllegalArgumentException")
    }
}

describe("GIVEN a ticket file with extra frontmatter fields") {
    describe("WHEN parse is called") {
        it("THEN additionalFields contains 'type'")
        it("THEN additionalFields contains 'priority'")
        it("THEN additionalFields contains 'assignee'")
        it("THEN additionalFields does NOT contain 'id'")
        it("THEN additionalFields does NOT contain 'title'")
        it("THEN additionalFields does NOT contain 'status'")
    }
}
```

**Suspend context note**: `describe` block bodies are NOT suspend contexts. The `parse()` call
must be inside an `it` block (which IS a suspend context in Kotest's `DescribeSpec`).

For the "throws" tests, use `io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> { parser.parse(path) }`.

---

### Phase 8: Anchor Point and Cross-linking (Completion Criteria)

**This phase must be done after all code is written and tests pass.**

**Steps**:

1. Run `anchor_point.create` in the shell to generate a new AP (e.g., `ap.NEWUUID.E`).

2. In `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`, find the `## CLI Entry Point` heading and add the AP identifier on the line immediately after it:
   ```markdown
   ## CLI Entry Point

   ap.NEWUUID.E
   ```
   (The exact format to match existing AP placements in the codebase.)

3. In the KDoc of `TicketParser` interface (in `TicketParser.kt`), add the reference:
   ```kotlin
   /**
    * ...existing docs...
    *
    * See design doc: ref.ap.NEWUUID.E (CLI Entry Point section — ticket as required input)
    */
   ```

---

## 5. Implementation Order (Sequential)

1. Add snakeyaml to `app/build.gradle.kts` (Phase 1)
2. Create `TicketData.kt` (Phase 2)
3. Create `YamlFrontmatterParser.kt` (Phase 3)
4. Create test resource files (Phase 5) — needed before tests are written
5. Write `YamlFrontmatterParserTest.kt` (Phase 6)
6. Run `YamlFrontmatterParserTest` — verify green
7. Create `TicketParser.kt` (Phase 4)
8. Write `TicketParserTest.kt` (Phase 7)
9. Run all tests — verify green
10. Anchor point + cross-linking (Phase 8)
11. Close ticket, write change log entry

---

## 6. Technical Considerations

### snakeyaml Type Handling

snakeyaml's `Yaml().load()` returns `Any?`. The loaded type depends on YAML content:
- YAML strings → `String`
- YAML integers → `Int` or `Long`
- YAML lists → `List<Any>`
- YAML booleans → `Boolean`

`additionalFields: Map<String, Any>` correctly accepts this heterogeneous output. No special coercion needed.

When extracting `id`, `title`, `status`: call `.toString()` — safe even if snakeyaml parses them as non-String (unusual but defensive).

### File Reading — Coroutines IO Dispatcher

Use `withContext(Dispatchers.IO) { path.readText() }` inside the `suspend fun parse()`. This is the standard pattern for blocking file I/O inside a coroutine. Import `kotlinx.coroutines.Dispatchers` and `kotlinx.coroutines.withContext`.

### Frontmatter Delimiter Detection

The YAML frontmatter spec requires `---` on its own line. The content may use `\n` (Unix) or `\r\n` (Windows) line endings. The split logic should handle both. The simplest approach:

```kotlin
private const val DELIMITER = "---"

fun parse(content: String): FrontmatterParseResult {
    val lines = content.lines()
    require(lines.firstOrNull()?.trimEnd() == DELIMITER) {
        "Content does not start with YAML frontmatter delimiter (---)"
    }
    val closingIndex = lines.drop(1).indexOfFirst { it.trimEnd() == DELIMITER }
    require(closingIndex >= 0) {
        "Missing closing YAML frontmatter delimiter (---)"
    }
    // closingIndex is relative to lines.drop(1), so actual index is closingIndex + 1
    val yamlLines = lines.subList(1, closingIndex + 1)
    val bodyLines = lines.drop(closingIndex + 2)  // everything after the closing ---
    val yamlBlock = yamlLines.joinToString("\n")
    val body = bodyLines.joinToString("\n").trimStart('\n')
    ...
}
```

This approach is line-based, handles both line-ending styles, and is easy to reason about.

### Error Messages

Error messages must be clear to the caller — they will appear as the message in
`IllegalArgumentException`. Include which field is missing:
- `"Ticket is missing required field: id"`
- `"Ticket is missing required field: title"`

---

## 7. Testing Strategy

### Unit Test Scope

| Class | Test Type | I/O |
|---|---|---|
| `YamlFrontmatterParser` | Pure unit (no I/O, no coroutines) | None — inline strings |
| `TicketParserImpl` | Unit with test resource files | File read via test resources (classpath) |

### Key Scenarios

| Scenario | Class Under Test | Expected Outcome |
|---|---|---|
| Valid full ticket | `TicketParserImpl` | All fields populated, body in description |
| Missing `id` | `TicketParserImpl` | `IllegalArgumentException` |
| Missing `title` | `TicketParserImpl` | `IllegalArgumentException` |
| Extra frontmatter fields | `TicketParserImpl` | Fields in `additionalFields`, id/title/status NOT in `additionalFields` |
| Valid frontmatter string | `YamlFrontmatterParser` | yamlFields populated, body correct |
| No opening `---` | `YamlFrontmatterParser` | `IllegalArgumentException` |
| No closing `---` | `YamlFrontmatterParser` | `IllegalArgumentException` |
| Body has multiple paragraphs | `YamlFrontmatterParser` | Full body preserved |

### Edge Cases to Include in Tests

- `YamlFrontmatterParser`: body that itself contains `---` (must NOT confuse with frontmatter delimiter — the algorithm only looks for the first closing `---` after the opening one).
- `TicketParserImpl`: description body trimmed of leading blank line that follows the closing `---`.

---

## 8. Open Questions / Already Resolved

All design questions are resolved per the CLARIFICATION document:

| Question | Decision |
|---|---|
| What is `description`? | Markdown body (content after closing `---`) |
| `parse()` suspend or not? | `suspend fun` — file I/O |
| `YamlFrontmatterParser` as object? | Yes — stateless utility, Kotlin `object` |
| `additionalFields` type? | `Map<String, Any>` |
| Fail behavior on missing required fields? | `throw IllegalArgumentException` |
| YAML library? | snakeyaml `2.2` |

No remaining open questions. Implementation can proceed directly.
