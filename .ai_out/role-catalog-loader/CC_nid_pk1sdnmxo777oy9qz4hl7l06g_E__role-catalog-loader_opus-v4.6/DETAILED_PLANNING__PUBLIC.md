# DETAILED_PLANNING -- Role Catalog Loader

## 1. Problem Understanding

**Goal:** Create a component that auto-discovers agent role definitions from a directory of `.md` files. Each file represents a role; its YAML frontmatter contains a required `description` and optional `description_long`. The loader returns a list of structured `RoleDefinition` objects.

**Constraints:**
- Mirror the TicketParser pattern exactly (interface + companion factory + Impl in one file, data class in separate file)
- Reuse `YamlFrontmatterParser` for frontmatter parsing
- Fail-fast on missing `description`, non-existent directory, empty directory
- No new dependencies needed
- Package: `com.glassthought.chainsaw.core.rolecatalog`

**Assumptions:**
- Only top-level `.md` files matter (no recursive subdirectory walk) -- the design doc says "every Markdown file in `$CHAINSAW_AGENTS_DIR`", implying flat directory
- The YAML frontmatter key is `description_long` (snake_case, matching the design doc example), mapped to Kotlin property `descriptionLong` (camelCase)
- Directory emptiness means "no `.md` files found" (not literally zero files of any type)

## 2. High-Level Architecture

```
RoleDefinition              (data class -- separate file)
    |
RoleCatalogLoader           (interface -- companion factory)
    |
RoleCatalogLoaderImpl       (implementation -- same file as interface)
    |
    +-- YamlFrontmatterParser  (reused from ticket package)
```

**Data flow:**
1. Caller provides a `Path` (directory)
2. `RoleCatalogLoaderImpl` validates the directory exists and is a directory
3. Walks the directory for `*.md` files
4. For each file: reads content -> `YamlFrontmatterParser.parse()` -> extracts `description` (required) and `description_long` (optional) -> constructs `RoleDefinition`
5. If no `.md` files found, fails fast
6. Returns `List<RoleDefinition>`

## 3. Implementation Phases

### Phase 1: Create `RoleDefinition` data class

**Goal:** Define the data model for a parsed role.

**File:** `app/src/main/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleDefinition.kt`

**Key details:**
- Properties: `name: String`, `description: String`, `descriptionLong: String?`, `filePath: Path`
- `name` = filename without extension (case preserved)
- `description` = from frontmatter field `description`
- `descriptionLong` = from frontmatter field `description_long` (nullable)
- `filePath` = absolute path to the `.md` file
- KDoc: document each property, note that `name` is derived from filename

**Verification:** Compiles.

---

### Phase 2: Create `RoleCatalogLoader` interface + `RoleCatalogLoaderImpl`

**Goal:** Implement the loader that scans a directory and returns role definitions.

**File:** `app/src/main/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleCatalogLoader.kt`

**Key details:**

**Interface `RoleCatalogLoader`:**
- Single method: `suspend fun load(dir: Path): List<RoleDefinition>`
- KDoc: describe purpose, `@throws IllegalArgumentException` for invalid dir / missing description
- Add `ref.ap.XXX.E` reference back to design doc (AP will be created at ticket close)
- Companion factory: `fun standard(outFactory: OutFactory): RoleCatalogLoader = RoleCatalogLoaderImpl(outFactory)`

