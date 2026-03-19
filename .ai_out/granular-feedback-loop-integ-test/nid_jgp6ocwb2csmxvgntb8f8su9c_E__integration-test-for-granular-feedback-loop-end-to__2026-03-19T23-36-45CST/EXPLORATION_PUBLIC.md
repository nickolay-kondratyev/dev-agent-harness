# Exploration: Granular Feedback Loop Integration Test

## Ticket
Gate 6 of granular feedback loop spec (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).
Sanity check — unit tests provide primary coverage.

## Dependencies — All CLOSED
1. **nid_fq8wn0eb9yrvzcpzdurlmsg7i_E** — InnerFeedbackLoop, per-item processing, severity ordering, file movement
2. **nid_yzmwosyazxksnr1hafmw87x1m_E** — PartCompletionGuard validates no pending critical/important on PASS
3. **nid_gp9rduvxoqf14m95z9bttnaxq_E** — FeedbackItem, StructuredFeedbackFormat, FeedbackWritingInstructions, FeedbackDirectorySection

## Key Classes
- `InnerFeedbackLoop(deps: InnerFeedbackLoopDeps)` — per-item feedback processing (critical→important→optional)
- `PartCompletionGuard` — blocks completion if critical/important in pending/
- `RejectionNegotiationUseCaseImpl` — per-item rejection negotiation (1 round, reviewer authority)
- `FeedbackResolutionParser` — parses `## Resolution:` markers (ADDRESSED/REJECTED/SKIPPED)
- `PartExecutorImpl` — main orchestrator, `innerFeedbackLoop` is optional param in `PartExecutorDeps`
- `FakeAgentFacade` — fail-hard programmable fake with call recording

## Test Infrastructure
- `SharedContextDescribeSpec` — base class for integ tests, provides ShepherdContext
- `isIntegTestEnabled()` — gates describe blocks via `-PrunIntegTests=true`
- `IntegTestCallbackProtocol` — CORE_PROTOCOL, SELF_COMPACTION_PROTOCOL, BOOTSTRAP_MESSAGE
- `IntegTestHelpers` — resolveCallbackScriptsDir(), createSystemPromptFile(), createDoneInstructionFile()
- `ServerPortInjectingAdapter` — wraps real adapter with port injection

## Feedback File Structure
- `__feedback/pending/` — severity-prefixed files (critical__, important__, optional__)
- `__feedback/addressed/` — items resolved by doer
- `__feedback/rejected/` — items where reviewer accepted rejection

## Test Approach
Use FakeAgentFacade with real component wiring (InnerFeedbackLoop, PartCompletionGuard, etc.) to test orchestration deterministically. This is a "wired integration test" that validates all components work together.

Covers:
1. Reviewer writes feedback files with severity prefixes → harness picks them up in order
2. Doer receives one item at a time → doer marks ADDRESSED → file moves to addressed/
3. Rejection negotiation (REJECTED → reviewer judgment → acceptance or insistence)
4. Part completion guard (no critical/important on PASS)
5. Iteration counter: once per needs_iteration, not per item
6. Feedback files presence guard (empty pending → crash)

## Existing Test Patterns to Follow
- `SelfCompactionIntegTest` — real GLM agent E2E
- `AgentFacadeImplIntegTest` — real GLM agent E2E
- `InnerFeedbackLoopTest` — unit test with FakeAgentFacade

## Key Decision
The ticket scope says "real agent (or GLM fake)". Given this is a sanity check:
- Use FakeAgentFacade for deterministic orchestration testing of all feedback loop components wired together
- This validates the wiring/orchestration without GLM flakiness
- Unit tests already cover individual components; this test covers the INTEGRATION between them
