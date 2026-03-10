# Consolidated Code Review — Harness HTTP Server

**Branch:** `CC_nid_cxzmudlhewszwlkknedyo0wq2_E__harness-http-server_opus-v4.6`
**Commits reviewed:** `c9d5c6cc..HEAD` (6 commits)
**Overall Verdict:** ⛔ **NOT_READY**

## What Was Built

- `HarnessServer` interface + `KtorHarnessServer` implementation (Ktor CIO, port 0)
- `AgentRequests.kt` — 4 typed request data classes with shared `AgentRequest` interface
- `PortFileManager` — port file write/delete with `DEFAULT_PATH`
- 12 BDD tests (8 server lifecycle + endpoints, 4 port file manager)
- Ktor 3.1.1 dependencies added to `app/build.gradle.kts`

Infrastructure is sound. The port-0 binding, port file workflow, AsgardCloseable lifecycle, and structured logging are all correctly implemented. The two NOT_READY issues are a real bug and a documentation gap that will mislead future implementors.

---

## CRITICAL

### 1. Server orphaned if `portFileManager.writePort()` throws during `start()`
**Reviewer:** CODE_QUALITY_REVIEWER

**The bug:**
```kotlin
override suspend fun start() {
    check(engine == null) { "Server already started" }

    val server = embeddedServer(CIO, port = 0) { configureServer() }
    server.start(wait = false)                    // ← Server is running

    val resolvedPort = server.engine.resolvedConnectors().first().port
    portFileManager.writePort(resolvedPort)        // ← If this throws (disk full, permissions)...

    engine = server                               // ← ...these are never reached
    boundPort = resolvedPort
}
```

If `writePort()` throws: the Ktor server is bound and running, but `engine` stays `null`. Consequence chain:
- `close()` does `engine ?: return` — silently exits, server is never stopped
- Next `start()` passes `check(engine == null)` — starts a **second** Ktor server
- Two running servers, one port file (pointing to the second), first server is permanently orphaned

**Fix:** Assign `engine = server` immediately after `server.start()`, before the I/O call:
```kotlin
server.start(wait = false)
engine = server                          // assign first

val resolvedPort = server.engine.resolvedConnectors().first().port
try {
    portFileManager.writePort(resolvedPort)
    boundPort = resolvedPort
} catch (e: Exception) {
    close()                              // clean shutdown on I/O failure
    throw e
}
```

---

### 2. Port file path is duplicated across the Bash/Kotlin boundary — no cross-reference
**Reviewer:** DRY_SPECIALIST_REVIEWER

The sole agent-discovery contract (where the port file lives) exists in two places with no link between them:

**`scripts/harness-cli-for-agent.sh` line 11:**
```bash
PORT_FILE="${HOME}/.chainsaw_agent_harness/server/port.txt"
```

**`PortFileManager.kt`:**
```kotlin
val DEFAULT_PATH: Path = Path.of(
    System.getProperty("user.home"),
    ".chainsaw_agent_harness", "server", "port.txt"
)
```

If the path changes in one place without the other, every agent invocation silently fails with a "port file not found" error — no test can catch cross-language drift. The KDoc in `PortFileManager` already references `ref.ap.8PB8nMd93D3jipEWhME5n.E` but does not say the literal path values must stay in sync with the shell script. The shell script has no reciprocal reference at all.

**Fix:** Add explicit cross-reference comments at both sites:

In `PortFileManager.kt`:
```kotlin
// MUST match PORT_FILE in scripts/harness-cli-for-agent.sh (ref.ap.8PB8nMd93D3jipEWhME5n.E)
val DEFAULT_PATH: Path = Path.of(...)
```

In `harness-cli-for-agent.sh`:
```bash
# MUST match PortFileManager.DEFAULT_PATH in app/src/main/kotlin/.../PortFileManager.kt
PORT_FILE="${HOME}/.chainsaw_agent_harness/server/port.txt"
```

---

## IMPORTANT

### 3. `/agent/question` stub has no inline comment — deferred blocking behavior is invisible
**Reviewers:** CODE_QUALITY_REVIEWER, DRY_SPECIALIST_REVIEWER, ARCHITECTURE_ADHERENCE_REVIEWER

The architecture requires `/agent/question` to eventually block the caller until a human provides an answer (delivered via TMUX). The current stub returns 200 immediately — correct for V1. But the routing code has no comment:

```kotlin
post("/question") { handleAgentRequest<AgentQuestionRequest>("/agent/question") }
```

All four endpoints look identical. A future implementor has no signal that `/question` needs fundamentally different treatment from `/done` or `/status`. The class-level KDoc says "stub" but that is easy to miss when landing at the routing code.

**Fix:** Add an inline comment at the routing site:
```kotlin
// STUB: V1 returns 200 immediately.
// Future: must suspend until human answers (answer delivered via TMUX send-keys).
post("/question") { handleAgentRequest<AgentQuestionRequest>("/agent/question") }
```