**Class `RoleCatalogLoaderImpl`:**
- Constructor: `(outFactory: OutFactory)`
- `private val out = outFactory.getOutForClass(RoleCatalogLoaderImpl::class)`
- `override suspend fun load(dir: Path): List<RoleDefinition>`:
  1. Log `loading_role_catalog` with the directory path
  2. Validate: `require(dir exists and is a directory)` with clear message
  3. Use `withContext(Dispatchers.IO)` for file system operations
  4. Walk directory with `Files.walk(dir, 1)` (max depth 1 = flat) inside `.use {}` for resource safety
  5. Filter to regular files with `.md` extension
  6. Collect to list (inside the `.use {}` block -- stream must be consumed before closing)
  7. Validate: `require(mdFiles.isNotEmpty())` -- fail-fast on empty catalog
  8. For each `.md` file: read content, parse with `YamlFrontmatterParser.parse()`, extract fields, build `RoleDefinition`
  9. Extract `description` from `yamlFields["description"]` -- throw `IllegalArgumentException` if missing, include filename in error message
  10. Extract `description_long` from `yamlFields["description_long"]` -- nullable, no error if missing
  11. Derive `name` from `file.nameWithoutExtension` (Kotlin extension on Path via `kotlin.io.path`)
  12. Log `role_catalog_loaded` with count
  13. Return the list

**Important implementation notes:**
- `Files.walk(dir, 1)` with maxDepth=1 avoids recursion but includes the dir itself -- filter with `Files.isRegularFile()`
- Use `kotlin.io.path.extension` and `kotlin.io.path.nameWithoutExtension` for Path extensions
- Use `kotlin.io.path.readText()` for file reading (inside `withContext(Dispatchers.IO)`)

**Verification:** Compiles, unit tests pass (Phase 3).

---

### Phase 3: Create test resources

**Goal:** Provide fixture `.md` files for unit tests.

**Directory:** `app/src/test/resources/com/glassthought/chainsaw/core/rolecatalog/`

Note: Unlike TicketParser which loads individual resource files, RoleCatalogLoader loads a **directory**. Create subdirectories under the resources path, each representing a test scenario.

**Test resource directories and files:**

**3a. `valid-catalog/` -- happy path with multiple roles**

`valid-catalog/IMPLEMENTOR.md`:
```markdown
---
description: "Implements features based on detailed plans"
description_long: "Full-stack implementation agent that writes production code, tests, and documentation"
---

Implementation role body content.
```

`valid-catalog/REVIEWER.md`:
```markdown
---
description: "Reviews code for correctness and style"
---

Reviewer role body content.
```

**3b. `missing-description/` -- role file lacking required `description`**

`missing-description/BAD_ROLE.md`:
```markdown
---
description_long: "Has long desc but no short desc"
---

Some body.
```

**3c. `single-role/` -- directory with exactly one role**

`single-role/PLANNER.md`:
```markdown
---
description: "Creates execution plans from tickets"
---

Planner body.
```

**3d. `empty-catalog/` -- directory with no `.md` files**

Create the directory but put NO `.md` files in it. Place a `.gitkeep` file so the empty directory is tracked by git.

`empty-catalog/.gitkeep`: (empty file)

**Verification:** Files exist on disk, accessible via `Class.getResource()`.

---

### Phase 4: Create `RoleCatalogLoaderTest`

**Goal:** Comprehensive BDD tests covering all acceptance criteria.

**File:** `app/src/test/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleCatalogLoaderTest.kt`

**Pattern:** Mirror `TicketParserTest` exactly -- extend `AsgardDescribeSpec`, use `resourcePath()` helper, one assert per `it` block.

**Resource path helper:** Since we load directories (not individual files), the helper should resolve directory paths:
```kotlin
fun resourceDir(name: String): Path =
    Path.of(
        RoleCatalogLoaderTest::class.java
            .getResource("/com/glassthought/chainsaw/core/rolecatalog/$name")!!
            .toURI()
    )
```

**Test cases (BDD structure):**

```
GIVEN a valid catalog directory with multiple roles
  WHEN load is called
    THEN returns a list with 2 roles
    THEN contains a role named IMPLEMENTOR
    THEN contains a role named REVIEWER
    THEN IMPLEMENTOR has correct description
    THEN IMPLEMENTOR has descriptionLong populated
    THEN REVIEWER has description populated
    THEN REVIEWER has null descriptionLong
    THEN each role has a filePath ending with its filename

GIVEN a catalog directory with a role missing description
  WHEN load is called
    THEN throws IllegalArgumentException
    THEN error message contains the filename

GIVEN an empty catalog directory (no .md files)
  WHEN load is called
    THEN throws IllegalArgumentException

GIVEN a non-existent directory path
  WHEN load is called
    THEN throws IllegalArgumentException

GIVEN a catalog directory with a single role
  WHEN load is called
    THEN returns a list with exactly 1 role
    THEN the role name matches the filename without extension
```

