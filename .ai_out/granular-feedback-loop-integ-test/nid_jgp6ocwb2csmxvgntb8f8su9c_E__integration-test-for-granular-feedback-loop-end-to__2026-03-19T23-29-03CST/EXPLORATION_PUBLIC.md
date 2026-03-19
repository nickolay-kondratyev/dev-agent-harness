# Exploration: Granular Feedback Loop Integration Test

## Ticket
Gate 6 of granular feedback loop spec (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E).

## Dependency Tickets — All CLOSED
1. **nid_fq8wn0eb9yrvzcpzdurlmsg7i_E** (Inner Feedback Loop) — InnerFeedbackLoop, per-item processing, severity ordering, file movement
2. **nid_yzmwosyazxksnr1hafmw87x1m_E** (Part Completion Guard) — validates no pending critical/important on PASS
3. **nid_gp9rduvxoqf14m95z9bttnaxq_E** (Instruction Sections) — FeedbackItem, StructuredFeedbackFormat, FeedbackWritingInstructions, FeedbackDirectorySection

## Key Classes
- `PartExecutorImpl` — main orchestrator with doer+reviewer path, inner feedback loop
- `InnerFeedbackLoop` — per-item feedback processing (critical→important→optional)
- `PartCompletionGuard` — blocks completion if critical/important in pending/
- `RejectionNegotiationUseCase` — per-item rejection negotiation
- `FeedbackResolutionParser` — parses `## Resolution:` markers
- `FakeAgentFacade` — programmable fake for testing
- `AgentFacadeImpl` — real implementation with GLM agents

## Test Infrastructure
- `SharedContextDescribeSpec` — base class for integ tests, provides ShepherdContext
- `isIntegTestEnabled()` — gates describe blocks via `-PrunIntegTests=true`
- `FakeAgentFacade` — fail-hard programmable fake with call recording
- GLM agent spawning IS working (see AgentFacadeImplIntegTest)

## Integration Test Scope (Sanity Check)
1. Reviewer writes feedback files with severity prefixes
2. Doer receives one item at a time in severity order
3. Resolution markers parsed and files moved correctly
4. Rejection negotiation works
5. Self-compaction between items (when context low)
6. Iteration counter: once per needs_iteration, not per item
7. Part completion guard: no critical/important on PASS

## Approach Decision Needed
- **Option A**: FakeAgentFacade with real component wiring (fast, deterministic, tests orchestration)
- **Option B**: Real GLM agents via SharedContextDescribeSpec (slow, flaky, but true E2E)
- Ticket says "sanity check" — unit tests provide primary coverage
