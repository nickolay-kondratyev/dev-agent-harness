# PLANNER__PRIVATE -- Role Catalog Loader

## Planning Notes

### Pattern Fidelity
The plan mirrors TicketParser exactly:
- Interface + companion factory (`standard(outFactory)`) + Impl class in one file
- Data class in separate file
- Same logging patterns (Out/Val/ValType)
- Same dispatcher usage (withContext(Dispatchers.IO))
- Same test structure (AsgardDescribeSpec, resourcePath helper, one assert per it)

### Key Difference from TicketParser
TicketParser parses a single file. RoleCatalogLoader scans a directory and parses multiple files.
This means:
- Test resources are organized as subdirectories (each subdirectory = a test scenario) rather than individual files
- The `resourcePath` helper returns directory paths, not file paths
- There's an additional validation layer (directory existence + non-empty)

### YAML Key Mapping
The design doc uses `description_long` (snake_case) in YAML frontmatter. The Kotlin data class uses `descriptionLong` (camelCase). This is a straightforward mapping from `yamlFields["description_long"]` to the `descriptionLong` constructor parameter. No serialization framework involved -- it's a manual extraction from the Map.

### Empty Directory Testing
Git does not track empty directories. The `.gitkeep` file ensures the `empty-catalog/` directory is committed. The loader only looks for `.md` files, so `.gitkeep` won't interfere.

### Scope Decision: Flat vs Recursive
The plan specifies `Files.walk(dir, 1)` (flat, maxDepth=1) based on the design doc's language "every Markdown file in $CHAINSAW_AGENTS_DIR". If recursive scanning is ever needed, changing `1` to `Int.MAX_VALUE` is trivial -- but we don't over-engineer for that now.

### No New Dependencies Confirmed
- snakeyaml: already in build.gradle.kts (from TicketParser)
- asgardCore (Out/OutFactory): already present
- asgardTestTools (AsgardDescribeSpec): already present
- kotest: already present
- kotlin.io.path extensions: stdlib, no dependency needed

### Risk: None
This is a simple, well-bounded feature with clear patterns to follow.
