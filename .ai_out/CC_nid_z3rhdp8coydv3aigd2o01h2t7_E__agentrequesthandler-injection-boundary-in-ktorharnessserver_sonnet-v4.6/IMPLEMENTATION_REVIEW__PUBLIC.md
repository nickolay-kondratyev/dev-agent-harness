# Review: AgentRequestHandler Injection Boundary in KtorHarnessServer

## Summary

Introduces `AgentRequestHandler` interface + `NoOpAgentRequestHandler` placeholder,
injects it into `KtorHarnessServer`, and adds tests that verify handler invocation and
`/agent/question` response body. All 17 tests pass, sanity check passes, no regressions.

The SRP goal is achieved: `KtorHarnessServer` is now purely HTTP protocol concerns,
and business logic lives in the injected handler. The `handleAgentRequest` helper is
cleanly extended with the action lambda. Documentation is accurate and helpful.

**VERDICT: CHANGES_NEEDED**

One MUST-fix violation of the project's CLAUDE.md standard (`Pair` usage),
one SHOULD-fix test hygiene concern. Remaining items are suggestions.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### MUST: `createRecordingFixture` returns `Pair` — violates CLAUDE.md

`app/src/test/kotlin/com/glassthought/chainsaw/core/server/KtorHarnessServerTest.kt`, line 234:

```kotlin
fun createRecordingFixture(): Pair<ServerFixture, RecordingAgentRequestHandler> {
    ...
    return Pair(ServerFixture(server, portFilePath), handler)
}
```

CLAUDE.md states explicitly: "No `Pair`/`Triple` → create descriptive `data class`."

Fix: define a small data class at the describe-block scope:

```kotlin
data class RecordingFixture(
    val server: ServerFixture,
    val handler: RecordingAgentRequestHandler,
)
```

Then return `RecordingFixture(ServerFixture(...), handler)` and destructure as
`val (fixture, handler) = createRecordingFixture()` (destructuring still works since
`data class` generates `component1`/`component2`).

The destructuring call sites (`val (fixture, handler) = ...`) in the `it` blocks do not
need to change because `data class` componentN functions match.

---

### SHOULD: Recording tests don't use `withServer` — resource-leak risk on assertion failure

The three recording tests (lines 247–286) manually call `start()` and `close()` with
`try/finally`. The existing `withServer` helper already provides this pattern. The recording
tests cannot reuse `withServer` directly because `withServer` only exposes `ServerFixture`,
not the handler.

The risk: if assertion throws before `fixture.server.close()` is reached in `finally`,
the server leaks. The `try/finally` block does protect against this, but the pattern is
duplicated across three `it` blocks rather than extracted.

Suggested fix — add a `withRecordingServer` helper parallel to `withServer`:

```kotlin
suspend fun withRecordingServer(
    block: suspend (ServerFixture, RecordingAgentRequestHandler) -> Unit,
) {
    val (fixture, handler) = createRecordingFixture()
    fixture.server.start()
    try {
        block(fixture, handler)
    } finally {
        fixture.server.close()
    }
}
```

Then each `it` block becomes:

```kotlin
it("THEN onDone is invoked with the correct branch") {
    withRecordingServer { fixture, handler ->
        postJson(fixture.server.port(), "/agent/done", """{"branch":"my-branch"}""").close()
        handler.doneCalls.size shouldBe 1
    }
}
```

This also makes the question test cleaner: `handler.questionAnswer = "the answer"` moves
inside the lambda where it belongs alongside the server setup.

---

## Suggestions

### NICE: Missing recording-handler coverage for `/failed` and `/status`

The recording-handler describe block covers `onDone` (call count + branch value) and
`onQuestion` (response body). `onFailed` and `onStatus` have no handler-invocation tests.
This is acceptable for V1 since the tests verify the delegation pattern is working, but
adding parallel tests for `/failed` and `/status` would complete the coverage symmetrically.
Consider a follow-up ticket if the full coverage is desired.

### NICE: `RecordingAgentRequestHandler.questionAnswer` is a mutable `var`

```kotlin
var questionAnswer = "test-answer"
```

The test sets this before `start()` (line 273: `handler.questionAnswer = "the answer"`),
so there is no concurrent mutation concern in the current tests. For consistency with the
immutability preference in CLAUDE.md ("Favor immutability"), consider accepting
`questionAnswer` as a constructor parameter instead:

```kotlin
class RecordingAgentRequestHandler(
    private val questionAnswer: String = "test-answer",
) : AgentRequestHandler {
    ...
    override suspend fun onQuestion(request: AgentQuestionRequest): String = questionAnswer
}
```

The one test that overrides it would pass `"the answer"` at construction time.
This is a suggestion, not a requirement.

### NICE: Default parameter on `KtorHarnessServer` constructor may silently hide missing wiring

```kotlin
private val agentRequestHandler: AgentRequestHandler = NoOpAgentRequestHandler(),
```

The default is correct for V1 per the ticket spec, and the KDoc documents it clearly.
When wiring in the real phase-runner handler, there is no compile-time guarantee that
the default is not accidentally kept. This is a future concern — worth noting in the
ticket or CLAUDE.md when real handler injection is added.

---

## Documentation Updates Needed

None. The KDoc on `KtorHarnessServer` and the `AgentRequestHandler` interface doc
are accurate and sufficient.
