---
id: nid_7vpbal1qdmrvt23g44vpq6hgv_E
title: "Implement InstructionSection sealed class + assembleFromPlan rendering engine + shared sections"
status: open
deps: [nid_8ts4qxw2wevxwep3yk2gvqwja_E]
links: [nid_zseecydaikj0f2i2l14nwcfax_E, nid_gp9rduvxoqf14m95z9bttnaxq_E, nid_r2rdkc0t9jd9597sumbgzp7aw_E]
created_iso: 2026-03-18T18:16:34Z
status_updated_iso: 2026-03-18T18:16:34Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [ContextForAgentProvider, architecture, InstructionSection]
---

## Goal

Implement the InstructionSection sealed class hierarchy and the assembleFromPlan rendering engine as specified in `doc/core/ContextForAgentProvider.md` (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E), section "Internal Design: Data-Driven Assembly".

This is the **assembly-side** architecture. The request-side sealed hierarchy (AgentInstructionRequest) is tracked separately in `nid_8ts4qxw2wevxwep3yk2gvqwja_E`.

## What to Build

### 1. InstructionSection sealed class

Create a sealed class with subtypes for each logical content block. This ticket covers the **shared/core sections** used across multiple roles:

| Section type | Description |
|---|---|
| `RoleDefinition` | Full `.md` file for the role from `$TICKET_SHEPHERD_AGENTS_DIR` |
| `PrivateMd` | Self-compaction context (`${sub_part}/private/PRIVATE.md`). **Skipped silently** if file does not exist. |
| `PartContext` | Part `name` and `description` from CurrentState |
| `Ticket` | Ticket markdown file content |
| `OutputPathSection(label, path)` | Labeled output path — replaces all role-specific output path sections |
| `WritingGuidelines` | Static PUBLIC.md writing guidance |
| `CallbackHelp` | Compaction-survival callback script usage (role-specific done signal) |

### 2. assembleFromPlan rendering engine

```kotlin
private suspend fun assembleFromPlan(
    plan: List<InstructionSection>,
    request: AgentInstructionRequest,
): Path
```

Walks the plan list, renders each section, joins with separators, writes to `request.outputDir/instructions.md`.

### 3. Section rendering

Each InstructionSection subtype must have a render method/function that produces its markdown content. Conditional sections (e.g., PrivateMd when file absent, PartContext for non-execution roles) produce empty/skip.

## Files

- New: `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionSection.kt` — sealed class + shared subtypes
- New: `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionPlanAssembler.kt` — the assembleFromPlan engine
- Tests: `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionSectionTest.kt` — unit tests per section renderer
- Tests: `app/src/test/kotlin/com/glassthought/shepherd/core/context/InstructionPlanAssemblerTest.kt` — engine tests

## Key Design Decisions

- Each section renders independently — testable in isolation
- Sections that read files (RoleDefinition, PrivateMd) take file paths and read at render time
- PrivateMd: skip silently if file does not exist (most sub-parts never self-compact)
- Use existing `InstructionText` constants and `InstructionRenderers` functions where applicable
- The engine writes to disk and returns the Path

## Spec Reference
- `doc/core/ContextForAgentProvider.md` — "Internal Design: Data-Driven Assembly" section
- Existing code: `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`
- Existing renderers: `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionRenderers.kt`
- Existing text constants: `app/src/main/kotlin/com/glassthought/shepherd/core/context/InstructionText.kt`

## Acceptance Criteria

1. InstructionSection sealed class exists with all 7 shared subtypes listed above. Note: `InlineFileContentSection` and `FeedbackDirectorySection` are out of scope — they belong to `nid_r2rdkc0t9jd9597sumbgzp7aw_E` and `nid_gp9rduvxoqf14m95z9bttnaxq_E` respectively.
2. assembleFromPlan engine renders a list of sections into a file
3. Each section has unit tests verifying its rendered output
4. PrivateMd silently skips when file does not exist
5. Engine test verifies section ordering and separator insertion
6. All tests pass via `./test.sh`

