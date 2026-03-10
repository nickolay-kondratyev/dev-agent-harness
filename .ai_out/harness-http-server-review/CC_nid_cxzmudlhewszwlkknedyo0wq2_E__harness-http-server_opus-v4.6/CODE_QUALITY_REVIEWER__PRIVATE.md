# Code Quality Review — Private Notes

## Review Process

### Files Read
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/HarnessServer.kt` (KtorHarnessServer + HarnessServer interface)
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/AgentRequests.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/PortFileManager.kt`
- `app/src/test/kotlin/com/glassthought/chainsaw/core/server/KtorHarnessServerTest.kt`
- `app/src/test/kotlin/com/glassthought/chainsaw/core/server/PortFileManagerTest.kt`
- `scripts/harness-cli-for-agent.sh`
- `_tickets/harness-http-server.md`
- `submodules/thorg-root/.../AsgardDescribeSpec.kt`

### Investigation of Each Prompted Area

**1. Concurrency (engine/boundPort as mutable vars)**

The `check(engine == null)` guard + subsequent assignment is a read-check-write with no lock. In a single-threaded CLI this is fine. Marked OPTIONAL since V1 is stated to be serial.

Compared to using `AtomicReference` — for V1 the annotation approach (KDoc) is sufficient per Pareto's principle. A Mutex would be idiomatic if this grows into a server with concurrent lifecycle calls.

**2. /agent/question stub behavior**

Read `harness-cli-for-agent.sh` to understand the full curl contract. The shell uses `--max-time 30` (not blocking indefinitely). The shell help text says "This call returns immediately; the answer will be delivered back to you via TMUX — just wait for it."

This tells me the *current* stub behavior (return 200 immediately) is architecturally correct for V1. The TMUX path for delivering the answer is the design intent. The server-side question handler will need to be fundamentally different when wired — it needs to suspend, wait for human input via some mechanism, and return the answer text in the response body (or the TMUX path makes the response content irrelevant). This ambiguity is the real gap — not the 200 response itself.

Verdict: The 200 stub is fine. The missing TODO comment is the IMPORTANT finding.

**3. Port file + start failure atomicity**

This is the clearest correctness bug. The sequence is:
```
server.start(wait=false)          // server running
resolvedConnectors().first().port  // could throw if engine crashed immediately
portFileManager.writePort(...)     // could throw (disk, permissions)
engine = server                    // only assigned if all above succeed
```

If anything between `server.start` and `engine = server` throws, we have a running Ktor server with no handle. This is a real orphaned resource bug. Marked CRITICAL.

**4. `private inline fun` with reified type in class**

Verified Kotlin semantics: `private inline` on a member extension function is legal. The inlining happens at the call site (the lambda bodies in `routing {}` block). The `reified T` constraint works because the call site is within the class itself. `out` is captured from the enclosing instance — this is valid. No issue.

**5. Test resource cleanup**

Two distinct issues:

a) The "port file deleted after close" test — missing try/finally. This is the IMPORTANT finding because it's a leaked server, not just a temp file.

b) OkHttpClient not closed — this creates a thread pool leak. Added to the IMPORTANT finding as a secondary recommendation.

c) Temp dirs from `createFixture()` — OS cleanup handles this. Not worth raising.

**6. `withServer` suspend from describe block**

`describe` block bodies are synchronous (they register tests, not execute them). The `withServer` call is inside `it` blocks, which are `suspend`. Correct usage. The question prompt was a known red herring — confirmed by reading AsgardDescribeSpec source (it wraps DescribeSpec which runs `it` bodies in coroutine context).

### Verdict Reasoning

CRITICAL: 1 finding (orphaned server on start failure)
IMPORTANT: 2 findings (test server leak, missing TODO on question endpoint)
OPTIONAL: 1 finding (concurrency documentation gap)

Overall NOT_READY because the CRITICAL finding is a real resource leak bug that would be hard to diagnose and fix later. The fixes are all simple. The code quality overall is good — clean structure, good naming, correct use of suspend, proper DRY in handleAgentRequest.
