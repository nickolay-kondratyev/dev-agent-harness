---
id: nid_ymtlew0apdjcyaco2qff3vva4_E
title: "Agent CLI Script (harness-cli-for-agent.sh)"
status: open
deps: []
links: []
created_iso: 2026-03-09T23:06:21Z
status_updated_iso: 2026-03-09T23:06:21Z
type: feature
priority: 1
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [wave1, cli, bash]
---

Create the bash CLI script that agents use to communicate back to the harness via HTTP.

## Scope
- Create `harness-cli-for-agent.sh` bash script
- Reads server port from `$HOME/.chainsaw_agent_harness/server/port.txt`
- Subcommands: `done`, `question "<text>"`, `failed "<reason>"`, `status`
- Each subcommand wraps a `curl` POST to the corresponding `/agent/*` endpoint
- Includes `--help` output (this will be embedded in agent instructions)
- Error handling: fail with clear message if port file does not exist (server not running)
- All requests include the current git branch as identifier

## Key Decisions
- Script lives on the agent PATH (placed in a known location, e.g., `scripts/` or `bin/`)
- Port discovery is file-based — NO env var needed
- The `question` subcommand blocks until the server responds (curl blocks, server holds the connection)
- Keep the script simple — it is glue code wrapping curl
- `--help` output will be wrapped in `<critical_to_keep_through_compaction>` tags in agent instructions

## Testing
- Test: `--help` produces usage output
- Test: fails with clear error when port file is missing
- Test: constructs correct curl commands for each subcommand (can test with mock or by inspecting constructed URLs)

## Files touched
- New file: `scripts/harness-cli-for-agent.sh` (or `bin/harness-cli-for-agent.sh`)
- Possibly a test script
- Does NOT touch `app/build.gradle.kts`

## Reference
- See "Agent CLI Script" section in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`

## Completion Criteria — Anchor Point
As part of closing this ticket:
1. Run `anchor_point.create` to generate a new AP for this component.
2. Add `ap.XXX.E` just below the `### Agent CLI Script` heading in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md`.
3. Add `ref.ap.XXX.E` as a comment near the top of `harness-cli-for-agent.sh` pointing back to that design ticket section.

