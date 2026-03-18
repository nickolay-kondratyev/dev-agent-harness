---
id: nid_5mxkd9x6l1fx6ntbxd6mtkqr5_E
title: "SIMPLIFY_CANDIDATE: Eliminate model-versions.json for V1 — use model name directly in commit author"
status: open
deps: []
links: []
created_iso: 2026-03-18T02:10:06Z
status_updated_iso: 2026-03-18T02:10:06Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, YAGNI, V1-scope]
---

ref.ap.BvNCIzjdHS2iAP4gAQZQf.E (git spec)

Currently commit author encodes model version from a separate config/model-versions.json file (e.g., CC_sonnet-v4.6_WITH-username). This config must be manually kept in sync with actual agent model versions, and a missing/malformed file is a startup failure.

Proposal: For V1, use just the model name without version granularity: CC_sonnet_WITH-username. The version config file is forward-looking for quality-by-model-version analysis, but V1 does not yet have the analysis tooling to consume it.

Why simpler: Remove config/model-versions.json, its parsing, its startup validation, and the version lookup logic.
Why more robust: Eliminates config-drift risk (config says v4.5 but agent is actually v4.6). One fewer startup failure mode. Follows YAGNI — add version tracking in V2 when the analysis tooling exists to consume it.

File: doc/core/git.md

