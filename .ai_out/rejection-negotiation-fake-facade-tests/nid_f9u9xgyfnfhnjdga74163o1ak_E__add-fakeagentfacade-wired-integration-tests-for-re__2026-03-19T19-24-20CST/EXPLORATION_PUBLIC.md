# Exploration Summary

## Task
Add FakeAgentFacade-wired integration tests for RejectionNegotiationUseCaseImpl — testing the full composition: FakeAgentFacade → ReInstructAndAwaitImpl → RejectionNegotiationUseCaseImpl.

## Architecture

### Wiring Chain
```
RejectionNegotiationUseCaseImpl(reInstructAndAwait, feedbackFileReader, outFactory)
  └── ReInstructAndAwaitImpl(agentFacade)
        └── FakeAgentFacade (test double)
```

### Key Types
- **RejectionNegotiationUseCase** (fun interface): `execute(doerHandle, reviewerHandle, feedbackFilePath) → RejectionResult`
- **RejectionResult** (sealed): `Accepted`, `AddressedAfterInsistence`, `AgentCrashed(details)`, `FailedWorkflow(reason)`
- **ReInstructAndAwait** (fun interface): `execute(handle, message) → ReInstructOutcome`
- **ReInstructOutcome** (sealed): `Responded(signal: AgentSignal.Done)`, `FailedWorkflow(reason)`, `Crashed(details)`
- **ReInstructAndAwaitImpl**: Maps AgentSignal → ReInstructOutcome
- **FakeAgentFacade**: Programmable test double with fail-hard defaults and call recording
  - `onSendPayloadAndAwaitSignal(handler)` — programs response behavior
  - `sendPayloadCalls` — records all calls for verification
  - Handler is a lambda `(SpawnedAgentHandle, AgentPayload) → AgentSignal`

### Signal Mapping (AgentSignal → ReInstructOutcome → RejectionResult)
- `AgentSignal.Done(PASS)` → `Responded(Done(PASS))` → `Accepted`
- `AgentSignal.Done(NEEDS_ITERATION)` → `Responded(Done(NEEDS_ITERATION))` → triggers doer compliance flow
- `AgentSignal.Done(COMPLETED)` → `Responded(Done(COMPLETED))` → `AgentCrashed` (unexpected for reviewer)
- `AgentSignal.Crashed` → `Crashed` → `AgentCrashed`
- `AgentSignal.FailWorkflow` → `FailedWorkflow` → `FailedWorkflow`

### FeedbackFileReader
- Functional interface: `suspend (Path) → String`
- In tests: closure-based fake, can return different content on successive reads using a counter

### Existing Test Patterns (from RejectionNegotiationUseCaseImplTest.kt)
- BDD GIVEN/WHEN/THEN via nested describe/it blocks
- Helper builders: `buildHandle(name)`, `buildFileReader(first, second)`, `buildSut(reInstructAndAwait, feedbackFileReader)`
- `FakeReInstructAndAwait`: dispatches by handle guid, records calls
- One assert per `it` block

### Key Files
- Source: `app/src/main/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCase.kt`
- Source: `app/src/main/kotlin/com/glassthought/shepherd/usecase/reinstructandawait/ReInstructAndAwait.kt`
- Fake: `app/src/test/kotlin/com/glassthought/shepherd/core/agent/facade/FakeAgentFacade.kt`
- Existing tests: `app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationUseCaseImplTest.kt`
- FakeAgentFacade usage: `app/src/test/kotlin/com/glassthought/shepherd/usecase/reinstructandawait/ReInstructAndAwaitImplTest.kt`

## New Test Class Target
`app/src/test/kotlin/com/glassthought/shepherd/usecase/rejectionnegotiation/RejectionNegotiationWithFakeAgentFacadeTest.kt`

## Key Challenge
FakeAgentFacade's `onSendPayloadAndAwaitSignal` handler receives ALL calls. For multi-step flows (reviewer then doer), the handler must dispatch based on the handle or use an ArrayDeque to queue sequential responses.
