# NonInteractiveAgentRunner / ap.ad4vG4G2xMPiMHRreoYVr.E

A lightweight alternative to `SpawnTmuxAgentSessionUseCase` (ref.ap.hZdTRho3gQwgIXxoUtTqy.E)
for utility tasks that don't need interactive TMUX sessions — infrastructure recovery, failure
analysis, file cleanup, etc.

---

## Motivation

The full TMUX agent session lifecycle (`SpawnTmuxAgentSessionUseCase` → handshake → health
monitoring → `SessionsState` registration → `AgentTypeAdapter.resolveSessionId()` → session kill) is
designed for **long-lived, interactive work sessions**. Using this machinery for short-lived
utility tasks (e.g., `rm .git/index.lock`, summarizing failure context) is disproportionate
complexity — a crane to pick up a penny.

`NonInteractiveAgentRunner` provides a **subprocess-based, fire-and-forget** agent invocation
for tasks that complete in minutes, not hours.

---

## Design

Runs an agent as a **subprocess** in `--print` mode (non-interactive, run-and-exit). No TMUX,
no handshake, no health monitoring, no `SessionsState`, no `AgentTypeAdapter.resolveSessionId()`.

The agent receives instructions via CLI args, does its work, and exits. The harness waits for
the process to complete (with a timeout) and checks the exit code.

---

## Interface

```kotlin
interface NonInteractiveAgentRunner {
    suspend fun run(request: NonInteractiveAgentRequest): NonInteractiveAgentResult
}

data class NonInteractiveAgentRequest(
    /** The instructions for the agent — passed as -p argument */
    val instructions: String,

    /** Working directory for the subprocess */
    val workingDirectory: Path,

    /** Which agent binary to use */
    val agentType: AgentType,  // ClaudeCode, PI

    /** Model name (e.g., "sonnet", value of $AI_MODEL__ZAI__FAST) */
    val model: String,

    /** Kill the process after this duration */
    val timeout: Duration,
)

sealed class NonInteractiveAgentResult {
    /** Agent exited with code 0 */
    data class Success(val output: String) : NonInteractiveAgentResult()

    /** Agent exited with non-zero code */
    data class Failed(val exitCode: Int, val output: String) : NonInteractiveAgentResult()

    /** Process killed after timeout */
    data class TimedOut(val output: String) : NonInteractiveAgentResult()
}
```

---

## Command Construction

Per agent type:

### ClaudeCode

```bash
claude --print --model {model} -p "{instructions}"
```

- Uses the system `claude` binary (same as `ClaudeCodeAdapter` ref.ap.A0L92SUzkG3gE0gX04ZnK.E)
- `--print` flag: non-interactive mode, agent runs and exits
- No `--system-prompt-file` needed for utility tasks (uses Claude Code defaults)
- No env var exports needed beyond what the harness process already has

### PI

```bash
pi --provider zai --model {model} -p "{instructions}"
```

- Requires `ZAI_API_KEY` env var set in the subprocess environment
- `ZAI_API_KEY` value read from `${MY_ENV}/.secrets/Z_AI_GLM_API_TOKEN` at initialization
- Model name comes from `$AI_MODEL__ZAI__FAST` env var (resolved at initialization)

---

## Execution

1. Build the shell command based on `agentType`
2. Spawn subprocess via `ProcessRunner` with:
   - Working directory from request
   - Inherited env vars + `ZAI_API_KEY` (for PI)
   - Combined stdout+stderr capture
3. Await process completion with `timeout`
4. On exit code 0 → `NonInteractiveAgentResult.Success(output)`
5. On non-zero exit → `NonInteractiveAgentResult.Failed(exitCode, output)`
6. On timeout → kill process → `NonInteractiveAgentResult.TimedOut(output)`

---

## Required Environment Variables

| Env var | Purpose | Validated at |
|---|---|---|
| `AI_MODEL__ZAI__FAST` | Model name for PI agent utility tasks (e.g., `glm-4.7-flash`) | Initialization |

`ZAI_API_KEY` is derived from `${MY_ENV}/.secrets/Z_AI_GLM_API_TOKEN` (already validated
via `MY_ENV` at initialization ref.ap.BvNCIzjdHS2iAP4gAQZQf.E).

---

## Wiring

`NonInteractiveAgentRunner` is wired by `ContextInitializer` (ref.ap.9zump9YISPSIcdnxEXZZX.E)
as part of `ShepherdContext` (ref.ap.TkpljsXvwC6JaAVnIq02He98.E). Available to all use cases
that need lightweight agent invocation.

---

## Consumers

| Consumer | Agent Type | Model | Timeout | Purpose |
|---|---|---|---|---|
| `TicketFailureLearningUseCase` (ref.ap.cI3odkAZACqDst82HtxKa.E) | ClaudeCode | sonnet | 20 min | Read artifacts, produce failure summary text (stdout only — harness handles ticket update and git) |

> **V1 note:** `AutoRecoveryByAgentUseCase` (ref.ap.q54vAxzZnmWHuumhIQQWt.E) was the planned
> PI consumer but is **deferred to V2** — V1 uses index.lock fast-path + fail-fast instead.
> PI command construction is retained in the interface for V2 readiness.

> **Why 20 minutes:** Non-interactive agents have **no health monitoring** — no ping, no
> `lastActivityTimestamp`, no proof-of-life mechanism. The process-level timeout is the **only**
> safeguard. These agents may encounter complex diagnostic work (scanning logs, reading large
> artifacts, running git commands), so a generous timeout prevents premature kills. 20 minutes
> balances "give it time to finish" against "don't hang forever."

---

## What It Does NOT Do

- Does **not** manage TMUX sessions — agents run as plain subprocesses
- Does **not** perform handshake — no `HandshakeGuid`, no `/signal/started`
- Does **not** monitor health — no ping, no `lastActivityTimestamp`
- Does **not** register in `SessionsState` — sessions are ephemeral and untracked
- Does **not** resolve agent session IDs — not needed for non-interactive runs
- Does **not** support `--resume` — each invocation is independent

---

## Comparison with SpawnTmuxAgentSessionUseCase

| Concern | SpawnTmuxAgentSessionUseCase | NonInteractiveAgentRunner |
|---|---|---|
| Session type | TMUX (interactive, persistent) | Subprocess (non-interactive, ephemeral) |
| Agent mode | Interactive (conversation loop) | `--print` (single run, exit) |
| Handshake | Full HandshakeGuid protocol | None |
| Health monitoring | Health-aware await loop with ping | Process-level timeout only |
| Session tracking | `SessionsState` + in-memory `CurrentState` (flushed to `current_state.json`) | None |
| Session ID | `AgentTypeAdapter.resolveSessionId()` | Not applicable |
| Use case | Long-lived work sessions (implementation, review) | Short-lived utility tasks (recovery, analysis) |
| Complexity | High (necessary for interactive work) | Low (appropriate for utility tasks) |
