---
closed_iso: 2026-03-18T01:43:19Z
id: nid_elqwwgm5e5v9ruitmqbogcek4_E
title: "Analyze whether it makes sense to pull in https://github.com/awslabs/cli-agent-orchestrator"
status: closed
deps: []
links: []
created_iso: 2026-03-18T01:35:06Z
status_updated_iso: 2026-03-18T01:43:19Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---


READ specs under FOLDER=[./doc]. FOCUS on specs not code.

Research https://github.com/awslabs/cli-agent-orchestrator and give a recommendation whether it's worthwhile to include or whether going directly to TMUX from our orchestrator framework makes more sense.

---

## Resolution: Do NOT pull in CLI Agent Orchestrator (CAO)

**Recommendation: Continue going directly to TMUX from our Kotlin harness.**

### What is CAO?

[CLI Agent Orchestrator](https://github.com/awslabs/cli-agent-orchestrator) (CAO) is a **Python-based** multi-agent orchestration system from AWS Labs that coordinates AI coding agents (Claude Code, Amazon Q, Kiro CLI, Codex CLI, Gemini CLI, Kimi CLI, Copilot CLI) within isolated tmux sessions via a FastAPI HTTP server and MCP (Model Context Protocol). It is actively maintained (last commit: 2026-03-18, 328 stars).

CAO uses a **supervisor-worker pattern** where the supervisor is itself an AI agent running in tmux that delegates tasks to worker agents via MCP tools (`handoff`, `assign`, `send_message`). Workers are spawned in their own tmux sessions with isolated contexts.

### Why NOT to pull it in

| Reason | Details |
|--------|---------|
| **Language mismatch** | CAO is Python 3.10+; TICKET_SHEPHERD is Kotlin/JVM. No clean interop path. Running CAO as a subprocess would introduce cross-language coordination overhead. |
| **Architectural incompatibility** | CAO uses MCP-based real-time messaging with inbox/watchdog delivery. TICKET_SHEPHERD uses file-based, git-committed communication with HTTP signal callbacks (`HandshakeGuid` protocol). These are not composable. |
| **Supervisor regression** | CAO's supervisor is an AI agent — exactly the pattern TICKET_SHEPHERD was designed to replace. Our whole point is that the orchestrator should be deterministic Kotlin code, not an LLM whose behavior depends on its context window. |
| **We already solve harder problems** | Git-integrated communication history, deterministic workflow execution, context window self-compaction/session rotation, ticket-driven planning, structured iteration budgets — none of these exist in CAO. |
| **Double infrastructure** | Would require running `cao-server` (FastAPI/SQLite) alongside our Ktor server — more moving parts for less value. |
| **Fragile status detection** | CAO detects agent state via regex on terminal output (spinner chars, prompt markers). Our HTTP callback protocol is more robust and agent-type-independent. |

### Architectural comparison

| Dimension | CAO | TICKET_SHEPHERD |
|-----------|-----|-----------------|
| Coordinator | AI agent (non-deterministic) | Kotlin process (deterministic) |
| Communication | MCP tools + HTTP inbox | File-based (`instructions.md`/`PUBLIC.md`) + HTTP signals |
| Visibility | Terminal buffer capture (volatile) | Git-committed files (permanent) |
| State persistence | SQLite | Git history + JSON state files |
| Workflow | Supervisor's system prompt | JSON workflow spec + plan |
| Status detection | Regex on terminal output (fragile) | HTTP callbacks with HandshakeGuid |
| Git integration | None | Deep (branch mgmt, commits, author attribution) |
| Context window mgmt | None | Self-compaction, session rotation |

### Patterns worth studying (no dependency needed)

1. **Provider regex patterns**: CAO's Claude Code idle/processing/completed detection patterns (`[✶✢✽✻✳]` for spinner, `>` / `❯` for prompt) could serve as **supplementary health monitoring fallback** signals alongside our HTTP callbacks.
2. **`pipe-pane` for log streaming**: Using `tmux pipe-pane` to stream output to files + file watcher for real-time activity detection — could enhance our health monitoring without polling.
3. **Bracketed paste for input**: CAO uses `load-buffer` + `paste-buffer -p` for reliable multi-line input delivery. Worth auditing our `TmuxCommunicator` for equivalent robustness.

### Sources
- [GitHub: awslabs/cli-agent-orchestrator](https://github.com/awslabs/cli-agent-orchestrator)
- [AWS Blog: Introducing CLI Agent Orchestrator](https://aws.amazon.com/blogs/opensource/introducing-cli-agent-orchestrator-transforming-developer-cli-tools-into-a-multi-agent-powerhouse/)
- [DEV.to: CLI Agent Orchestrator Review](https://dev.to/pinishv/cli-agent-orchestrator-when-one-ai-agent-isnt-enough-dc9)