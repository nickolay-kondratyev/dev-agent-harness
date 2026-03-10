# Clarification: Git Branch Manager

## Requirements (Confirmed Clear)
- BranchNameBuilder: pure function, `{TICKET_ID}__{slugified_title}__try-{N}`
- Slugify: lowercase, non-alphanumeric → hyphens, collapse, trim, truncate at ~60 chars
- GitBranchManager: interface + impl, `createAndCheckout(branchName)` + `getCurrentBranch()`
- Uses ProcessRunner from asgardCore
- Package: `com.glassthought.chainsaw.core.git`
- V1: truncate long titles, no LLM compression
- No build.gradle.kts changes

## Resolved During Clarification
- ProcessRunner API from asgardCore will be discovered by implementation agent (it's a library dependency)
- BranchNameBuilder takes TicketData + tryNumber as inputs
- No ambiguities requiring human input