**Verification:** All tests pass with `./gradlew :app:test`.

---

### Phase 5: Anchor Point + Design Doc Cross-Reference

**Goal:** Create stable cross-references between the design doc and the implementation.

**Steps:**
1. Run `anchor_point.create` to generate a new AP
2. Add the AP below `#### Role Catalog -- Auto-Discovered` heading in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`
3. Add `ref.ap.XXX.E` in the KDoc of `RoleCatalogLoader` interface

**Verification:** `anchor_point.find_anchor_point_and_references` finds both definition and reference.

---

## 4. Technical Considerations

### File Walking Safety
- `Files.walk()` returns a `Stream<Path>` that holds OS file handles. MUST use `.use {}` (Kotlin's equivalent of try-with-resources) to avoid resource leaks.
- Use `maxDepth = 1` in `Files.walk(dir, 1)` since the design doc implies a flat directory structure.
- Filter with `Files.isRegularFile(it)` to exclude the root directory entry and any subdirectories.

### Error Messages
- Include the filename in error messages for missing `description` so developers can quickly identify which role file is broken: `"Role file [FILENAME] is missing required frontmatter field: description"`
- Include the directory path in directory validation errors.

### Thread Safety
- `YamlFrontmatterParser` is a stateless `object` -- safe to call from coroutines.
- `Files.walk()` is called inside `withContext(Dispatchers.IO)` -- correct dispatcher for blocking I/O.

### Performance
- Not a concern at this scale. Role catalogs will have at most dozens of files.

## 5. Testing Strategy

### Unit Tests
All tests use pre-built resource directories (Phase 3). No temp directories needed since we are testing parsing logic, not file system creation.

### Key Scenarios
| Scenario | Expected Result |
|---|---|
| Valid catalog with 2 roles | Returns 2 `RoleDefinition` objects with correct fields |
| Role with `description_long` | `descriptionLong` is populated |
| Role without `description_long` | `descriptionLong` is null |
| Role missing `description` | `IllegalArgumentException` thrown |
| Empty directory (no `.md` files) | `IllegalArgumentException` thrown |
| Non-existent directory | `IllegalArgumentException` thrown |
| Single role | Returns list of size 1, name matches filename |
| Filename casing preserved | `IMPLEMENTOR.md` -> name = `IMPLEMENTOR` |

### Edge Cases Considered but NOT Tested (KISS)
- Nested subdirectories with `.md` files -- out of scope (maxDepth=1 excludes them)
- Non-`.md` files in directory -- filtered out by extension check, implicitly tested by `valid-catalog/` which only has `.md` files
- Symlinks -- not a current concern

## 6. Files Created (Summary)

| File | Type |
|---|---|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleDefinition.kt` | Data class |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleCatalogLoader.kt` | Interface + Impl |
| `app/src/test/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleCatalogLoaderTest.kt` | Test |
| `app/src/test/resources/com/glassthought/chainsaw/core/rolecatalog/valid-catalog/IMPLEMENTOR.md` | Test resource |
| `app/src/test/resources/com/glassthought/chainsaw/core/rolecatalog/valid-catalog/REVIEWER.md` | Test resource |
| `app/src/test/resources/com/glassthought/chainsaw/core/rolecatalog/missing-description/BAD_ROLE.md` | Test resource |
| `app/src/test/resources/com/glassthought/chainsaw/core/rolecatalog/single-role/PLANNER.md` | Test resource |
| `app/src/test/resources/com/glassthought/chainsaw/core/rolecatalog/empty-catalog/.gitkeep` | Test resource |

## 7. Open Questions / Decisions Needed

None. The ticket is well-specified and all design decisions are clear from the exploration and clarification phases.
