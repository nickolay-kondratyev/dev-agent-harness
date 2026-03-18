---
id: nid_cqkr90es7s6frccf4arh487an_E
title: "SIMPLIFY_CANDIDATE: Reuse done signal for self-compaction instead of dedicated SelfCompacted signal"
status: open
deps: []
links: []
created_iso: 2026-03-18T14:24:48Z
status_updated_iso: 2026-03-18T14:24:48Z
type: chore
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, protocol]
---

## Problem

Self-compaction introduces a new `AgentSignal.SelfCompacted` variant, a new HTTP endpoint (`/signal/self-compacted`), a new callback script command, and strict signal enforcement (if agent signals `done` instead of `self-compacted`, re-instruct once then crash). The harness initiates compaction and already knows the agent is in compaction mode.

## Spec Reference

- `doc/use-case/ContextWindowSelfCompactionUseCase.md` (ref.ap.8nwz2AHf503xwq8fKuLcl.E)

## Proposed Change

Reuse existing `done completed` signal during compaction. The harness tracks compaction state internally — when it receives `done` during compaction mode, it validates PRIVATE.md exists (which it already does). Remove the `SelfCompacted` signal variant, `/signal/self-compacted` endpoint, and associated callback command.

## Justification

- **Simpler protocol**: One fewer signal type, one fewer endpoint, one fewer callback command.
- **More robust**: Eliminates the failure mode where an agent signals `done` instead of `self-compacted` (currently requires re-instruction + crash). The harness already knows context (it initiated compaction), so the signal type carries no new information.
- **Fewer agent instructions**: Agent just needs to do its work and signal `done` as usual — no need to learn a special compaction signal.
- **Less testing surface**: One fewer signal path to test end-to-end.

