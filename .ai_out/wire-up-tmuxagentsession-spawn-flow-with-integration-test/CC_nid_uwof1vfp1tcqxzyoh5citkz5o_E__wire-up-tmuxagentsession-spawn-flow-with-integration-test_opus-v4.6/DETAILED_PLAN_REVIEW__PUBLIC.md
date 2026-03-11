# Plan Review: Wire up TmuxAgentSession Spawn Flow

## Executive Summary

The plan is well-structured, demonstrates strong understanding of the existing codebase, and correctly decomposes the problem into clean phases with proper SRP boundaries. There are two major concerns: (1) `AgentStarterBundleFactory.create()` signature diverges from the ticket's design without justification, and (2) Phase 8 (ResumeTmuxAgentSessionUseCase) is under-specified and should either be fully designed or explicitly deferred. The plan is otherwise sound and can proceed with minor revisions.

## Critical Issues (BLOCKERS)

None.

## Major Concerns

### 1. AgentStarterBundleFactory.create() Signature Mismatch

- **Concern**: The ticket specifies `fun create(agentType: AgentType, phaseType: PhaseType): AgentStarterBundle`. The plan changes this to `fun create(agentType: AgentType, request: StartAgentRequest): AgentStarterBundle`, passing the entire request object instead of just `phaseType`.
- **Why**: The ticket's design intentionally states "sessionIdResolver is agent-type-scoped (ignores phaseType internally)" -- the factory needs `agentType` and `phaseType` as separate concerns. Passing the whole `StartAgentRequest` leaks the request abstraction into the factory layer and creates an unnecessary coupling. The factory does not need `workingDir` -- that is the starter's concern, and the starter is configured via constructor injection.
- **Wait**: Actually, re-reading the plan, `ClaudeCodeAgentStarter` takes `workingDir` as a constructor param, and the factory creates the starter. So the factory DOES need `workingDir` from the request. This means the signature change is justified -- the factory needs more than just `phaseType`.
- **Resolution**: The signature change is acceptable IF the plan explicitly documents WHY `request` is passed instead of `phaseType` (because the factory needs `workingDir` to construct the starter). The plan mentions this in passing ("Design decision: The factory takes request (not just phaseType) to allow future extensibility") but should be clearer about the concrete reason: `workingDir` is needed NOW, not just "future extensibility."
- **Suggestion**: Clarify the rationale. Minor adjustment -- not blocking.

### 2. Phase 8 (ResumeTmuxAgentSessionUseCase) Is Under-Specified

- **Concern**: Phase 8 sketches the resume use case but then says "No integration test per ticket scope. A simple unit test or deferred to future ticket." The flow has a hard-coded `claude --resume <sessionId>` command with no `AgentStarter` or abstraction, which breaks the pattern established by the spawn flow (where `AgentStarter` builds the command).
- **Why**: If resume is in scope, it needs proper design. If it is out of scope, it should be clearly deferred to a follow-up ticket rather than half-implemented. A half-implementation with no tests and hard-coded commands is the kind of thing that creates tech debt.
- **Suggestion**: Either (a) defer Phase 8 entirely to a follow-up ticket with a clear TODO, or (b) design it properly with an `AgentResumer` interface parallel to `AgentStarter`. Given PARETO, option (a) is strongly preferred -- the resume flow is secondary scope per the clarification document, and a stub with no test is worse than no code at all.

## Simplification Opportunities (PARETO)

### 1. TmuxAgentSession Does Not Need an Interface

- **Current approach**: Interface `TmuxAgentSession` + `DefaultTmuxAgentSession` data class.
- **Simpler alternative**: Just a `data class TmuxAgentSession(val tmuxSession: TmuxSession, val resumableAgentSessionId: ResumableAgentSessionId)`.
- **Value**: The plan's rationale is "Interface allows different creation paths (spawn vs resume) to produce the same type." But different creation paths (spawn vs resume) do NOT produce different implementations -- they both produce the same data. A data class is sufficient. If a genuine need for polymorphism arises later, an interface can be extracted at that time (YAGNI). This follows the codebase pattern: `ResumableAgentSessionId` is a plain data class, not an interface.
- **Impact**: Eliminates one unnecessary abstraction, reduces cognitive load.

### 2. AgentTypeChooser is Arguably Premature

- **Current approach**: `AgentTypeChooser` interface + `DefaultAgentTypeChooser` that always returns `CLAUDE_CODE` + unit test.
- **Simpler alternative**: In V1, `SpawnTmuxAgentSessionUseCase` could simply hardcode `AgentType.CLAUDE_CODE` and document the extension point with a comment. When a second agent type is needed, extract the interface then.
- **Counter-argument**: The interface is small and cheap, and aligns with OCP. The plan already acknowledges it is trivial. This is a judgment call -- keeping it is acceptable.
- **Verdict**: Keep it. The cost is low and it follows OCP per project standards.

### 3. System Prompt File Handling Needs Clarity

