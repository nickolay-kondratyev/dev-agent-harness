---
id: nid_a8n55s2p9qiz4xwkea7tg1baa_E
title: "SIMPLIFY_CANDIDATE: Replace model version file-reading with structured config"
status: in_progress
deps: []
links: []
created_iso: 2026-03-15T01:03:34Z
status_updated_iso: 2026-03-17T20:47:59Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, git, config]
---

The git spec (doc/core/git.md) defines commit author encoding that reads model versions from files on disk:

  Format: ${CODING_AGENT}_${CODING_MODEL}-v${VERSION_OF_MODEL}_WITH-${HOST_USERNAME}
  Example: CC_sonnet-v4.6_WITH-nkondrat

The VERSION_OF_MODEL is resolved by reading a file at ${MODEL_VERSION_DIR}/${model_name}, where the file contains just the version string (e.g., "4.6").

## Problem
This is a non-standard, fragile pattern:
- Files may not exist → runtime error at commit time
- Files may contain wrong content (extra whitespace, wrong encoding)
- Requires maintaining a directory of version files outside the repo
- Requires MODEL_VERSION_DIR env var to be set correctly
- Adding a new model requires creating a new file in the right directory

## Proposal
Replace with a structured config approach. Options (in order of preference):
1. **Inline map in workflow JSON** — workflow already defines agentType and model per sub-part. Add a modelVersion field.
2. **Config file in repo** — a single `config/model-versions.json` that maps model names to versions.
3. **Constants in code** — a Kotlin map of model→version, updated when models change.

Option 1 is preferred because it co-locates model version with model selection (already in the workflow JSON), requires no extra files or env vars, and is version-controlled with the workflow.

## Benefits
- SIMPLER: No file I/O at commit time, no external directory dependency, no MODEL_VERSION_DIR env var
- MORE ROBUST: Eliminates "file not found" and "wrong content" failure classes entirely
- Model version is co-located with model selection → single source of truth

## Affected Specs
- doc/core/git.md (commit author encoding, model version resolution)
- doc/schema/plan-and-current-state.md (if adding modelVersion to workflow schema)

## Risk
- Low: This is a display/attribution concern. The version only appears in commit author strings. Changing the resolution mechanism has no functional impact on orchestration.

--------------------------------------------------------------------------------

OK lets refactor to have JSON structure. But it should be a file as JSON not a map hardcoded. WHY: to be able to update the configuration without recompilation.