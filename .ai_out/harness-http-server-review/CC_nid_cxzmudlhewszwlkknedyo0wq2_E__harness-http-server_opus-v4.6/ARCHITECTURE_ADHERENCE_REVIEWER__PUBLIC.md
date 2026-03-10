# Architecture Adherence Review: Harness HTTP Server

**Verdict: READY**

The implementation is architecturally sound. All V1 endpoints are present, the design aligns with
CLAUDE.md, and constructor injection is used correctly throughout. Findings below are ordered by
severity — the one IMPORTANT item must be addressed before the server delivers business value.

---

## Findings

### IMPORTANT: Endpoint handlers are stubs — no business logic is wired

**Issue:**
`handleAgentRequest` receives the typed request, logs it, and immediately returns `{"status": "ok"}`.
None of the four endpoints trigger their documented downstream effect:

- `/agent/done` — should signal task completion to the phase runner
- `/agent/question` — should block the curl caller until a human provides an answer (documented as
  "curl blocks until human answers")
- `/agent/failed` — should invoke `FailedToExecutePlanUseCase`
- `/agent/status` — should reply to the health ping (correct behavior: 200 is the reply, this one is fine)

**Why it matters:**
The `/agent/question` contract is the most severe gap. CLAUDE.md explicitly requires the HTTP
response to be withheld until a human answers. Returning 200 immediately breaks the agent's
blocking-wait assumption and will cause silent data loss — the agent will move on before the
question is answered. This is not a logging gap; it is a missing behavioral contract.

**Recommendation:**
The server needs a `RequestHandler` (or similar) interface injected into `KtorHarnessServer` that
the routing layer delegates to. Each endpoint delegates to a method on that interface. This keeps
`KtorHarnessServer` responsible only for HTTP protocol concerns (SRP) while the phase runner wires
in the real handler. A no-op implementation is an acceptable placeholder during incremental
development, but it must be explicit (not silent) and covered by a follow-up ticket.

Example boundary:

```kotlin
interface AgentRequestHandler {
    suspend fun onDone(request: AgentDoneRequest)
    suspend fun onQuestion(request: AgentQuestionRequest): String  // returns answer
    suspend fun onFailed(request: AgentFailedRequest)
    suspend fun onStatus(request: AgentStatusRequest)
}
```

`KtorHarnessServer` receives `AgentRequestHandler` via constructor injection and delegates each
route to it. The `/agent/question` route suspends on `onQuestion` before responding, which is the
only way to honour the blocking-curl contract.

---

### OPTIONAL: `PortFileManager` is a concrete class, not behind an interface

**Issue:**
`KtorHarnessServer` depends directly on `PortFileManager` (a concrete class), not on an interface.
CLAUDE.md states "We Like Interfaces" (DIP). The concrete dependency is also visible in the
`KtorHarnessServerTest`, where the real `PortFileManager` is instantiated, making the test a
light integration test rather than a unit test.

**Why it matters:**
If port-file behaviour needs to change (e.g., use a different storage backend, write atomically,
or emit an event), `KtorHarnessServer` must be modified. With an interface, `KtorHarnessServer`
would be closed to that modification (OCP).

**Recommendation:**
Extract a `PortPublisher` interface with `publish(port: Int)` and `unpublish()`. The current
`PortFileManager` becomes `FilePortPublisher : PortPublisher`. This is a small, clean break.
The test fixture becomes trivially mockable.

Note: this is OPTIONAL because the current design is functional, the risk of the coupling is
low in a single-deployment CLI, and introducing the interface without the handler boundary
(the IMPORTANT item above) is premature.

---

### OPTIONAL: Ktor dependency version pinning is inconsistent with project conventions

**Issue:**
`app/build.gradle.kts` pins Ktor at `3.1.1` using inline version strings, while Jackson (also
a structured serialization library in active development) is pinned at `2.17.2` inline. Both
patterns are identical, so this is consistent within the file. However, neither is managed via the
version catalog (`libs.versions.toml`), which is used for Kotlin, Guava, and Kotest.

**Why it matters:**
Inline strings for two major serialization libraries (Jackson + Ktor) means version drift is a
manual grep exercise rather than a single-source update. Low risk today; higher maintenance
burden as the dependency surface grows.

**Recommendation:**
Move `ktor.*` and `jackson.*` coordinates into `libs.versions.toml`. This is a housekeeping
item with no architectural urgency.

---

## Architecture Alignment Summary

| Requirement (CLAUDE.md) | Status |
|---|---|
| Ktor CIO, port 0 (OS-assigned) | Correct |
| Port written to `$HOME/.chainsaw_agent_harness/server/port.txt` | Correct |
| Server starts once, stays alive across phases | Structure is present; lifecycle managed via `AsgardCloseable` |
| POST /agent/done, /question, /failed, /status present | Endpoints exist, handlers are stubs |
| All requests include git branch as identifier | Correct — `branch` field on every request type |
| Constructor injection only, no DI framework | Correct |
| AsgardCore Out/OutFactory logging | Correct |
| Jackson + Kotlin module for serialization | Correct |
| AsgardCloseable resource management | Correct |
| BDD tests with GIVEN/WHEN/THEN | Correct |
| One assert per test | Correct |
