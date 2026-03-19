---
id: nid_5z93biuqub3mhcejfpofjmj39_E
title: "Implement PartExecutorImpl — core doer/reviewer execution loop with FakeAgentFacade tests"
status: open
deps: [nid_g3e2bkvqepf2wzipadayt9in8_E, nid_m7oounvwb31ra53ivu7btoj5v_E, nid_fwf09ycnd4d8wdoqd1atuohgb_E, nid_smb6zudqraf0hkp3u9kjx855e_E, nid_o9j8yo1yf76iwrj1x12u19t0z_E]
links: []
created_iso: 2026-03-19T00:34:27Z
status_updated_iso: 2026-03-19T18:39:24Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [core, part-executor, implementation]
---

## Context

Spec: `doc/core/PartExecutor.md` (ref.ap.mxIc5IOj6qYI7vgLcpQn5.E)
Also: `doc/core/AgentFacade.md` Gate 3 (ref.ap.9h0KS4EOK5yumssRCJdbq.E)

This ticket covers the core PartExecutorImpl implementation — the doer-only and doer+reviewer execution paths, EXCLUDING the inner granular feedback loop (which has its own ticket nid_fq8wn0eb9yrvzcpzdurlmsg7i_E).

## What to Implement

Location: `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt`

### Constructor Dependencies (R6 from AgentFacade spec)

PartExecutor takes `AgentFacade` as its SOLE agent-facing dependency. No Clock, SessionsState, AgentUnresponsiveUseCase, or ContextWindowStateReader in constructor.

```kotlin
class PartExecutorImpl(
    private val doerConfig: SubPartConfig,
    private val reviewerConfig: SubPartConfig?,  // null = no reviewer
    private val agentFacade: AgentFacade,
    private val contextForAgentProvider: ContextForAgentProvider,
    private val gitCommitStrategy: GitCommitStrategy,
    private val failedToConvergeUseCase: FailedToConvergeUseCase,
    // iteration config
) : PartExecutor
```

### Paths to Implement

1. **Doer-only path** (reviewerConfig == null): spawn → send → await signal → PUBLIC.md validation → map to PartResult
2. **Doer+reviewer path**: spawn doer → send → await COMPLETED → spawn reviewer → send → await PASS/NEEDS_ITERATION → iteration loop
3. **PUBLIC.md validation** (ref.ap.THDW9SHzs1x2JN9YP9OYU.E) after every AgentSignal.Done
4. **Iteration budget enforcement** → FailedToConvergeUseCase
5. **Signal-to-PartResult mapping** for both paths
6. **Context window check at done boundaries** via agentFacade.readContextWindowState()

### Unit Tests (Gate 3 scenarios — all using FakeAgentFacade)

- Happy path: spawn → work → done → completed
- Doer+reviewer: doer COMPLETED → reviewer PASS → PartResult.Completed  
- Iteration: doer → reviewer NEEDS_ITERATION → doer → reviewer PASS
- FailWorkflow signal → PartResult.FailedWorkflow
- Crashed signal → PartResult.AgentCrashed
- Iteration budget exceeded → FailedToConverge path
- Missing PUBLIC.md after done → immediate AgentCrashed
- Context window low at done boundary → self-compaction triggered

## Acceptance Criteria

- PartExecutorImpl constructor has NO Clock, SessionsState, AgentUnresponsiveUseCase, or ContextWindowStateReader (R6 verification)
- All unit tests use FakeAgentFacade — fast, deterministic, no real infra
- All listed test scenarios pass
- `./test.sh` passes

