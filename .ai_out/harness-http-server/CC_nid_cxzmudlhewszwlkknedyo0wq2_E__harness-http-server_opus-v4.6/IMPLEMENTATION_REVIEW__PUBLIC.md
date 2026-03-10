# Implementation Review: Harness HTTP Server

## Summary

The implementation creates a Ktor CIO HTTP server with 4 stub POST endpoints (`/agent/done`, `/agent/question`, `/agent/failed`, `/agent/status`), a port file manager, and request data classes. The build passes, sanity check passes, all 12 tests (8 server + 4 port file) pass, and no existing tests were removed or modified.

**Overall assessment: GOOD implementation.** The code follows the approved plan, applies the plan reviewer's feedback (simplified PortFileManager to a plain class), uses correct patterns (constructor injection, `AsgardCloseable`, structured `Out` logging with `Val`/`ValType`, BDD tests, one assert per test). The JSON payload shapes match the `harness-cli-for-agent.sh` contract.

There are no CRITICAL issues. There are two IMPORTANT issues and a few suggestions below.

## CRITICAL Issues

None.

## IMPORTANT Issues

### 1. `Pair` usage in `PortFileManagerTest` violates CLAUDE.md coding standards

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/chainsaw/core/server/PortFileManagerTest.kt` (line 16-20)

CLAUDE.md explicitly states: "No `Pair`/`Triple` -- create descriptive `data class`." The `createTempPortFile()` function returns `Pair<Path, PortFileManager>` and destructures it with `val (portFilePath, manager) = createTempPortFile()`.

**Fix:** Create a small data class (similar to the `ServerFixture` in the server test) or inline the creation:

```kotlin
data class PortFileFixture(val portFilePath: Path, val manager: PortFileManager)

fun createFixture(): PortFileFixture {
    val tempDir = Files.createTempDirectory("port-file-manager-test")
    val portFilePath = tempDir.resolve("port.txt")
    return PortFileFixture(portFilePath, PortFileManager(portFilePath))
}
```

### 2. DRY violation -- four nearly identical endpoint handler blocks in `KtorHarnessServer`

**File:** `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/chainsaw/core/server/HarnessServer.kt` (lines 98-136)

All four endpoint handlers follow the exact same pattern:
1. Receive request (type varies)
2. Log with hard-coded path string and `request.branch`
3. Respond with `mapOf("status" to "ok")`

The only differences are: the path string, the request type, and the fact that request types share a `branch` field but don't have a common interface.

This is acceptable for a stub server with 4 endpoints, but when real handler logic is wired in, this duplication will become a maintenance concern. For now, the most practical improvement would be to extract the common `branch` field into an interface. This would allow the logging portion to be consolidated without over-engineering:

```kotlin
interface AgentRequest {
    val branch: String
}

data class AgentDoneRequest(override val branch: String) : AgentRequest
data class AgentQuestionRequest(override val branch: String, val question: String) : AgentRequest
// ... etc
```

Then the logging becomes a single function:

```kotlin
private suspend fun logAgentRequest(path: String, request: AgentRequest) {
    out.info(
        "agent_request_received",
        Val(path, ValType.HTTP_REQUEST_PATH),
        Val(request.branch, ValType.GIT_BRANCH_NAME),
    )
}
```

**Severity:** This is not blocking for a stub server, but should be addressed before real handler logic is added. The 4-way duplication will compound when real handlers are wired in.

## Suggestions

### 1. Temp directories are not cleaned up in tests

Both `KtorHarnessServerTest` and `PortFileManagerTest` create temp directories via `Files.createTempDirectory(...)` but never delete them. The JVM's `createTempDirectory` does NOT auto-delete. While the OS will eventually clean `/tmp`, in CI environments or repeated local runs these can accumulate.

Consider adding cleanup in `afterEach` or using a helper that registers cleanup. For a pragmatic approach, at minimum delete the temp dir in the `withServer` finally block:

```kotlin
suspend fun withServer(block: suspend (ServerFixture) -> Unit) {
    val fixture = createFixture()
    fixture.server.start()
    try {
        block(fixture)
    } finally {
        fixture.server.close()
        Files.deleteIfExists(fixture.portFilePath)
        Files.deleteIfExists(fixture.portFilePath.parent)
    }
}
```

### 2. No error handling for malformed JSON requests

The endpoints will return a Ktor-default 500 or 400 error if the JSON body is malformed or missing required fields. This is fine for a stub, but when real handlers are wired, consider installing a `StatusPages` plugin with a handler for `ContentTransformationException` to return structured error responses. Not needed now, but worth noting as a follow-up concern.

### 3. Response body could be a named constant or data class

All four endpoints respond with `mapOf("status" to "ok")`. This creates a new map allocation per request. A simple companion constant would be cleaner:

```kotlin
companion object {
    private const val GRACEFUL_SHUTDOWN_PERIOD_MILLIS = 1000L
    private const val SHUTDOWN_TIMEOUT_MILLIS = 5000L
    private val OK_RESPONSE = mapOf("status" to "ok")
}
```

### 4. Test: consider verifying response body content

The endpoint tests only verify `response.code shouldBe 200`. For completeness, at least one test could verify the response body is `{"status":"ok"}`. This would catch accidental changes to the response format. Not critical since the CLI script ignores the body, but it documents the contract.

## Verification Results

| Check | Result |
|-------|--------|
| `./gradlew :app:build` | PASS (exit code 0) |
| `./sanity_check.sh` | PASS (exit code 0) |
| `./gradlew :app:test` | PASS (exit code 0) |
| Existing tests removed? | No -- only new test files added |
| Existing tests modified? | No |
| JSON payloads match CLI script? | Yes -- `branch`, `question`, `reason` fields align |
| Logging follows `Out`/`Val`/`ValType` pattern? | Yes |
| `AsgardCloseable` correctly implemented? | Yes -- `close()` is `suspend`, stops engine then deletes port file |
| Constructor injection only? | Yes |
| BDD test structure? | Yes -- GIVEN/WHEN/THEN with `describe`/`it` |
| One assert per `it` block? | Yes |

## Verdict

**APPROVED WITH MINOR REVISIONS.** The two IMPORTANT issues (Pair usage, DRY in endpoint handlers) should be addressed. The suggestions are optional but recommended.
