---
closed_iso: 2026-03-17T20:51:17Z
id: nid_a8n55s2p9qiz4xwkea7tg1baa_E
title: "SIMPLIFY_CANDIDATE: Replace model version file-reading with structured config"
status: closed
deps: []
links: []
created_iso: 2026-03-15T01:03:34Z
status_updated_iso: 2026-03-17T20:51:17Z
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

## Resolution

**Completed.** Replaced file-per-model directory resolution with a single `config/model-versions.json` config file (Option 2 from proposal, per engineer's direction to use JSON file not hardcoded map).

### Changes made (spec-only):
1. **Created `config/model-versions.json`** — structured JSON mapping model names to version strings
2. **Updated `doc/core/git.md`**:
   - "Commit Author" table: `VERSION_OF_MODEL` source now references `config/model-versions.json`
   - "Model Version Resolution" section: replaced file-per-model directory approach with JSON config
   - "Required Environment Variables" table: removed `MODEL_VERSION_DIR` row
3. **No changes to `doc/schema/plan-and-current-state.md`** — model version is not part of the plan/current-state schema (it's a git commit-time concern only)

### Code changes still needed:
- `Constants.kt`: Remove `MODEL_VERSION_DIR` from `REQUIRED_ENV_VARS.ALL`
- `EnvironmentValidatorTest.kt`: Remove `MODEL_VERSION_DIR` from test fixtures
- Implement `ModelVersionConfig` class to load and parse `config/model-versions.json` at initialization
- Update `GitCommitStrategy` (or wherever `MODEL_VERSION_DIR` is consumed) to use the new config