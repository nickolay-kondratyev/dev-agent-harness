## Project Overview

Codename: **TICKET_SHEPHERD**. Package: `com.glassthought.shepherd`.

CLI Kotlin Agent Harness — replaces a top-level orchestrator agent with a Kotlin CLI process.
Sub-agents are spawned as independent processes with fully isolated context windows.

### Key Characteristics
- **CLI application** built in Kotlin (JVM)
- **Agent coordination**: manages workflow phases, file-based communication between agents
- **NOT thorg-specific** — general-purpose agent harness

### Architecture — Spec References

> Full specs live under `doc/`. The high-level entry point is `doc/high-level.md`.

| Area | Spec (AP ref) |
|------|---------------|
| High-level design, hard constraints, vocabulary | `doc/high-level.md` |
| Central coordinator (`TicketShepherd`) | ref.ap.P3po8Obvcjw4IXsSUSU91.E |
| Dependency wiring (`TicketShepherdCreator`) | ref.ap.cJbeC4udcM3J8UFoWXfGh.E |
| Agent↔Harness protocol, endpoints, callbacks | ref.ap.wLpW8YbvqpRdxDplnN7Vh.E |
| Agent spawning, HandshakeGuid, session ID | ref.ap.hZdTRho3gQwgIXxoUtTqy.E |
| AgentFacade facade (testability seam, FakeAgentFacade, virtual time) | ref.ap.9h0KS4EOK5yumssRCJdbq.E |
| PartExecutor, AgentSignal, iteration loop | ref.ap.fFr7GUmCYQEV5SJi8p6AS.E |
| Context/instruction assembly | ref.ap.9HksYVzl1KkR9E1L2x8Tx.E |
| SessionsState (GUID→session registry, internal to AgentFacadeImpl) | ref.ap.7V6upjt21tOoCFXA7nqNh.E |
| Health monitoring UseCases | ref.ap.RJWVLgUGjO5zAwupNLhA0.E |
| DirectLLM single interface (model config at wiring time) | ref.ap.hnbdrLkRtNSDFArDFd9I2.E |
| UserQuestionHandler strategy | ref.ap.NE4puAzULta4xlOLh5kfD.E |
| Git branching, commits, author attribution | ref.ap.BvNCIzjdHS2iAP4gAQZQf.E |
| Plan & current-state JSON schema, in-memory CurrentState | ref.ap.56azZbk7lAMll0D4Ot2G0.E |
| `.ai_out/` directory schema | ref.ap.BXQlLDTec7cVVOrzXWfR7.E |
| SetupPlanUseCase (routing) | ref.ap.VLjh11HdzC8ZOhNCDOr2g.E |
| DetailedPlanningUseCase | ref.ap.cJhuVZTkwfrWUzTmaMbR3.E |
| Agent type & model assignment | ref.ap.Xt9bKmV2wR7pLfNhJ3cQy.E |
| Agent roles directory structure | ref.ap.Q7kR9vXm3pNwLfYtJ8dZs.E |
| Resume-on-restart (V2) | ref.ap.LX1GCIjv6LgmM7AJFas20.E |
| Working tree validation (startup guard) | ref.ap.QL051Wl21jmmYqTQTLglf.E |
| Git operation failure handling (GitOperationFailureUseCase) | ref.ap.AQ8cRaCyiwZWdK5TZiKgJ.E |
| NonInteractiveAgentRunner (subprocess --print agents) | ref.ap.ad4vG4G2xMPiMHRreoYVr.E |
| AutoRecoveryByAgentUseCase (generic, **V2 — deferred**) | ref.ap.q54vAxzZnmWHuumhIQQWt.E |
| TicketFailureLearningUseCase (cross-try learning) | ref.ap.cI3odkAZACqDst82HtxKa.E |
| PUBLIC.md validation after done signal | ref.ap.THDW9SHzs1x2JN9YP9OYU.E |
| Context window self-compaction (detection, PRIVATE.md, session rotation) | ref.ap.8nwz2AHf503xwq8fKuLcl.E |
| ContextWindowStateReader interface | ref.ap.ufavF1Ztk6vm74dLAgANY.E |

### Dependencies
- Will take dependencies on well established third-party libraries.
- **Depends on asgardCore** (Out/OutFactory logging, ProcessRunner, AsgardCloseable, coroutines)
