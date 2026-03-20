---
id: nid_vrghoub55m7ypcua36lj437hg_E
title: "Wire TicketStatusUpdater production implementation in TicketShepherdCreator"
status: open
deps: []
links: [nid_ygnxc2gdnpjb8niouyg794r1g_E]
created_iso: 2026-03-20T00:46:34Z
status_updated_iso: 2026-03-20T00:46:34Z
type: feature
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [wiring, production, ticket]
---

## Context

`TicketShepherdCreatorImpl` (line 141-143 of `app/src/main/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreator.kt`) has a TODO stub for `TicketStatusUpdater`:

```kotlin
private val ticketStatusUpdater: TicketStatusUpdater = TicketStatusUpdater {
    TODO("TicketStatusUpdater not yet wired for production")
}
```

## What Needs to Happen

Implement a production `TicketStatusUpdater` that updates the ticket YAML frontmatter status to "done".

### Interface
- `app/src/main/kotlin/com/glassthought/shepherd/usecase/ticketstatus/TicketStatusUpdater.kt`
- `fun interface TicketStatusUpdater { suspend fun markDone() }`

### Spec (from `doc/core/TicketShepherd.md` — workflow success step 4b)
- Set ticket `status` field to `"done"` in the ticket file's YAML frontmatter\n- Called after `FinalCommitUseCase.commitIfDirty()` on workflow success\n- The ticket path is available from `TicketData` resolved in `TicketShepherdCreatorImpl.create()`\n\n### Implementation Details\n- Read the ticket markdown file\n- Parse YAML frontmatter (between `---` delimiters)\n- Update the `status:` field to `done`\n- Write the file back\n- The `TicketStatusUpdaterImpl` needs the ticket file path — receive via constructor\n- Consider using `tk close <id>` CLI command as alternative to direct file manipulation\n\n### Existing Fakes (for reference)\n- `TsTicketStatusUpdater` in `app/src/test/kotlin/.../TicketShepherdTest.kt`\n\n### Acceptance Criteria\n- Production `TicketStatusUpdaterImpl` created\n- Wired in `TicketShepherdCreatorImpl` default parameter (needs ticket path from `TicketData`)\n- TODO stub removed\n- Unit tests verifying: status updated to done, file written correctly\n- YAML frontmatter preserved (other fields unchanged)

