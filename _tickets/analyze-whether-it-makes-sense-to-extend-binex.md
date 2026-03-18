---
closed_iso: 2026-03-18T01:54:11Z
id: nid_rbk6jzzqoz9qvkr3l4s7hgnwk_E
title: "Analyze whether it makes sense to extend binex"
status: closed
deps: []
links: []
created_iso: 2026-03-18T01:42:39Z
status_updated_iso: 2026-03-18T01:54:11Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---

## Analysis: Should We Extend Binex for Shepherd's Needs?

**TL;DR: No. Binex operates at a fundamentally different level of abstraction. The effort to extend it would exceed building Shepherd's agent interaction layer from scratch, with no meaningful reuse.**

---

### What Is Binex?

[Binex](https://github.com/Alexli18/binex) ("Debuggable runtime for AI agent workflows") is a Python-based (3.11+, FastAPI, React) visual orchestrator for multi-agent DAG pipelines. It focuses on:

- **YAML-defined DAG workflows** with conditional routing, fan-out/fan-in
- **LLM-as-API-call** execution model via [LiteLLM](https://docs.litellm.ai/) (40+ providers)
- **Observability**: per-node trace, replay, diff, cost tracking, artifact lineage
- **Visual Web UI**: drag-and-drop workflow builder, debug dashboard, Gantt timeline

Agent types supported via URI-prefix adapters:
| Prefix | What it does |
|--------|-------------|
| `llm://` | Direct LLM API call via LiteLLM |
| `local://` | In-process Python callable |
| `a2a://` | Remote agent via A2A protocol |
| `human://input\|approve\|output` | User interaction gates |
| `langchain://`, `crewai://`, `autogen://` | Framework adapters |
| `builtin://shell_command` | Shell command as a **tool** (not agent) |

---

### Answers to Specific Questions

#### 1) Can Binex run Claude Code in interactive mode?

**No.** Binex has no concept of interactive/persistent agent sessions. Its execution model is:
- **Stateless per node**: each node fires, produces output, completes
- **API-call driven**: LLM nodes make HTTP API calls, not CLI invocations
- **No terminal/tmux awareness**: no mechanism for sending input to a running process

Shepherd's interactive mode requires TMUX-based persistent sessions where the harness sends input via `tmux send-keys` and agents respond with HTTP callbacks. This is architecturally incompatible with binex's fire-and-forget node model.

#### 2) Can Binex run Claude Code CLI at all?

**Not as an agent.** Binex has `builtin://shell_command` as a **tool** available to LLM agents (the LLM can invoke shell commands during its reasoning). But there is no adapter that spawns a CLI process AS an agent node.

The `local://` adapter runs Python callables in-process. You could theoretically write a Python callable that shells out to `claude --print`, but:
- This would be a hacky wrapper, not a first-class integration
- It would only support `--print` (non-interactive, run-and-exit) mode
- No iteration, no feedback loops, no persistent sessions

#### 3) Would it be hard to extend to enable iteration?

**Yes, significantly hard.** The fundamental mismatch:

| Concern | Binex | Shepherd |
|---------|-------|---------|
| Execution model | DAG (directed acyclic graph) | Doer-Reviewer iteration loop with persistent sessions |
| Iteration | Conditional branching in DAG (pre-defined paths) | Dynamic iteration: reviewer feedback → doer retries in same session |
| Agent lifecycle | Stateless, fire-once | Long-lived, receives multiple instructions |
| Communication | Node outputs piped to dependent nodes | TMUX send-keys with Payload ACK protocol |

Binex's DAG model is inherently **acyclic** — you cannot have a node send output back to a predecessor without fundamentally changing the execution engine. Shepherd's doer-reviewer loop requires:
- Same agent session receiving multiple rounds of instructions
- Fresh `CompletableDeferred` per round within the same spawn
- Budget-aware iteration counting with convergence failure handling

Adding this to binex would mean rewriting its core `runtime/` orchestration engine.

#### 4) Would it be hard to extend for Claude Code interactive mode (Tmux-like runner)?

**Extremely hard — effectively a rewrite of Shepherd's core value proposition.** You would need to build from scratch within binex:

1. **TMUX session management**: spawn, send-keys, kill sessions
2. **Bootstrap handshake protocol**: HandshakeGuid, startup timeout, session ID resolution
3. **Payload Delivery ACK protocol**: XML-wrapped payloads, unique PayloadIds, ACK timeout/retry
4. **Health monitoring**: activity timestamps, ping/ping-ack, crash detection (30 min stale → ping → 3 min timeout → crash)
5. **Context window state awareness**: reading compaction state, emergency session rotation at 20% remaining
6. **HTTP callback server**: fire-and-forget signals from agents (started, done, fail-workflow, user-question, ack-payload)
7. **AgentTypeAdapter abstraction**: pluggable agent types (ClaudeCodeAdapter resolves session IDs by scanning JSONL files)

This is essentially **all of Shepherd's `AgentFacade` layer** — the entire reason Shepherd exists. Building it inside binex gains nothing; you'd be fighting binex's architecture at every turn.

---

### Additional Questions Worth Answering

#### 5) What would we actually reuse from binex?

Almost nothing relevant to Shepherd's core needs:
- **Observability/tracing**: Shepherd already has `.ai_out/` + git-commit-per-signal, which is more appropriate for its model
- **DAG orchestration**: Shepherd's plan is sequential with iteration, not a general DAG
- **Web UI**: Nice-to-have but orthogonal; could be added to Shepherd independently
- **LiteLLM integration**: Shepherd uses DirectLLM tier for non-agent LLM calls, which is simpler and more controlled

#### 6) Technology stack mismatch?

Yes. Binex is Python 3.11+ / FastAPI / React. Shepherd is Kotlin/JVM. Adopting binex would mean either:
- **Rewriting Shepherd in Python** (massive effort, loses Kotlin type safety, coroutines)
- **Running binex as a sidecar** (adds operational complexity, IPC overhead, two runtimes)

Neither makes sense.

#### 7) What is binex actually good for?

Binex excels at a different use case: **orchestrating stateless LLM-API-call pipelines** where you want visual debugging, replay, and cost tracking. Think: "chain GPT-4 summarizer → Claude classifier → Ollama formatter" with full observability. This is fundamentally different from Shepherd's use case of **orchestrating persistent interactive coding agents**.

---

### Conclusion

**Do not extend binex for Shepherd.** The architectural gap is too large:

| | Binex | Shepherd |
|-|-------|---------|
| Agent model | Stateless API calls | Persistent interactive sessions |
| Communication | DAG data flow | TMUX send-keys + HTTP callbacks |
| Iteration | Acyclic conditional branching | Cyclic doer-reviewer feedback loop |
| Language | Python | Kotlin/JVM |
| Value proposition | Visual debugging of LLM pipelines | Orchestrating coding agents with iteration |

The effort to bridge these gaps would exceed building Shepherd's features from scratch, while adding unnecessary complexity and a foreign technology dependency.

---

### Sources
- [Binex GitHub Repository](https://github.com/Alexli18/binex)
- [Binex Documentation Site](https://alexli18.github.io/binex)
- [Binex HN Discussion](https://news.ycombinator.com/item?id=47315313)
- Shepherd specs: `doc/high-level.md`, `doc/core/AgentInteraction.md`, `doc/core/PartExecutor.md`, `doc/core/SessionsState.md`
