---
closed_iso: 2026-03-10T00:50:45Z
id: nid_pk1sdnmxo777oy9qz4hl7l06g_E
title: "Role Catalog Loader"
status: closed
deps: [nid_r9on08uqjmumuc6wi2c53e8p9_E]
links: []
created_iso: 2026-03-09T23:07:06Z
status_updated_iso: 2026-03-10T00:50:45Z
type: feature
priority: 1
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [wave1, rolecatalog]
---

Implement auto-discovery and loading of agent role definitions from a directory of markdown files.

## Scope
- Create `RoleDefinition` data class: `name` (derived from filename), `description`, `descriptionLong` (optional), `filePath`
- Create `RoleCatalogLoader` interface + implementation: `load(dir: Path): List<RoleDefinition>`
- Scan `$CHAINSAW_AGENTS_DIR` for all `.md` files (every .md file is an eligible role)
- Parse YAML frontmatter to extract `description` (required) and `description_long` (optional)
- Role name derived from filename (e.g., `IMPLEMENTOR.md` → name = `IMPLEMENTOR`)
- Package: `com.glassthought.chainsaw.core.rolecatalog`

## Dependencies
- Reuses `YamlFrontmatterParser` utility from Ticket Parser ticket (nid_r9on08uqjmumuc6wi2c53e8p9_E)
- YAML parsing dependency already added by Ticket Parser

## Key Decisions
- Fail-fast if `description` field is missing from frontmatter
- Fail-fast if directory does not exist or is empty
- Role name = filename without extension (uppercase preserved)
- No filtering or opt-in flags — every .md file in the directory is a role

## Testing
- Unit tests with temp directories containing sample role .md files
- Test: valid role files parsed correctly
- Test: missing `description` field fails fast
- Test: directory with multiple .md files returns all roles
- Test: non-existent directory fails fast
- Test: `description_long` is optional and correctly parsed when present

## Files touched
- New files under `app/src/main/kotlin/com/glassthought/chainsaw/core/rolecatalog/`
- New files under `app/src/test/kotlin/com/glassthought/chainsaw/core/rolecatalog/`
- Does NOT touch `app/build.gradle.kts` (YAML dep already added by Ticket Parser)

## Reference
- See "Role Catalog — Auto-Discovered" section in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`

## Completion Criteria — Anchor Point
As part of closing this ticket:
1. Run `anchor_point.create` to generate a new AP for this component.
2. Add `ap.XXX.E` just below the `#### Role Catalog — Auto-Discovered` heading in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`.
3. Add `ref.ap.XXX.E` in the KDoc of the `RoleCatalogLoader` interface pointing back to that design ticket section.

## Resolution

All requirements implemented and tests passing.

### Files Created
- `app/src/main/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleDefinition.kt` — data class with `name`, `description`, `descriptionLong?`, `filePath`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleCatalogLoader.kt` — interface + `RoleCatalogLoaderImpl`
- `app/src/test/kotlin/com/glassthought/chainsaw/core/rolecatalog/RoleCatalogLoaderTest.kt` — 14 BDD tests
- Test resources in `app/src/test/resources/com/glassthought/chainsaw/core/rolecatalog/` (valid-catalog, missing-description, single-role, empty-catalog)

### Anchor Point
- Created `ap.iF4zXT5FUcqOzclp5JVHj.E` in design doc at "Role Catalog — Auto-Discovered" heading
- Added `ref.ap.iF4zXT5FUcqOzclp5JVHj.E` in `RoleCatalogLoader` interface KDoc

### Key Design Choices
- Mirrors `TicketParser` pattern (interface + companion factory + Impl)
- Reuses `YamlFrontmatterParser` — no YAML parsing duplication
- Flat directory scan (maxDepth=1) with `Files.walk().use {}`
- All file I/O wrapped in `withContext(Dispatchers.IO)`
- No new dependencies added
