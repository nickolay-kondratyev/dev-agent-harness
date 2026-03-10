# Test Coverage Review — Private Notes

## Files Examined

- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/chainsaw/core/server/HarnessServer.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/chainsaw/core/server/PortFileManager.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/chainsaw/core/server/AgentRequests.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/chainsaw/core/server/KtorHarnessServerTest.kt`
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/chainsaw/core/server/PortFileManagerTest.kt`

## Analysis Notes

### Malformed JSON (IMPORTANT)

Ktor's `ContentNegotiation` plugin with Jackson will throw a `JsonParseException` or `MissingKotlinParameterException` when deserialization fails. Ktor converts this to a 400 Bad Request by default. The call to `call.receive<T>()` in `handleAgentRequest` is where this failure occurs. Since all four endpoints share `handleAgentRequest`, a single test covering one endpoint with malformed JSON would effectively cover the shared path.

The concern is real: `harness-cli-for-agent.sh` constructs JSON payloads. If the JSON contract drifts (e.g., field renamed, missing required field), the agent gets a 400 with no existing test to catch the regression. The current tests only exercise the happy path.

### Response Body (IMPORTANT)

`OK_RESPONSE` is a `companion object` constant: `mapOf("status" to "ok")`. Jackson serializes this as `{"status":"ok"}`. The shell script likely parses or at least expects this exact shape. The tests verify status code 200 but not body content. This is a meaningful gap because the body IS the contract for the shell script consumer.

### Port before start / double start (OPTIONAL)

Both guards are simple and correctly implemented:
- `port()` returns `boundPort ?: throw IllegalStateException(...)`
- `start()` has `check(engine == null) { "Server already started" }`

These are low-risk omissions. The code is clear and the guards are simple. Worth adding as they pin the contract, but not blocking.

### `/agent/question` blocking

Implementation is clearly a stub (`handleAgentRequest` treats it identically to `/done`). The architecture says it should block until human responds. This is a future implementation concern, not a test gap for the current stub. The current test (200 response) is correct for what is implemented.

### Overall quality assessment

The test setup is solid — using real Ktor CIO server bound to port 0 with OkHttp client is the right choice. The `withServer` fixture correctly wraps start/close in try/finally. The `PortFileManagerTest` is thorough for its scope (write, read-back, delete, idempotent-delete). The structure follows BDD conventions consistently.

The two IMPORTANT items (malformed JSON + response body) are genuine gaps that could let real contract regressions slip through. They are easy to add and have clear value.