---

### 4. One test does not use `withServer` — Ktor server leak on test failure
**Reviewer:** CODE_QUALITY_REVIEWER

The test `"THEN port file is deleted after close"` bypasses the `withServer` try/finally helper:
```kotlin
it("THEN port file is deleted after close") {
    val fixture = createFixture()
    fixture.server.start()
    fixture.server.close()                  // ← No try/finally; if close() throws, server leaks
    Files.exists(fixture.portFilePath) shouldBe false
}
```

All other 7 tests use `withServer`. This inconsistency violates the established pattern in the same file and risks a leaked Ktor server if `close()` throws or a future assertion is added between start and close.

**Fix:** Refactor using `withServer` (which already has try/finally), or verify server is stopped outside the helper:
```kotlin
it("THEN port file is deleted after close") {
    withServer { fixture ->
        // server is closed by withServer; check port file after the block
    }
    // Or verify portFilePath doesn't exist here
}
```
*(Note: `withServer` closes in `finally`, so post-block check works.)*

---

### 5. Malformed JSON not tested — silent 400 on contract drift
**Reviewer:** TEST_COVERAGE_REVIEWER

All 4 endpoint tests only exercise happy path (valid JSON). `handleAgentRequest` calls `call.receive<T>()` — Ktor+Jackson returns 400 on bad JSON or missing required fields. Since `harness-cli-for-agent.sh` constructs all payloads, a contract drift (renamed field, missing required field) causes silent 400s with no test catching the regression.

**Fix:** Add one test covering one endpoint with malformed JSON to verify the shared handler path returns a non-200 status (exercising all 4 via the shared inline function):
```kotlin
describe("AND POST /agent/done is called with malformed JSON") {
    it("THEN response status is 400") {
        withServer { fixture ->
            val response = postJson(fixture.server.port(), "/agent/done", """{"invalid"}""")
            response.use { it.code shouldBe 400 }
        }
    }
}
```

---

### 6. Endpoint response body not asserted — shell script contract can silently drift
**Reviewer:** TEST_COVERAGE_REVIEWER

Tests verify `response.code shouldBe 200` but not the response body `{"status":"ok"}`. The `harness-cli-for-agent.sh` may check the response body to detect harness errors. If a refactor changes `OK_RESPONSE`, all HTTP tests stay green while the real consumer breaks.

**Fix:** Assert response body in at least one endpoint test:
```kotlin
it("THEN response body is {\"status\":\"ok\"}") {
    withServer { fixture ->
        val response = postJson(fixture.server.port(), "/agent/done", """{"branch": "test"}""")
        response.use { it.body!!.string() shouldBe """{"status":"ok"}""" }
    }
}
```

---

## OPTIONAL

### 7. `AgentRequestHandler` injection boundary missing (future wiring concern)
**Reviewer:** ARCHITECTURE_ADHERENCE_REVIEWER

Currently all endpoints do identical stub behavior. When real behavior is wired (especially `/question`'s blocking), the logic will need to enter `KtorHarnessServer`. To keep SRP clean, an `AgentRequestHandler` interface should be injected at that time — server owns HTTP protocol, handler owns behavior. Not needed now, but worth capturing as a ticket.

### 8. `PortFileManager` not behind interface
**Reviewer:** ARCHITECTURE_ADHERENCE_REVIEWER

`KtorHarnessServer` depends on the concrete `PortFileManager`. Per CLAUDE.md DIP principle. Low urgency in a single-deployment CLI — acceptable today.

### 9. Ktor version strings not in version catalog
**Reviewer:** ARCHITECTURE_ADHERENCE_REVIEWER

Ktor `3.1.1` and Jackson `2.17.2` are inline strings while other deps use `libs.versions.toml`. Minor inconsistency.

### 10. Lifecycle guard paths untested
**Reviewer:** TEST_COVERAGE_REVIEWER

`port()` before `start()` → throws `IllegalStateException`. `start()` called twice → `check()` throws. Both guards are correct; tests would pin the contract. Low urgency.

### 11. `OkHttpClient` not closed in test suite
**Reviewer:** CODE_QUALITY_REVIEWER

`httpClient` holds a thread pool. Not closed in afterAll. Low risk (GC/JVM shutdown handles it) but inconsistent with resource management standards.

---

## Reviewers Summary

| Reviewer | Verdict |
|----------|---------|
| ARCHITECTURE_ADHERENCE_REVIEWER | APPROVED WITH CONDITIONS |
| CODE_QUALITY_REVIEWER | NOT_READY |
| TEST_COVERAGE_REVIEWER | READY |
| DRY_SPECIALIST_REVIEWER | NOT_READY |

**Overall: NOT_READY** — two issues require fixes before this is production-ready:
1. The server-orphan bug on `writePort` failure (CRITICAL correctness bug)
2. Cross-reference comments on the port file path across Bash/Kotlin boundary (CRITICAL DRY/maintenance)
