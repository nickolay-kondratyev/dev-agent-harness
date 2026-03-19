# Exploration: FeedbackResolutionParser Implementation

**Date:** 2026-03-19  
**Scope:** Understand codebase structure, feedback loop patterns, and parser implementation requirements

---

## 1. Kotlin Source File Organization

### Package Structure Under `app/src/main/kotlin/com/glassthought/shepherd/`

```
core/
├── agent/                      # Agent spawning, tmux, facade
├── context/                    # Instruction assembly, context providers
├── data/                       # Core data types
├── filestructure/              # Path resolution (AiOutputStructure)
├── infra/                      # Infrastructure (logging, dispatchers, console)
├── initializer/                # Bootstrap, environment validation
├── state/                      # State models (Part, Phase, CurrentState)
├── supporting/                 # Utilities by domain
│   ├── git/                    # Git operations
│   └── ticket/                 # Ticket parsing (YamlFrontmatterParser, TicketParser)
├── time/                       # Clock abstraction
└── workflow/                   # Workflow definition parsing

usecase/
└── healthmonitoring/           # Health monitoring use cases

cli/
└── AppMain.kt                  # Entry point
```

### Key Pattern: `supporting/` Package for Utilities

The `supporting/` package is where stateless utility classes live:
- **Location:** `core/supporting/{domain}/`
- **Example:** `core/supporting/ticket/` contains `YamlFrontmatterParser.kt`, `TicketParser.kt`, `TicketData.kt`
- **Recommendation:** Feedback parsing should live in `core/supporting/feedback/`

---

## 2. Feedback Package Existence

**Finding:** No `com.glassthought.shepherd.feedback` package exists yet.

**Recommendation:** Create `core/supporting/feedback/` to match existing patterns:
```
core/supporting/feedback/
├── FeedbackResolutionParser.kt        # Main parser
├── FeedbackResolutionData.kt           # Parsed data model
└── FeedbackSeverity.kt                 # Enum for severity levels
```

---

## 3. Granular Feedback Loop Specification

Located at: `/doc/plan/granular-feedback-loop.md` (ap.5Y5s8gqykzGN1TVK5MZdS.E)

### Key Concepts Relevant to FeedbackResolutionParser

**File Format (R6):**
- Feedback files live in `__feedback/pending/`, `__feedback/addressed/`, `__feedback/rejected/`
- Filename format: `{severity}__{descriptive-slug}.md`
- Valid severity prefixes: `critical__`, `important__`, `optional__`

**Resolution Marker Format:**
Doers append a `## Resolution:` section to the feedback file after processing:

```markdown
## Resolution: ADDRESSED
Added null check in Parser.kt:45. Also added unit test in ParserTest.kt.
```

or:

```markdown
## Resolution: REJECTED
This null check is unnecessary — the upstream caller already validates...
```

or (optional items only):

```markdown
## Resolution: SKIPPED
Reviewed — this is a minor style preference that does not affect correctness...
```

**Parsing Requirements:**
- Scan for `## Resolution:` line (case-insensitive match on keyword)
- Extract keyword after `## Resolution:` (ADDRESSED, REJECTED, or SKIPPED)
- SKIPPED is valid only for `optional__` prefixed files
- Missing marker → `PartResult.AgentCrashed` immediately (no retry — ACK-confirmed delivery)
- Harness reads the marker to determine disposition (no validation of reasoning content — KISS principle)

### Files Present in Test Resources

Located at: `app/src/test/resources/com/glassthought/shepherd/core/context/fixtures/feedback/`

Test fixtures show the actual feedback file structure:

**Example 1: Addressed Feedback**
```markdown
# Race condition in session manager

**File(s):** `src/main/kotlin/SessionManager.kt`

The session manager has a TOCTOU race condition.

---

## Resolution: ADDRESSED
Fixed by adding mutex around session lookup.
```

**Example 2: Rejected Feedback**
```markdown
# Use CoroutineScope instead of GlobalScope

**File(s):** `src/main/kotlin/Launcher.kt`

GlobalScope should be replaced with structured concurrency.

---

## Resolution: REJECTED
WHY-NOT: GlobalScope is intentional here — this is the top-level launcher...
```

