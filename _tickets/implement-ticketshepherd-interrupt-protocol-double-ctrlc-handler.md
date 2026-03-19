---
id: nid_eq78xsmd72qtrycecxb7ltqp7_E
title: "Implement TicketShepherd interrupt protocol — double Ctrl+C handler"
status: in_progress
deps: [nid_xeq8q9q7xmr56x5ttr98br4z9_E, nid_m7oounvwb31ra53ivu7btoj5v_E, nid_m3cm8xizw5qhu1cu3454rca79_E]
links: []
created_iso: 2026-03-19T00:39:56Z
status_updated_iso: 2026-03-19T17:41:31Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [shepherd, ticket-shepherd, interrupt]
---

## Context

Spec: `doc/core/TicketShepherd.md` (ref.ap.P3po8Obvcjw4IXsSUSU91.E), section "Interrupt Protocol (Ctrl+C)".

The harness uses a double-Ctrl+C pattern to prevent accidental termination.

## What to Implement

### 1. InterruptHandler (or similar)
A component that installs a JVM shutdown hook / signal handler for SIGINT (Ctrl+C).

### 2. Behavior
1. **First Ctrl+C** -> print `"Press Ctrl+C again to confirm exit."` and record the current timestamp. Execution continues uninterrupted.
2. **Second Ctrl+C within 2 seconds** -> kill all TMUX sessions, write in-memory `CurrentState` with `FAILED` status on active sub-parts (flush to `current_state.json`), exit with non-zero code.
3. **Second Ctrl+C after more than 2 seconds** -> treated as a fresh first Ctrl+C (timestamp resets, prompt reprints).

### 3. Key Design Decisions
- **No stdin contention** — avoids conflict with `StdinUserQuestionHandler` which also reads stdin for agent Q&A.
- **No blocking window** — does not hold a stdin read that delays signal delivery.
- **Standard CLI idiom** — users already know double-Ctrl+C.
- Uses `Clock` interface (nid_xeq8q9q7xmr56x5ttr98br4z9_E) for testability of the 2-second window.
- Needs reference to `AgentFacade` (to kill sessions) and `CurrentState` (to flush failed status).

### 4. Dependencies
- `Clock` interface (nid_xeq8q9q7xmr56x5ttr98br4z9_E) for time.
- `AgentFacade` interface (nid_m7oounvwb31ra53ivu7btoj5v_E) for session cleanup.
- `CurrentState` (nid_m3cm8xizw5qhu1cu3454rca79_E) for state flush.

### 5. Testing
- Unit test: first Ctrl+C records timestamp, does not exit.
- Unit test: second Ctrl+C within 2 seconds triggers cleanup and exit.
- Unit test: second Ctrl+C after 2 seconds resets timestamp.
- Use `TestClock` to control time in tests.

