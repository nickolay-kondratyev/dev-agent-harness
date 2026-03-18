---
closed_iso: 2026-03-18T13:08:56Z
id: nid_3fy5tujjzrud1fduy25zzdijm_E
title: "Look into openclaw"
status: closed
deps: []
links: []
created_iso: 2026-03-18T12:55:55Z
status_updated_iso: 2026-03-18T13:08:56Z
type: task
priority: 3
assignee: nickolaykondratyev
---

READ specs under FOLDER=[./doc]. FOCUS on specs not code.

Research into whether Open Claw supports the use cases that we are trying to build with shepherd already. Put the findings into this ticket.

### References
- https://docs.openclaw.ai/
- https://docs.openclaw.ai/tools/lobster
- https://github.com/laurentenhoor/devclaw

---

## Research Findings

### What is OpenClaw?

OpenClaw is a **self-hosted gateway** that bridges messaging platforms (WhatsApp, Telegram, Discord, iMessage, Mattermost) with AI coding agents like "Pi". It runs locally on the user's machine and routes messages from chat apps to AI agents. Core value: always-available AI access across chat channels with data privacy (no cloud dependency).

**OpenClaw is NOT an agent orchestration framework in the TICKET_SHEPHERD sense. It is a chat-to-AI gateway with plugin extensibility.**

### What is the Lobster Tool?

Lobster is OpenClaw's **typed workflow runtime** — a subprocess-based pipeline executor. Key characteristics:
- Executes multi-step tool sequences as a **single deterministic operation** (reduces LLM back-and-forth)
- Supports **JSON-piped CLI command chains** with steps communicating via stdout/stdin
- Has **approval gates**: pauses before side effects, returns a `resumeToken` for later resumption
- Workflow definitions use `.lobster` YAML/JSON files with named steps, env vars, and conditional logic
- Enforces timeouts (20,000ms default) and output size caps (512,000 bytes)
- Three response statuses: `ok`, `needs_approval`, `cancelled`

Lobster is a **deterministic pipeline runner with human approval checkpoints**, not a multi-agent orchestrator.

### What is DevClaw (github.com/laurentenhoor/devclaw)?

DevClaw is an **OpenClaw plugin** that turns the gateway into an autonomous development manager. It automates multi-project software development workflows using GitHub/GitLab issues as its state machine.

**Architecture (3-tier):**
1. **Orchestrator agent** — plans and dispatches; does not write code
2. **Worker agents** — developers, reviewers, testers, architects; run in isolated sessions per project
3. **Scheduling engine (`work_heartbeat`)** — CLI-based heartbeat scanning queues and dispatching workers

Workers are assigned by tier (junior/medior/senior → Haiku/Sonnet/Opus). State lives entirely in GitHub/GitLab issues (labels drive the state machine). Claims 60-80% token savings. 23 tools enforce the pipeline.

---

### Capability Comparison: OpenClaw/DevClaw vs. TICKET_SHEPHERD Requirements

| Capability | OpenClaw / DevClaw | Notes |
|---|---|---|
| **Multi-agent orchestration / coordinating multiple Claude Code agents** | ❌ Partial (DevClaw only) | DevClaw orchestrates workers via label state machine, but these are OpenClaw sessions — not independently spawned Claude Code OS processes |
| **Spawning sub-agents as independent processes with isolated context windows** | ❌ Partial (DevClaw) | Workers get isolated sessions per project, but via OpenClaw's internal session model — not OS-level process spawning with HandshakeGuid protocol |
| **Phase/plan-based workflow management (setup plan → detailed plan → execute parts)** | ❌ No | DevClaw uses a flat label-driven pipeline (planning → dev → review → deploy), not a structured plan-JSON with setup/detailed phases |
| **HTTP callback/communication protocol between orchestrator and sub-agents** | ❌ No | Communication is via tool calls within OpenClaw's framework; no comparable HTTP callback protocol |
| **Context window management / self-compaction** | ❌ No | Not documented; DevClaw's session reuse accumulates context but does not manage window limits or trigger compaction/rotation |
| **Git operations (branching, commits with author attribution)** | ⚠️ Partial (DevClaw mentions PR/Git tools) | Vague; no detail on branching strategy or author attribution comparable to `doc/core/git.md` |
| **Ticket/task management** | ✅ Yes (DevClaw) | Task state lives in GitHub/GitLab issues with label transitions |
| **Health monitoring of agents** | ⚠️ Minimal | DevClaw mentions "2-hour timeout detection" only; nothing comparable to Shepherd's ping/crash detection |
| **Resume-on-restart functionality** | ⚠️ Partial (Lobster `resumeToken`) | Lobster can resume paused approval workflows; no harness-level restart-from-checkpoint |
| **Failure learning / cross-try learning** | ❌ No | Not mentioned anywhere |
| **User question delegation (when agent needs to ask user something)** | ❌ No | Not a first-class feature |
| **NonInteractive agent running (--print mode subprocess)** | ❌ No | Not documented |

---

### Key Architectural Differences

1. **Domain mismatch**: OpenClaw is a **chat gateway**. TICKET_SHEPHERD is an **agent harness** coordinating sub-agents on ticket work. Fundamentally different problem domains.

2. **Orchestration model**: DevClaw is **issue-tracker-driven** (GitHub/GitLab labels as state machine). TICKET_SHEPHERD uses a **Kotlin CLI process** with structured plan-JSON and distinct workflow phases (SetupPlan → DetailedPlan → PartExecutor per part).

3. **Agent spawning**: DevClaw uses OpenClaw's internal session model. TICKET_SHEPHERD spawns **independent OS-level processes** with isolated context windows, HandshakeGuid identity, and an HTTP callback protocol for bidirectional communication.

4. **Communication protocol**: TICKET_SHEPHERD has a defined **Agent↔Harness HTTP protocol** with typed endpoints and TMUX `send-keys` delivery with ACK. OpenClaw/DevClaw has no comparable protocol.

5. **Context window awareness**: TICKET_SHEPHERD has explicit context window detection (35%/20% thresholds), PRIVATE.md compaction, and session rotation (ref.ap.8nwz2AHf503xwq8fKuLcl.E). OpenClaw has none of this.

6. **Failure learning**: TICKET_SHEPHERD has `TicketFailureLearningUseCase` for structured cross-try learning. DevClaw has no equivalent.

7. **Git workflow**: TICKET_SHEPHERD has detailed git branching strategy with try-N resolution and agent commit attribution (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E). DevClaw mentions Git vaguely.

---

### Bottom Line

**OpenClaw/DevClaw do NOT replace or duplicate TICKET_SHEPHERD.**

They overlap only at a high conceptual level (both route work to AI agents). OpenClaw is primarily a **chat-to-agent gateway** for messaging app integration; DevClaw is a **GitHub-issue-driven dev pipeline plugin** for project management workflows.

Neither implements:
- Structured plan-based orchestration with phase separation
- OS-level process isolation with HandshakeGuid protocol
- HTTP callback communication protocol with TMUX delivery
- Context self-compaction with threshold monitoring
- Failure learning and cross-try knowledge transfer
- Health monitoring with ping/crash detection
- NonInteractive agent runner for utility tasks

**TICKET_SHEPHERD addresses a more focused and technically deeper problem**: a Kotlin CLI harness that replaces a top-level orchestrator agent, with full context isolation, bidirectional communication protocol, health monitoring, and git workflow ownership. This problem space is largely unaddressed by OpenClaw/DevClaw.
