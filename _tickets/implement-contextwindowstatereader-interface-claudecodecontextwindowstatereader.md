---
closed_iso: 2026-03-19T16:46:54Z
id: nid_mebn70o7xjiabzx5uxngjx8uf_E
title: "Implement ContextWindowStateReader interface + ClaudeCodeContextWindowStateReader"
status: closed
deps: [nid_xeq8q9q7xmr56x5ttr98br4z9_E, nid_m7oounvwb31ra53ivu7btoj5v_E]
links: []
created_iso: 2026-03-19T00:40:44Z
status_updated_iso: 2026-03-19T16:46:54Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, compaction]
---

## Context

Spec: `doc/use-case/ContextWindowSelfCompactionUseCase.md` (ref.ap.8nwz2AHf503xwq8fKuLcl.E), R1.
Reader interface: ref.ap.ufavF1Ztk6vm74dLAgANY.E

This is Gate 1 from the compaction spec — the foundation for context window monitoring.

## What to Implement

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/agent/contextwindow/ContextWindowStateReader.kt`

### 1. ContextWindowStateReader interface

```kotlin
interface ContextWindowStateReader {
    /**
     * Reads the current context window state for an agent session.
     * Throws [ContextWindowStateUnavailableException] if the state file
     * is not present — this is a hard stop failure indicating the
     * external hook is not configured.
     *
     * Returns [ContextWindowState] with [ContextWindowState.remainingPercentage] = null
     * when the file is present but its [ContextWindowState.fileUpdatedTimestamp] is older
     * than [HarnessTimeoutConfig.contextFileStaleTimeout]. Callers MUST treat null as
     * "unknown" — no compaction should be triggered, but a warning must be logged.
     */
    suspend fun read(agentSessionId: String): ContextWindowState
}
```

Note: `ContextWindowState` data class is already defined in AgentFacade interface ticket (nid_m7oounvwb31ra53ivu7btoj5v_E).

### 2. ContextWindowStateUnavailableException

Extends `AsgardBaseException`. Used for:
- File missing → hard stop failure (external hook not configured)
- File malformed (missing `remaining_percentage` or `file_updated_timestamp`, unparseable JSON) → same exception with parse error details

### 3. ClaudeCodeContextWindowStateReader implementation

Reads from: `${HOME}/.vintrin_env/claude_code/session/<agentSessionId>/context_window_slim.json`

JSON format:
```json
{
  "file_updated_timestamp": "<ISO-8601 UTC>",
  "remaining_percentage": N
}
```

Behavior:
- File missing → `ContextWindowStateUnavailableException` (hard stop)
- File malformed → `ContextWindowStateUnavailableException` with parse error details
- File present, `file_updated_timestamp` older than `contextFileStaleTimeout` → return `ContextWindowState(remainingPercentage = null)` + log warning
- File present, timestamp fresh → return `ContextWindowState(remainingPercentage = N)`

### Constructor Dependencies

```kotlin
class ClaudeCodeContextWindowStateReader(
    private val clock: Clock,
    private val harnessTimeoutConfig: HarnessTimeoutConfig,
    private val outFactory: OutFactory,
) : ContextWindowStateReader
```

### Staleness detection

Uses `file_updated_timestamp` from JSON body (NOT OS file mtime). Compare against `clock.now()` minus `harnessTimeoutConfig.contextFileStaleTimeout`.

WHY staleness is NOT a hard-stop: the hook stopping mid-session is a recoverable situation. An unknown context state is safe to ignore (no compaction triggered). A truly dead agent will be caught by noActivityTimeout in health monitoring.

## Tests

1. Unit test: valid JSON with fresh timestamp → returns `ContextWindowState(remainingPercentage = N)`
2. Unit test: valid JSON with stale timestamp → returns `ContextWindowState(remainingPercentage = null)` + warning logged
3. Unit test: file missing → throws `ContextWindowStateUnavailableException`
4. Unit test: malformed JSON (missing fields) → throws `ContextWindowStateUnavailableException`
5. Unit test: malformed JSON (unparseable) → throws `ContextWindowStateUnavailableException`
6. Use TestClock for deterministic staleness detection
7. Use temp directory to write test JSON files

## Acceptance Criteria

- Interface + implementation compile
- All unit tests pass
- `./test.sh` passes


## Notes

**2026-03-19T00:51:24Z**

Added missing dep: AgentFacade interface (nid_m7oounvwb31ra53ivu7btoj5v_E) — ContextWindowState data class is defined there.
