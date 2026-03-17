---
id: nid_4awf8wopl41nje4demfjsijn8_E
title: "SIMPLIFY_CANDIDATE: Eliminate model-versions.json — drop version from commit author"
status: open
deps: []
links: []
created_iso: 2026-03-17T23:40:01Z
status_updated_iso: 2026-03-17T23:40:01Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, spec, git]
---

## Problem

The commit author format `CC_sonnet-v4.6_WITH-nickolaykondratyev` includes `VERSION_OF_MODEL` resolved from `config/model-versions.json` (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E). This introduces:
- A dedicated config file (`config/model-versions.json`)
- Startup file reading + JSON parsing + validation
- Per-commit version lookup
- A "model not found in version map" failure path

## Simplification

Drop the version component from commit author. Format becomes:
`CC_sonnet_WITH-nickolaykondratyev` instead of `CC_sonnet-v4.6_WITH-nickolaykondratyev`

This eliminates:
- `config/model-versions.json` file entirely
- File reading/parsing at startup
- Version lookup at commit time
- "Model not found" failure class

## Why This Improves Robustness

- Fewer startup failure paths (no file-not-found, no malformed JSON)
- Fewer runtime failure paths (no lookup-miss at commit time)
- Model name (sonnet, opus) is the primary differentiator in git history — the exact version (4.6 vs 4.7) changes over time and can be inferred from the commit date
- Model versions are already tracked in session records in `current_state.json` (`agentSession` entries) for V2 resume

## Affected Specs

- `doc/core/git.md` — Model Version Resolution section, Commit Author section
- `doc/high-level.md` — mentions model-versions.json under Required Environment Variables

