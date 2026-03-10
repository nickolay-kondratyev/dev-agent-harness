# Exploration: AgentRequestHandler Injection Boundary in KtorHarnessServer

## Key Files

- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/HarnessServer.kt` — `HarnessServer` interface + `KtorHarnessServer` implementation
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/AgentRequests.kt` — request data classes (AgentDoneRequest, AgentQuestionRequest, AgentFailedRequest, AgentStatusRequest)
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/PortFileManager.kt` — `PortPublisher` interface + `PortFileManager`
- `app/src/test/kotlin/com/glassthought/chainsaw/core/server/KtorHarnessServerTest.kt` — full test suite

## Current State

`KtorHarnessServer` has:
- Constructor: `KtorHarnessServer(outFactory: OutFactory, portPublisher: PortPublisher)`
- 4 routes: `/agent/done`, `/agent/question`, `/agent/failed`, `/agent/status`
- All handled via `handleAgentRequest<T>(path)` — receives request, logs it, responds `{"status":"ok"}`
- Comment in code marks `/agent/question` as STUB needing future suspend-until-human-answers behavior

`KtorHarnessServer` is only constructed in the test (`KtorHarnessServerTest`). Not yet wired in `AppMain` or `Initializer`.

## Required Changes

1. **New file** `AgentRequestHandler.kt`:
   - `interface AgentRequestHandler` with 4 suspend methods
   - `class NoOpAgentRequestHandler` — placeholder with no behavior

2. **Modified** `HarnessServer.kt`:
   - Inject `agentRequestHandler: AgentRequestHandler` into `KtorHarnessServer`
   - Route each endpoint through the handler
   - `/question` returns handler's String answer in response body

3. **Modified** `KtorHarnessServerTest.kt`:
   - Pass `NoOpAgentRequestHandler()` to `KtorHarnessServer` constructor
   - Add tests verifying the handler is invoked

## Design Notes

- `NoOpAgentRequestHandler` needs no `OutFactory` (truly no-op)
- `onQuestion` returns `String` — response body: `{"answer": "<the string>"}`
- Keep DRY: use `handleAgentRequest` helper with action lambda for done/failed/status
- The question endpoint has different response shape, handled separately or via the lambda approach
