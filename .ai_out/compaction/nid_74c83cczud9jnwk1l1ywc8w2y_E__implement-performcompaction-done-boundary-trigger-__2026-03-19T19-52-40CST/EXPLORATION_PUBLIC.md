# Exploration: Compaction in PartExecutorImpl

## Key Findings

### Components That Exist (Ready to Use)
| Component | Path | Notes |
|-----------|------|-------|
| PartExecutorImpl | `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImpl.kt` | Needs compaction logic added |
| AgentFacade | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentFacade.kt` | All 4 methods ready |
| FakeAgentFacade | `app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacade.kt` | Programmable fake |
| AgentSignal.SelfCompacted | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentSignal.kt` | Already defined |
| ContextWindowState | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/ContextWindowState.kt` | `data class(remainingPercentage: Int?)` |
| SelfCompactionInstructionBuilder | `app/src/main/kotlin/com/glassthought/shepherd/core/compaction/SelfCompactionInstructionBuilder.kt` | Fully implemented |
| HarnessTimeoutConfig | `app/src/main/kotlin/com/glassthought/shepherd/core/data/HarnessTimeoutConfig.kt` | Has `selfCompactionTimeout`, `contextWindowSoftThresholdPct=35` |
| GitCommitStrategy | `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/GitCommitStrategy.kt` | `fun interface` |
| PublicMdValidator | `app/src/main/kotlin/com/glassthought/shepherd/core/executor/PublicMdValidator.kt` | Pattern reference for PRIVATE.md validation |
| SubPartConfig | `app/src/main/kotlin/com/glassthought/shepherd/core/executor/SubPartConfig.kt` | Has `privateMdPath: Path?` |
| AgentPayload | `app/src/main/kotlin/com/glassthought/shepherd/core/agent/facade/AgentPayload.kt` | Used by sendPayloadAndAwaitSignal |

### Components That Need Creating
- **CompactionTrigger enum** — does NOT exist yet
- **Compaction logic in PartExecutorImpl** — `afterDone()` reads context window state but only logs, no threshold check

### Critical Implementation Notes
1. **SelfCompacted signal**: Currently handled with `error(SELF_COMPACTED_UNEXPECTED)` in signal-mapping methods — correct for normal flow, stays as-is. During compaction, SelfCompacted comes from `sendPayloadAndAwaitSignal` directly (not through signal mapping).
2. **Handle mutability**: Doer-only uses `val handle`, doer+reviewer uses `var doerHandle`/`var reviewerHandle`. Compaction needs handle to become null for lazy respawn.
3. **afterDone()** already calls `agentFacade.readContextWindowState(handle)` — needs threshold check added.
4. **PartExecutorDeps** needs `HarnessTimeoutConfig` (or threshold value) and `SelfCompactionInstructionBuilder`.
5. **Session rotation** after compaction: next iteration detects `handle == null`, spawns new session. PRIVATE.md auto-included by ContextForAgentProvider.
6. **Tests**: 897-line test file exists. No compaction tests yet. Uses BDD style with FakeAgentFacade.

### Spec Reference
- Compaction spec: `doc/use-case/ContextWindowSelfCompactionUseCase.md` (ref.ap.8nwz2AHf503xwq8fKuLcl.E)
- PartExecutor spec: ref.ap.fFr7GUmCYQEV5SJi8p6AS.E
- PartExecutorImpl: ref.ap.mxIc5IOj6qYI7vgLcpQn5.E