- **Current approach**: Phase 6 first says `app/src/main/resources/prompts/test-agent-system-prompt.txt`, then backtracks to `config/prompts/test-agent-system-prompt.txt`, then says the factory receives `testSystemPromptFilePath: String?`.
- **Issue**: The plan contradicts itself about where the file lives and how the path is resolved. The final decision section says `config/prompts/test-agent-system-prompt.txt` but this is still ambiguous about how the absolute path is resolved at runtime.
- **Suggestion**: Keep it simple: the integration test constructs the absolute path using `System.getProperty("user.dir") + "/config/prompts/test-agent-system-prompt.txt"`. The factory receives the path as a constructor param. Document this clearly once, remove the back-and-forth.

## Minor Suggestions

### 1. Test Package Placement

The integration test is placed at `com.glassthought.chainsaw.core.agent` but existing integration tests are at `org.example` (see `TmuxSessionManagerIntegTest`, `TmuxCommunicatorIntegTest`). For consistency, the new integration test should follow the existing pattern -- or there should be a deliberate decision to migrate to the new package structure. Mixing both is inconsistent.

**Recommendation**: Place the integration test in `org.example` for consistency with existing tests, OR note that this is a deliberate migration to proper package placement and commit to migrating the existing tests in a follow-up.

### 2. Unit Test for SpawnTmuxAgentSessionUseCase

The plan initially considers mocking, then decides on "one focused unit test that verifies the session name format" plus relying on the integration test. Verifying session name format is a brittle test on an implementation detail. If the use case is a thin orchestrator, skip the unit test entirely and rely on the integration test. Do not test implementation details.

**Recommendation**: Drop the unit test for `SpawnTmuxAgentSessionUseCase`. The integration test is the real verification for an orchestrator. A unit test that only checks a string format adds maintenance burden without value.

### 3. ClaudeCodeAgentStarterBundleFactory Constructor Confusion

The plan says the factory takes `environment: Environment` and `outFactory: OutFactory`, but then says it also takes `testSystemPromptFilePath: String?`. The constructor param list is not clearly finalized. The plan should present a single definitive constructor.

**Recommendation**: Finalize as:
```kotlin
class ClaudeCodeAgentStarterBundleFactory(
    private val environment: Environment,
    private val systemPromptFilePath: String?,
    private val outFactory: OutFactory,
) : AgentStarterBundleFactory
```

### 4. Missing `--dangerously-skip-permissions` Rationale in Production

The plan states both test and production use `--dangerously-skip-permissions`. This is correct per the project overview (agents in tmux must be non-interactive), but the plan should explicitly confirm this as a conscious decision, not an oversight.

### 5. `sendKeys` Timing Discussion is Valuable But Over-Long

The GUID handshake timing section (Section 4) is good analysis but ultimately arrives at "start without a delay" which is the obvious default. The analysis is valuable for the implementor but could be condensed.

## Strengths

1. **Thorough exploration of existing code**: The plan accurately maps all existing interfaces (`TmuxSessionManager.createSession`, `AgentSessionIdResolver.resolveSessionId`, `HandshakeGuid`, etc.) and correctly builds on top of them.

2. **Clear phase decomposition**: Each phase has well-defined inputs, outputs, and dependencies. The implementor can work through them sequentially without ambiguity.

3. **Correct integration test pattern**: Uses `isIntegTestEnabled()`, `@OptIn(ExperimentalKotest::class)`, `afterEach` cleanup, extends `AsgardDescribeSpec` -- all matching the established patterns in `TmuxSessionManagerIntegTest` and `TmuxCommunicatorIntegTest`.

4. **Pragmatic test consolidation**: The decision to use a single `it` block with multiple assertions for the expensive integration test is the right call. Spawning 3 Claude sessions to satisfy "one assert per test" would be wasteful.

5. **Good CLI flag documentation**: The Claude Code CLI reference section is well-researched and distinguishes between `--system-prompt-file` vs `--append-system-prompt-file`, `--tools` vs `--allowedTools`, etc.

6. **Correct error handling philosophy**: "Let exceptions propagate, don't catch and wrap" -- aligns with the don't-log-and-throw principle.

7. **Environment.test() factory method**: Properly identified that `TestEnvironment` is `internal` and a public factory method is needed. Follows the existing `production()` pattern.

## Inline Adjustments Made

1. None -- all feedback is documented above for the planner to address.

## Verdict

- [ ] APPROVED
- [x] APPROVED WITH MINOR REVISIONS
- [ ] NEEDS REVISION
- [ ] REJECTED

### Required Revisions Before Implementation

1. **Resolve Phase 8**: Either defer `ResumeTmuxAgentSessionUseCase` entirely to a follow-up ticket, or design it properly. Do not half-implement.
2. **Simplify TmuxAgentSession**: Use a plain data class instead of interface + impl. Extract interface when needed.
3. **Finalize system prompt file handling**: Remove the back-and-forth; state the final approach once clearly.
4. **Clarify `AgentStarterBundleFactory.create()` rationale**: The `request` parameter is justified (needs `workingDir`), but say so explicitly.

### Signal to Top-Level Agent

These revisions are minor and can be addressed inline during implementation. **PLAN_ITERATION can be skipped** -- the implementor should incorporate these adjustments directly.
