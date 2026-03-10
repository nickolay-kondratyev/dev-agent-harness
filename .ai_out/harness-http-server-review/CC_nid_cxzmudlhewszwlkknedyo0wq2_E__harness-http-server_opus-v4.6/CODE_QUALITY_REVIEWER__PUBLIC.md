# Code Quality Review: Harness HTTP Server

**Scope**: `HarnessServer.kt` (KtorHarnessServer), `AgentRequests.kt`, `PortFileManager.kt`, `KtorHarnessServerTest.kt`

---

## Overall Verdict: NOT_READY

One CRITICAL correctness bug (orphaned server on start failure) and one IMPORTANT test reliability issue (leaked server in test). Both are straightforward fixes. The `/agent/question` stub is architecturally consistent as documented, but needs a comment for future implementors.

---

## Findings

### CRITICAL — Server orphaned if `writePort` throws during `start()`

**Issue**: In `KtorHarnessServer.start()`, the Ktor server is started before `engine` and `boundPort` are assigned:

```kotlin
server.start(wait = false)

val resolvedPort = server.engine.resolvedConnectors().first().port
portFileManager.writePort(resolvedPort)   // <-- throws here (e.g., disk full, permission denied)

engine = server        // never reached
boundPort = resolvedPort
```

If `portFileManager.writePort()` throws — or if `resolvedConnectors()` throws — the Ktor server is running and bound to a port, but `engine` is still `null`. The consequence is a three-way breakage:

1. The running server has no handle; it cannot be stopped via `close()` (which returns early on `engine == null`).
2. A subsequent `start()` call passes the `check(engine == null)` guard and starts a *second* server, orphaning the first permanently.
3. The port file is never written, so agents cannot discover the port and the orphaned server is unreachable — but still consuming OS resources.

**Why it matters**: I/O failures (disk full, permissions, path does not exist) are realistic. This is a resource leak and a correctness bug that will be very hard to diagnose in production because no error is logged and the harness will appear to start cleanly on a second attempt while consuming double the resources.

**Recommendation**: Assign `engine` first, before any fallible I/O. Or wrap the entire `writePort` + assignment block in a try/catch that stops the server if setup fails. The simplest correct ordering:

```kotlin
server.start(wait = false)
engine = server   // assign immediately, before any I/O

val resolvedPort = server.engine.resolvedConnectors().first().port
try {
    portFileManager.writePort(resolvedPort)
    boundPort = resolvedPort
} catch (e: Exception) {
    close()  // clean up the already-started server
    throw e
}
```

---

### IMPORTANT — Leaked Ktor server in test on assertion failure

**Issue**: The test "THEN port file is deleted after close" does NOT use the `withServer` helper:

```kotlin
it("THEN port file is deleted after close") {
    val fixture = createFixture()
    fixture.server.start()
    fixture.server.close()              // only called if start() succeeds
    Files.exists(fixture.portFilePath) shouldBe false   // if this assertion fails, no cleanup
}
```

`withServer` wraps everything in try/finally to guarantee `close()` is called. This test calls `start()` and `close()` inline without try/finally. If `close()` itself throws (unlikely but possible), or if the test is reworked to assert after start but before close (e.g., a future THEN added inside the test), the Ktor server will leak for the duration of the test suite run. This can cause port exhaustion or flaky behavior in long CI runs.

Additionally, `OkHttpClient` is instantiated at spec level but never closed. OkHttp holds a thread pool internally. This is a minor resource leak per spec run.

**Why it matters**: Test infrastructure must be as robust as production code. A leaked server in tests masks the very resource-safety properties the code is meant to have. The inconsistency with `withServer` is also a POLS violation — readers expect all tests in this class to follow the same cleanup pattern.

**Recommendation**: Use `withServer` for the close test, restructured to assert after the server has been stopped:

```kotlin
it("THEN port file is deleted after close") {
    val fixture = createFixture()
    fixture.server.start()
    try {
        fixture.server.close()
    } finally {
        // close is idempotent; already called above.
        // No need for additional cleanup here.
    }
    Files.exists(fixture.portFilePath) shouldBe false
}
```

Or use the `withServer` helper with a post-close assertion extracted outside the block. Also add `afterSpec { httpClient.dispatcher.executorService.shutdown() }` for the OkHttpClient.

---

### IMPORTANT — `/agent/question` stub has no comment about deferred blocking behavior

**Issue**: The architecture design (CLAUDE.md) states that `/agent/question` will eventually block the curl call until a human answers. The current stub returns 200 immediately. The shell CLI script (`harness-cli-for-agent.sh`) even has a `--max-time 30` cap and documents that the answer arrives via TMUX, which is consistent with the current fire-and-forget model. However, the server-side handler has no comment explaining this deferred intent:

```kotlin
post("/question") { handleAgentRequest<AgentQuestionRequest>("/agent/question") }
```

The existing KDoc on the class says "4 stub POST endpoints" but does not distinguish that `/agent/question` has fundamentally different *future* behavior (suspend-and-wait for human reply) from the other three stubs (which are essentially just event receivers).

**Why it matters**: When `/agent/question` is wired up in a future ticket, the implementor has no signal that this endpoint needs special treatment — it looks identical to `/done`, `/failed`, and `/status`. The risk is that a future engineer treats it as another event receiver and misses the blocking/response requirement entirely.

**Recommendation**: Add a TODO comment directly on the question route explaining the intended behavior:

```kotlin
// TODO(ap.NAVMACFCbnE7L6Geutwyk.E): /agent/question must eventually block until a human
// provides an answer, then return the answer in the response body. The answer is
// delivered back to the agent via TMUX send-keys. For now this is a stub that returns 200.
post("/question") { handleAgentRequest<AgentQuestionRequest>("/agent/question") }
```

---

### OPTIONAL — Concurrency: `start()` and `close()` share mutable state without synchronization

**Issue**: `engine` and `boundPort` are unsynchronized `var` fields. The `check(engine == null)` guard in `start()` is a non-atomic read-check-write sequence. Concurrent calls to `start()` from two coroutines could both see `null`, both create servers, and both proceed — resulting in two running Ktor servers with only the second one tracked in `engine`.

**Why it matters**: In V1 this is a serial CLI tool with a single coroutine caller, so the practical risk is essentially zero. It is called out because as the codebase grows, callers may not be aware of this constraint, and the class gives no indication it is not thread-safe.

**Recommendation**: For V1, add a KDoc note that `start()` and `close()` are not thread-safe and must be called from the same coroutine scope. If thread safety is needed later, a `Mutex` from `kotlinx.coroutines.sync` would be the idiomatic Kotlin coroutines solution.

---

## Non-Issues (examined, cleared)

- **`private inline fun` in class**: Kotlin correctly inlines `reified` type parameters for class member extension functions. Access to `out` (a private member of the enclosing class) is valid at the call site after inlining. No issue.
- **`/agent/question` immediate 200**: Consistent with the shell script's documented fire-and-forget model (`--max-time 30`, answer via TMUX). The stub behavior is correct *for V1*. The issue is only the missing comment (see IMPORTANT finding above).
- **`withServer` as `suspend fun` called from `it` blocks**: Kotest's `it` blocks are suspend contexts. `withServer` is correctly callable here.
- **Constant naming**: `GRACEFUL_SHUTDOWN_PERIOD_MILLIS` and `SHUTDOWN_TIMEOUT_MILLIS` are defined in the companion but used correctly in `close()`. No mismatch.
