---
closed_iso: 2026-03-17T22:36:41Z
id: nid_b5l7sip07e2kft5h8bzndq3fo_E
title: "SIMPLIFY_CANDIDATE: Restore staleness guard for context_window_slim.json to prevent acting on stale data"
status: closed
deps: []
links: []
created_iso: 2026-03-17T21:05:35Z
status_updated_iso: 2026-03-17T22:36:41Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, compaction, context-window]
---

--------------------------------------------------------------------------------
Per spec (doc/use-case/ContextWindowSelfCompactionUseCase.md), the `contextFileStaleTimeout` was removed. The current design trusts `remaining_percentage` from context_window_slim.json whenever it is present, with no check for how recently the file was written.

The file is written by an external hook (vintrin_env). If the hook:
- Stops writing mid-session (hook crash, env change)
- Writes a stale value (e.g., 45% from 30 minutes ago)

...the harness will:
1. Never trigger compaction (remaining_percentage looks healthy)
2. Agent silently runs out of context window
3. Health monitoring eventually catches it (via inactivity timeout)

The spec acknowledges this as a "benign failure mode" — but in practice, an agent running out of context window is NOT benign. It causes a crash, triggers recovery, and wastes time.

**Opportunity:** Restore a simple staleness guard in ContextWindowStateReader:
- If `context_window_slim.json` last-modified > N minutes (e.g., 5 minutes), treat `remaining_percentage` as unknown
- Unknown means: do NOT trigger compaction (safe default), but DO log a warning
- This can be a single `Files.getLastModifiedTime()` check — very simple

This change makes the stale-hook case detectable and logged, and avoids the harness silently acting on incorrect data.

Spec reference: doc/use-case/ContextWindowSelfCompactionUseCase.md, ref.ap.ufavF1Ztk6vm74dLAgANY.E (ContextWindowStateReader)
--------------------------------------------------------------------------------

OUCH Yes we need the staleness guard and we should respect the timestamp that was last written into the file.
LETS make sure we have the staleness check.
And this is how the file looks like `{"file_updated_timestamp":"2026-03-17T21:31:03.390Z","remaining_percentage":74}`





## Notes

**2026-03-17T22:36:37Z**

## Resolution

Updated spec (doc/use-case/ContextWindowSelfCompactionUseCase.md) and HarnessTimeoutConfig.kt:

1. **Vocabulary**: Updated `context_window_slim.json` format to document the `file_updated_timestamp` field.
2. **ContextWindowStateReader spec**: Added staleness guard — when `file_updated_timestamp` is older than `contextFileStaleTimeout` (default 5 min), `remainingPercentage` is returned as `null`.
3. **ContextWindowState**: Changed `remainingPercentage` to `Int?` (nullable). Null = stale/unknown. Callers skip compaction and log a warning.
4. **Caller behavior**: Documented that both done-boundary and emergency-interrupt polls must check for null and skip compaction with a warning.
5. **Thresholds table**: Restored `contextFileStaleTimeout` field (was incorrectly removed as "benign failure mode").
6. **HarnessTimeoutConfig.kt**: Added `contextFileStaleTimeout: Duration = 5.minutes` field and `2.seconds` fast-test override.
7. **Risks section**: Updated stale-hook risk entry to reflect the new detection behavior.
