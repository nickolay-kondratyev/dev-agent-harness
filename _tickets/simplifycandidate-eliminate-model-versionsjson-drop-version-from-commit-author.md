---
closed_iso: 2026-03-17T23:48:25Z
id: nid_4awf8wopl41nje4demfjsijn8_E
title: "SIMPLIFY_CANDIDATE: Eliminate model-versions.json — drop version from commit author"
status: closed
deps: []
links: []
created_iso: 2026-03-17T23:40:01Z
status_updated_iso: 2026-03-17T23:48:25Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, spec, git]
---

## Resolution: REJECTED — model versions are intentionally kept

**Decision**: The simplification was rejected. Model versions in commit authors serve important
long-term codebase analysis needs that outweigh the added complexity.

### Original Proposal

Drop `VERSION_OF_MODEL` from commit author format and eliminate `config/model-versions.json`.

### Why It Was Rejected

Model version history in commits enables:
- **Upgrade targeting**: Identify code authored by older model versions that could benefit from
  re-generation or review with newer versions.
- **Test coverage prioritization**: Code produced by less capable model versions may warrant
  additional testing — the version tag makes this queryable via `git log`.
- **Quality correlation**: Correlate defect rates with specific model versions.
- **Audit trail**: Commit dates alone are insufficient because version upgrades are not instant —
  different environments may run different versions on the same date.

### Spec Changes Made

Added a **"Why model versions in commit author"** rationale section to `doc/core/git.md`
(ref.ap.BvNCIzjdHS2iAP4gAQZQf.E) under Model Version Resolution, documenting the WHY
for long-term maintainers.