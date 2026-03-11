## Project Overview

Codename: **TICKET_SHEPHERD**. Package: `com.glassthought.shepherd`.

CLI Kotlin Agent Harness — replaces a top-level orchestrator agent with a Kotlin CLI process.
Sub-agents are spawned as independent processes with fully isolated context windows.

### Key Characteristics
- **CLI application** built in Kotlin (JVM)
- **Agent coordination**: manages workflow phases, file-based communication between agents
- **NOT thorg-specific** — general-purpose agent harness

### High-Level Architecture Decisions

**Ticket-driven**: Shepherd always operates on a ticket (markdown file with YAML frontmatter containing `id`, `title`, and `status`). The ticket is required input. Ticket must have `status: in_progress` on entry — fail hard otherwise. Marking the ticket `in_progress` is the caller's responsibility, outside Shepherd scope.

**CLI**: `shepherd run --workflow <name> --ticket <path>` via **picocli**.

**Agent invocation — TMUX only**: All agents spawned as interactive TMUX sessions. Strictly serial (one agent at a time) in V1. Separate session per phase — context carries via files.

**Agent↔Harness communication — bidirectional** (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E):
- Agent → Harness: HTTP POST via `callback_shepherd.*.sh` scripts (one per endpoint, wraps curl)
- Harness → Agent: TMUX `send-keys` / ref.ap.7sZveqPcid5z1ntmLs27UqN6.E
- **All HTTP callbacks are non-blocking** — every callback returns 200 immediately. Responses (Q&A answers, iteration feedback) delivered via TMUX `send-keys`, never via HTTP response.
- Structured content delivered via temp files (write file, send path)

**HTTP server**: Ktor CIO, binds port 0 (OS-assigned), writes port to `$HOME/.shepherd_agent_harness/server/port.txt`. Starts once, stays alive across all phases.

**Workflow definitions**: JSON under `./config/workflows/`. Shared "parts" schema for both static and planner-generated workflows. **Jackson + Kotlin module** for all serialization.

**Two workflow types**:
- `straightforward` — static parts, no planning
- `with-planning` — PLANNER → PLAN_REVIEWER iteration loop, then dynamic execution from `plan.json`

**Role catalog**: Auto-discovered from `$TICKET_SHEPHERD_AGENTS_DIR`. Every `.md` file is an eligible role; `description` extracted from YAML frontmatter.

**Session tracking**: `AgentSessionIdResolver` interface (`ClaudeCodeAgentSessionIdResolver` impl) — GUID handshake to discover Claude Code session IDs.

**DirectLLM — tier-scoped interfaces**: `DirectQuickCheapLLM`, `DirectMediumLLM`, `DirectBudgetHighLLM` — all extend `DirectLLM`. Callers depend on the tier interface; `Initializer` wires concrete implementations. V1: `DirectQuickCheapLLM` → GLM-4.7-Flash, `DirectBudgetHighLLM` → GLM-5 (both Z.AI/GLM). **Not used for iteration decisions** — the reviewer's verdict is authoritative.

**Server endpoints (V1)** (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E):
- `POST /callback-shepherd/done` — task complete with required `result` field (`completed` for doers, `pass`/`needs_iteration` for reviewers). Server validates result against sub-part role.
- `POST /callback-shepherd/user-question` — question for human. Returns 200 immediately; answer delivered via TMUX.
- `POST /callback-shepherd/fail-workflow` — unrecoverable error, aborts entire workflow → `FailedToExecutePlanUseCase`.
- `POST /callback-shepherd/ping-ack` — acknowledges health ping.
- All requests include HandshakeGuid as identifier.

**Callback scripts**: Four focused bash scripts, one per endpoint:
- `callback_shepherd.done.sh <completed|pass|needs_iteration>`
- `callback_shepherd.user-question.sh "<text>"`
- `callback_shepherd.fail-workflow.sh "<reason>"`
- `callback_shepherd.ping-ack.sh`

**CodeAgent abstraction**: `CodeAgent.run(instructionFile, workingDir, publicOutputFile) -> AgentResult`. Instructions are Markdown files. `ClaudeCodeAgent` is the V1 implementation.

**Context assembly**: `ContextForAgentProvider` interface (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) assembles context packages — agent instruction files (role definition + ticket + SHARED_CONTEXT.md + prior PUBLIC.md files + callback script usage), planner instructions (ticket + role catalog).

**Iteration decisions — reviewer-authoritative**: Reviewer signals `result: "pass"` (proceed) or `result: "needs_iteration"` (loop back to doer). No LLM re-evaluation of the reviewer's verdict. On `needs_iteration` beyond `iteration.max` → `FailedToConvergeUseCase` (BudgetHigh DirectLLM summarizes state, user decides whether to grant more iterations).

**Agent lifecycle**: TMUX session created → agent started → AgentSessionIdResolver GUID handshake → instruction file sent via `send-keys` → agent works (may call user-question) → agent calls `callback_shepherd.done.sh <result>` → harness reads result, proceeds accordingly.

**Health monitoring**: Timeout → ping via TMUX → crash detection. UseCase pattern (`NoStatusCallbackTimeOutUseCase`, `NoReplyToPingUseCase`, `FailedToExecutePlanUseCase`, `FailedToConvergeUseCase`). **UseCase naming principle**: when logic has a natural UseCase name (verb + noun + context), encapsulate it in a dedicated UseCase class — stateless, single-responsibility operations that the shepherd delegates to.

**Plan mutability**: Frozen during execution. Minor adjustments within a part OK. Major deviations → agent calls `callback_shepherd.fail-workflow.sh` → `FailedToExecutePlanUseCase` prints red error to console and halts — waits for human intervention. V2 will add automated cleanup (see `doc_v2/FailedToExecutePlanUseCaseV2.md`).

**Progress tracking**: `current_state.json` tracks workflow progress. Resume-on-restart is V2 (ref.ap.LX1GCIjv6LgmM7AJFas20.E).

**File structure**: `.ai_out/${git_branch}/` with `harness_private/`, `shared/`, `planning/`, `phases/` subdirectories.

**Git branching**: Derived from ticket. `{TICKET_ID}__{slugified_title}__try-{N}` (`__` delimiter). try-N resolved by `TicketShepherdCreator` (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) — dual check: scans both local branches and `.ai_out/` directories, picks first N where neither exists.

### Dependencies
- Will take dependencies on well established third-party libraries.
- **Depends on asgardCore** (Out/OutFactory logging, ProcessRunner, AsgardCloseable, coroutines)
