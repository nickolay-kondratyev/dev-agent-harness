# EXPLORATION_PUBLIC — Role Catalog Loader

## Key Findings

### 1. YamlFrontmatterParser (Reusable)
- **Path:** `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/YamlFrontmatterParser.kt`
- Stateless object; returns `FrontmatterParseResult(yamlFields: Map<String, String>, body: String)`
- Uses snakeyaml with custom `StringOnlyResolver` to preserve all scalars as strings
- Throws `IllegalArgumentException` if frontmatter delimiters missing or YAML invalid

### 2. TicketParser Pattern (To Mirror)
- **Interface + companion factory + Impl class** in single file
- `TicketParser.standard(outFactory): TicketParser` factory method
- Uses `withContext(Dispatchers.IO)` for file reads
- Structured logging with `Out`, `Val`, `ValType`
- Validates required fields, throws `IllegalArgumentException`

### 3. Package Structure
- New package: `com.glassthought.chainsaw.core.rolecatalog`
- Source: `app/src/main/kotlin/com/glassthought/chainsaw/core/rolecatalog/`
- Tests: `app/src/test/kotlin/com/glassthought/chainsaw/core/rolecatalog/`
- Test resources: `app/src/test/resources/com/glassthought/chainsaw/core/rolecatalog/`

### 4. Test Patterns
- Extend `AsgardDescribeSpec` (inherits `outFactory`)
- BDD: `describe("GIVEN ...") { describe("WHEN ...") { it("THEN ...") } }`
- One assert per `it` block
- Resource loading: `Class.getResource("/path/to/resource")!!.toURI()` → `Path.of()`
- Exception: `shouldThrow<IllegalArgumentException>`

### 5. Dependencies — All Present
- `org.yaml:snakeyaml:2.2` — already added by Ticket Parser
- `com.asgard:asgardCore:1.0.0` — Out/OutFactory
- `com.asgard:asgardTestTools:1.0.0` — AsgardDescribeSpec
- kotest assertions + runner

### 6. Design Reference
- `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md` (lines 367-381)
- "Role Catalog — Auto-Discovered" section specifies: every .md file in CHAINSAW_AGENTS_DIR is eligible, description required, description_long optional

### 7. File Walking
- Use `Files.walk()` with `.use {}` for resource safety
- `path.nameWithoutExtension` for role name
- `path.extension` for filtering `.md` files
