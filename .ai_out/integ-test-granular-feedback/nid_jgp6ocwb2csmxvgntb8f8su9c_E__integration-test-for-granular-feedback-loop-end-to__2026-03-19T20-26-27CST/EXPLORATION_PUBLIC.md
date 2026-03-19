# Exploration: Integration Test for Granular Feedback Loop

## Dependency Status
All 3 dependency tickets are **CLOSED** — implementation complete:
- `nid_fq8wn0eb9yrvzcpzdurlmsg7i_E` — InnerFeedbackLoop in PartExecutorImpl
- `nid_yzmwosyazxksnr1hafmw87x1m_E` — Part Completion Guard
- `nid_gp9rduvxoqf14m95z9bttnaxq_E` — Feedback-loop InstructionSection subtypes

## Key Implementation Files
- `InnerFeedbackLoop.kt` — inner loop orchestration (processes feedback files in severity order)
- `PartCompletionGuard.kt` — Gate 5 R8 validation
- `PartExecutorImpl.kt` — outer loop, wires InnerFeedbackLoop as optional dep
- `FeedbackResolutionParser.kt` — parses resolution markers
- `RejectionNegotiationUseCase.kt` — per-item rejection negotiation
- `InstructionSection.kt` — FeedbackItem, StructuredFeedbackFormat, FeedbackWritingInstructions, FeedbackDirectorySection subtypes

## Existing Unit Test Coverage
Comprehensive unit tests exist in:
- `InnerFeedbackLoopTest.kt` — R9 guard, severity ordering, ADDRESSED, SKIPPED, REJECTED→delegation, multi-item
- `PartCompletionGuard.kt` — PASS with critical→crash, PASS with optional→pass
- `RejectionNegotiationUseCaseImplTest.kt` — full negotiation scenarios
- `FeedbackResolutionParserTest.kt` — parser edge cases

## Integration Test Pattern
- Extends `SharedContextDescribeSpec` for shared `ShepherdContext`
- Gated with `.config(isIntegTestEnabled())`
- `@OptIn(ExperimentalKotest::class)`
- GLM injection via `GlmConfig` is working (already validated by `ClaudeCodeAdapterSpawnIntegTest`)

## Key Finding: AgentFacadeImpl Wiring Gap
`ShepherdContext` provides infrastructure (tmux, adapter) but NOT higher-level wiring (AgentFacade, ContextForAgentProvider, InnerFeedbackLoop). AgentFacadeImpl requires SessionsState, TmuxSessionCreator, SingleSessionKiller, ContextWindowStateReader, Clock, HarnessTimeoutConfig — none wired in test infra.

`PartExecutorDeps.innerFeedbackLoop` is `null` by default and must be explicitly wired.

Running a full end-to-end test with real agents requires the full AgentFacade stack which is not yet available in the integration test infrastructure.

## Options
1. **Full e2e with real agents**: Requires wiring AgentFacadeImpl + HTTP server in test infra (significant)
2. **Component integration with FakeAgentFacade**: Tests real filesystem + real ContextForAgentProvider + full flow composition (pragmatic, still adds value beyond unit tests)