**Example 3: Pending Feedback (No Resolution Yet)**
```markdown
# Missing null check in Parser

**File(s):** `src/main/kotlin/Parser.kt`

The parser does not handle null input, which will cause NPE.

---

## Resolution:
```

**Directory Structure:**
- `pending/` - Files awaiting doer action (no ## Resolution yet, or SKIPPED for optional)
- `addressed/` - Files with `## Resolution: ADDRESSED`
- `rejected/` - Files with `## Resolution: REJECTED` accepted by reviewer

---

## 4. Existing Parser/Utility Patterns

### YamlFrontmatterParser Example (Best Pattern to Follow)

Located at: `core/supporting/ticket/YamlFrontmatterParser.kt`

**Key Characteristics:**
```kotlin
object YamlFrontmatterParser {
    fun parse(content: String): FrontmatterParseResult {
        // Stateless — no constructor, no state
        // Pure function: String → FrontmatterParseResult
        // Comprehensive error handling with require()
        // Clear, focused documentation
    }
}

data class FrontmatterParseResult(
    val yamlFields: Map<String, String>,
    val body: String,
)
```

**Lessons:**
1. **Stateless object:** Use `object` keyword for singleton utilities
2. **Focused return type:** Custom `data class` with exactly what's needed
3. **Inline error handling:** Use `require()` for validation with clear messages
4. **No I/O in parser:** Let callers handle file reading (done in `TicketParser`)
5. **Minimal dependencies:** Only what's truly needed (snakeyaml for YAML)

### TicketParser Example (Wrapper with I/O & Logging)

Located at: `core/supporting/ticket/TicketParser.kt`

**Key Characteristics:**
```kotlin
fun interface TicketParser {
    suspend fun parse(path: Path): TicketData
    
    companion object {
        fun standard(outFactory: OutFactory): TicketParser = TicketParserImpl(outFactory)
    }
}

class TicketParserImpl(
    outFactory: OutFactory,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider.standard(),
) : TicketParser {
    // Handles I/O, logging, and delegation to pure parser
    // Constructor injection only (no DI framework)
    // Uses Out for structured logging
}
```

**Lessons:**
1. **Interface + Implementation separation:** Interface as contract, Impl for details
2. **Factory method pattern:** `TicketParser.standard()` for creation
3. **Suspend function:** For async I/O operations
4. **Constructor injection:** All dependencies passed to constructor
5. **Structured logging:** Uses `Out` with `Val` for structured values

---

## 5. BDD/Kotest Testing Patterns

Located at: `app/src/test/kotlin/com/glassthought/shepherd/core/ticket/`

### YamlFrontmatterParserTest Example

**Structure:**
```kotlin
class YamlFrontmatterParserTest : AsgardDescribeSpec({
    describe("GIVEN markdown content with valid YAML frontmatter") {
        val content = """...""".trimIndent()
        
        describe("WHEN parse is called") {
            it("THEN yamlFields contains the id") {
                val result = YamlFrontmatterParser.parse(content)
                result.yamlFields["id"] shouldBe "test-id-001"
            }
            
            it("THEN body contains the expected text") {
                val result = YamlFrontmatterParser.parse(content)
                result.body shouldContain "This is the body text."
            }
        }
    }
    
    describe("GIVEN markdown content without leading --- delimiter") {
        // ... error case tests
        it("THEN throws IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                YamlFrontmatterParser.parse(content)
            }
        }
    }
})
```

**Key Patterns:**
1. **Extends `AsgardDescribeSpec`:** Base test class with `outFactory` inherited
2. **GIVEN/WHEN/THEN structure:** Nested `describe` blocks for context
3. **One assertion per `it` block:** Each test focuses on single behavior
4. **Self-documenting:** Test name + nested structure = full context
5. **Data-driven:** Separate describe blocks for each scenario (valid, missing, malformed)
6. **Throw testing:** Use `shouldThrow<>` for exception cases

### Test Resources Location

- **Path:** `app/src/test/resources/com/glassthought/shepherd/core/ticket/`
- **Used by:** `TicketParserTest` with `Path.of(TicketParserTest::class.java.getResource(...).toURI())`
- **Pattern:** Test files named semantically: `valid-ticket.md`, `missing-id.md`, `extra-fields-ticket.md`

---

## 6. Existing Feedback Directory Infrastructure

### AiOutputStructure Already Has Feedback Support

Located at: `core/filestructure/AiOutputStructure.kt` (ap.BXQlLDTec7cVVOrzXWfR7.E)

**Path Methods Already Exist:**
```kotlin
fun feedbackDir(partName: String): Path
fun feedbackPendingDir(partName: String): Path
fun feedbackAddressedDir(partName: String): Path
fun feedbackRejectedDir(partName: String): Path
```

**Directory Creation Already Implemented:**
```kotlin
fun ensureStructure(parts: List<Part>) {
    // ...
    Phase.EXECUTION -> {
        Files.createDirectories(feedbackPendingDir(part.name))
        Files.createDirectories(feedbackAddressedDir(part.name))
        Files.createDirectories(feedbackRejectedDir(part.name))
        // ...
    }
}
```

**Key Finding:** The infrastructure is already in place! The FeedbackResolutionParser just needs to read from these directories.

---

## 7. Design Recommendations for FeedbackResolutionParser

### Should Create

**File 1: `core/supporting/feedback/FeedbackResolutionData.kt`**
```kotlin
package com.glassthought.shepherd.core.supporting.feedback

sealed class FeedbackResolution {
    data class Addressed(val reason: String) : FeedbackResolution()
    data class Rejected(val justification: String) : FeedbackResolution()
    data class Skipped(val reason: String) : FeedbackResolution()
    object Missing : FeedbackResolution()
}

data class FeedbackResolutionParseResult(
    val resolution: FeedbackResolution,
    val fullContent: String,
)
```

**File 2: `core/supporting/feedback/FeedbackSeverity.kt`**
```kotlin
package com.glassthought.shepherd.core.supporting.feedback

enum class FeedbackSeverity {
    CRITICAL,
    IMPORTANT,
    OPTIONAL,
}

fun extractSeverityFromFilename(filename: String): FeedbackSeverity? {
    return when {
        filename.startsWith("critical__") -> FeedbackSeverity.CRITICAL
        filename.startsWith("important__") -> FeedbackSeverity.IMPORTANT
        filename.startsWith("optional__") -> FeedbackSeverity.OPTIONAL
        else -> null
    }
}
```

**File 3: `core/supporting/feedback/FeedbackResolutionParser.kt`**

Key method signature:
```kotlin
object FeedbackResolutionParser {
    fun parseResolution(content: String): FeedbackResolutionParseResult {
        // Scan for "## Resolution:" line
        // Extract keyword (ADDRESSED, REJECTED, SKIPPED, or missing)
        // Extract rest of resolution section as reason/justification
        // Return FeedbackResolutionParseResult
    }
}
```

### Following Established Patterns

1. **No I/O in parser:** Parser only handles string → data structure
2. **Stateless object:** Use `object` keyword
3. **Custom sealed class for resolution:** Models the four states (Addressed, Rejected, Skipped, Missing)
4. **Comprehensive error handling:** Use `require()` to catch invalid states early
5. **Test resources:** Create `app/src/test/resources/com/glassthought/shepherd/core/feedback/` with fixture files
6. **BDD tests:** Use `AsgardDescribeSpec` with GIVEN/WHEN/THEN structure
7. **One assertion per test:** Focus on specific parse outcomes

---

## 8. Test Strategy

### Unit Tests (In FeedbackResolutionParserTest)

**Scenario 1: ADDRESSED resolution**
```
GIVEN feedback file with "## Resolution: ADDRESSED" section
WHEN parse is called
THEN resolution is FeedbackResolution.Addressed with extracted reason
```

**Scenario 2: REJECTED resolution**
```
GIVEN feedback file with "## Resolution: REJECTED" section
WHEN parse is called
THEN resolution is FeedbackResolution.Rejected with extracted justification
```

**Scenario 3: SKIPPED resolution (for optional items)**
```
GIVEN feedback file with "## Resolution: SKIPPED" section
WHEN parse is called
THEN resolution is FeedbackResolution.Skipped with extracted reason
```

**Scenario 4: Missing resolution marker**
```
GIVEN feedback file with "## Resolution:" but no keyword after
WHEN parse is called
THEN resolution is FeedbackResolution.Missing
```

**Scenario 5: No resolution section at all**
```
GIVEN feedback file with no "## Resolution:" section
WHEN parse is called
THEN resolution is FeedbackResolution.Missing
```

**Scenario 6: Case insensitivity**
```
GIVEN feedback file with "## RESOLUTION: ADDRESSED" (uppercase)
WHEN parse is called
THEN resolution is correctly parsed
```

**Scenario 7: Resolution marker in body text (not in ## section)**
```
GIVEN feedback file where "Resolution: ADDRESSED" appears in body without ## prefix
WHEN parse is called
THEN resolution is FeedbackResolution.Missing (not in header)
```

### Test Resources

Location: `app/src/test/resources/com/glassthought/shepherd/core/feedback/`

Files needed:
- `addressed-with-reason.md` - Valid ADDRESSED with implementation details
- `rejected-with-justification.md` - Valid REJECTED with reasoning
- `skipped-with-reason.md` - Valid SKIPPED (optional item)
- `missing-resolution.md` - No ## Resolution section
- `empty-resolution.md` - "## Resolution:" with nothing after
- `malformed-resolution.md` - "## Resolution: INVALID_KEYWORD"

---

## 9. Integration Points (When Parser Is Used)

**Where FeedbackResolutionParser Will Be Called:**

1. **PartExecutorImpl.PROCESS_FEEDBACK_ITEM()** (granular-feedback-loop.md, R3)
   - After doer signals `done`
   - Read resolution marker from feedback file
   - Move file to appropriate directory based on resolution

2. **RejectionNegotiationUseCase** (if rejection negotiation calls it)
   - Parse resolution after doer re-instruction in rejection negotiation loop

3. **Part Completion Guard** (R8)
   - Validate no pending critical/important before completion
   - Could use parser to categorize files

---

## 10. Summary

### What Exists
- `core/supporting/ticket/` pattern for stateless parsers
- `AiOutputStructure` with full feedback directory support
- Test resources with real feedback file examples
- BDD testing framework (`AsgardDescribeSpec`)
- Logging infrastructure (`Out`, `Val`, `ValType`)

### What Needs to Be Created
- `core/supporting/feedback/FeedbackResolutionParser.kt` (main parser)
- `core/supporting/feedback/FeedbackResolutionData.kt` (sealed classes for outcomes)
- `core/supporting/feedback/FeedbackSeverity.kt` (severity enum and extractor)
- Comprehensive test suite following BDD pattern
- Test resources (example feedback files)

### Implementation Readiness
- **High:** Clear patterns from existing parsers (YamlFrontmatterParser, TicketParser)
- **High:** Test infrastructure in place (AsgardDescribeSpec, Kotest)
- **High:** Feedback directory structure already integrated into codebase
- **Medium:** Spec is detailed but focus on minimal parsing (KISS principle)

---

## References

| Item | Location | AP Reference |
|------|----------|---------------|
| Granular feedback loop spec | `doc/plan/granular-feedback-loop.md` | ap.5Y5s8gqykzGN1TVK5MZdS.E |
| AI output directory schema | `doc/schema/ai-out-directory.md` | ap.BXQlLDTec7cVVOrzXWfR7.E |
| AiOutputStructure (feedback paths) | `core/filestructure/AiOutputStructure.kt` | ap.BXQlLDTec7cVVOrzXWfR7.E |
| PartExecutor (where parser is used) | Inner loop processing (R3) | ap.fFr7GUmCYQEV5SJi8p6AS.E |
| YamlFrontmatterParser (pattern) | `core/supporting/ticket/YamlFrontmatterParser.kt` | - |
| TicketParser (I/O wrapper pattern) | `core/supporting/ticket/TicketParser.kt` | - |
| Test patterns | `core/ticket/YamlFrontmatterParserTest.kt` | - |
| Test patterns | `core/ticket/TicketParserTest.kt` | - |
| Test fixtures | `app/src/test/resources/.../feedback/` | - |

