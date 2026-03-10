# Implementation Report -- Role Catalog Loader

## Summary

Implemented the Role Catalog Loader feature according to the approved detailed plan. All five phases completed successfully. All tests pass (both new and existing).

## Files Created

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleDefinition.kt` | Data class for parsed role definition |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleCatalogLoader.kt` | Interface + companion factory + RoleCatalogLoaderImpl |
| `app/src/test/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleCatalogLoaderTest.kt` | BDD tests (14 test cases) |
| `app/src/test/resources/com/glassthought/chainsaw/core/rolecatalog/valid-catalog/IMPLEMENTOR.md` | Test fixture: role with description + description_long |
| `app/src/test/resources/com/glassthought/chainsaw/core/rolecatalog/valid-catalog/REVIEWER.md` | Test fixture: role with description only |
| `app/src/test/resources/com/glassthought/chainsaw/core/rolecatalog/missing-description/BAD_ROLE.md` | Test fixture: role missing required description |
| `app/src/test/resources/com/glassthought/chainsaw/core/rolecatalog/single-role/PLANNER.md` | Test fixture: single role |
| `app/src/test/resources/com/glassthought/chainsaw/core/rolecatalog/empty-catalog/.gitkeep` | Test fixture: empty directory |

## Files Modified

| File | Change |
|------|--------|
| `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md` | Added `ap.iF4zXT5FUcqOzclp5JVHj.E` anchor point at "Role Catalog -- Auto-Discovered" heading |

## Design Decisions

### Pattern: Mirror TicketParser
- Interface + companion factory (`RoleCatalogLoader.standard(outFactory)`) + `RoleCatalogLoaderImpl` in single file
- Data class (`RoleDefinition`) in separate file
- Reuses `YamlFrontmatterParser` for YAML frontmatter extraction (no duplication)

### Fail-Fast Behavior
Three failure modes, all throwing `IllegalArgumentException` with descriptive messages:
1. **Non-existent/invalid directory**: message includes the directory path
2. **Empty catalog (no .md files)**: message includes "No .md files found" + directory path
3. **Missing description field**: message includes the filename of the offending role

### File Walking
- `Files.walk(dir, 1)` with `maxDepth=1` for flat (non-recursive) scan
- `.use {}` block for resource safety (OS file handles)
- Filters: `Files.isRegularFile(it) && it.extension == "md"`

### Coroutines / IO
- `withContext(Dispatchers.IO)` wraps all blocking file system operations
- `YamlFrontmatterParser` is stateless (`object`), safe for concurrent use

### Logging
- `ValType.FILE_PATH_STRING` for directory path in debug log
- `ValType.STRING_USER_AGNOSTIC` for role count in info log (following existing codebase pattern for string-valued counts)

## Reviewer Feedback Incorporated

1. **Error message content assertions** -- Added `THEN error message indicates no .md files found` for empty-directory case and `THEN error message contains the directory path` for non-existent directory case (from plan review adjustments 1).
2. **Clarifying comment on `resourceDir`** -- Added KDoc comment explaining that the helper works because Gradle runs tests against exploded resources, not JARs (from plan review adjustment 2).

## Test Coverage

14 test cases covering all specified scenarios:

| GIVEN | Test Cases |
|-------|------------|
| Valid catalog with multiple roles | 8 tests: list size, contains IMPLEMENTOR, contains REVIEWER, descriptions correct, descriptionLong populated/null, filePath correctness |
| Role missing description | 2 tests: throws exception, error message contains filename |
| Empty catalog directory | 2 tests: throws exception, error message indicates no .md files |
| Non-existent directory | 2 tests: throws exception, error message contains directory path |
| Single role directory | 2 tests: list size 1, name matches filename |

## Anchor Points

- `ap.iF4zXT5FUcqOzclp5JVHj.E` -- Defined in design doc at "Role Catalog -- Auto-Discovered" heading
- `ref.ap.iF4zXT5FUcqOzclp5JVHj.E` -- Referenced from `RoleCatalogLoader` interface KDoc

## No Dependencies Added

No new dependencies were required. `snakeyaml:2.2` was already present (added by Ticket Parser).
