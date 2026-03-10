# Test Coverage Review — KtorHarnessServer & PortFileManager

**Overall Verdict: READY**

The existing tests cover the core contract correctly and use an appropriate testing strategy (real server + real client). The missing coverage items below are honest gaps, but none represent a blocking deficiency for a V1 stub implementation.

---

## Findings

### IMPORTANT — Malformed JSON returns 400, not tested

**Issue:** There is no test for what happens when an agent sends malformed JSON to any endpoint.

**Why it matters:** The Ktor `ContentNegotiation` plugin with Jackson will return a 400 Bad Request when deserialization fails. The current stub endpoints just log and respond `{"status":"ok"}` for valid JSON, but nothing documents or pins down the error behavior for invalid input. A future harness that needs to surface useful error messages (or that wraps `handleAgentRequest` in error recovery logic) could accidentally break this without a test noticing.

This is not hypothetical — `harness-cli-for-agent.sh` is generated/called by agents. If the JSON contract drifts or a field is missing, the agent will get an opaque 400 with no test catching the regression.

**Recommendation:** Add one test per endpoint (or at minimum one representative test covering the shared `handleAgentRequest` logic) that sends malformed JSON and asserts 400 is returned. This pins the current behavior and ensures future changes to error handling are intentional.

---

### IMPORTANT — Response body not asserted

**Issue:** All four endpoint tests assert `response.code shouldBe 200` but do not verify the response body is `{"status":"ok"}`.

**Why it matters:** The response body `{"status":"ok"}` is the contract the `harness-cli-for-agent.sh` script depends on. If the implementation accidentally changes the key or value (e.g., a refactor changes `OK_RESPONSE` or the Jackson serialization), no test will catch it. The agent shell script will silently receive unexpected output with no failure signal.

**Recommendation:** Add one representative assertion (can be on any single endpoint, they share `OK_RESPONSE`) that verifies the body contains `{"status":"ok"}`. This is a one-line addition per test or a shared helper check.

---

### OPTIONAL — `port()` before `start()` not tested

**Issue:** `port()` throws `IllegalStateException("Server has not been started")` when called before `start()`. There is no test covering this guard.

**Why it matters:** Low risk — this is simple defensive programming. The guard exists and is correct. The only scenario where a missing test matters is if a future refactor accidentally changes the null-check logic.

**Recommendation:** Add a single test: `WHEN port() is called before start() THEN throws IllegalStateException`. Low-effort, pins the contract.

---

### OPTIONAL — Double `start()` guard not tested

**Issue:** `start()` has `check(engine == null) { "Server already started" }`. No test exercises this path.

**Why it matters:** In the current serial V1 architecture, double-start is unlikely. But the guard is there because it's a real correctness concern. Without a test, a future refactor could accidentally remove it undetected.

**Recommendation:** Add a test that calls `start()` twice and asserts `IllegalStateException` is thrown on the second call.

---

### NOT A CONCERN — `/agent/question` blocking behavior

**Issue (from prompt):** The architecture doc says `/agent/question` should block curl until human answers, but the implementation is a stub returning 200 immediately.

**Assessment:** This is a known V1 stub — the endpoint is clearly documented as non-blocking in the current implementation. A test asserting 200 is correct for the current stub. No test change needed here. The gap is architectural (blocking semantics not implemented yet), not a test gap. When the blocking behavior is implemented, the tests should change.

---

### NOT A CONCERN — Concurrent requests

**Issue:** No concurrency tests.

**Assessment:** V1 is explicitly serial (one agent at a time). No value in concurrency tests at this stage.

---

### NOT A CONCERN — Empty branch field

**Issue:** No validation that `branch` is non-empty.

**Assessment:** The current implementation logs and responds `{"status":"ok"}` regardless of field contents — it is a stub. There is no business logic triggered by `branch` yet. Validating field contents belongs in the future phase when the harness actually acts on the branch value.

---

### NOT A CONCERN — `writePort` called twice (overwrite)

**Issue:** No test for calling `writePort` twice.

**Assessment:** `Files.writeString` overwrites by default. The behavior is consistent with the standard library contract. The `PortFileManager` doc says "creates dirs + writes port" — overwrite is the obvious expectation. No test needed; it is covered implicitly by the existing lifecycle (start writes, close deletes).
