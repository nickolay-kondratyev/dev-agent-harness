---
id: nid_r9on08uqjmumuc6wi2c53e8p9_E
title: "Ticket Parser"
status: in_progress
deps: []
links: []
created_iso: 2026-03-09T23:05:48Z
status_updated_iso: 2026-03-09T23:26:29Z
type: feature
priority: 1
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [wave1, parser]
---

Implement a TicketParser that reads markdown files with YAML frontmatter and returns structured TicketData.

## Scope
- Create `TicketData` data class with fields: `id`, `title`, `status`, `description` (and extensible for other frontmatter fields)
- Create `TicketParser` interface + implementation: `parse(path: Path): TicketData`
- Create a reusable `YamlFrontmatterParser` utility that splits markdown into frontmatter (YAML) + body
- Add YAML parsing dependency to `app/build.gradle.kts` (e.g., snakeyaml)
- Package: `com.glassthought.chainsaw.core.ticket`

## Key Decisions
- Ticket is a markdown file with YAML frontmatter containing at minimum `id` and `title` fields
- Fail-fast if required fields (`id`, `title`) are missing
- The `YamlFrontmatterParser` will be reused by Role Catalog Loader for parsing role .md files

## Testing
- Unit tests with sample ticket markdown files (as test resources)
- Test: valid ticket parses correctly
- Test: missing `id` field fails fast
- Test: missing `title` field fails fast
- Test: extra frontmatter fields are preserved/accessible

## Files touched
- `app/build.gradle.kts` (add YAML dependency)
- New files under `app/src/main/kotlin/com/glassthought/chainsaw/core/ticket/`
- New files under `app/src/test/kotlin/com/glassthought/chainsaw/core/ticket/`

## Reference
- See ticket design: `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`
- Example ticket files in `_tickets/` directory for format reference

## Completion Criteria — Anchor Point
As part of closing this ticket:
1. Run `anchor_point.create` to generate a new AP for this component.
2. Add `ap.XXX.E` just below the `## CLI Entry Point` heading in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md` (that section describes the ticket as required input and its format).
3. Add `ref.ap.XXX.E` in the KDoc of the `TicketParser` interface pointing back to that design ticket section.

