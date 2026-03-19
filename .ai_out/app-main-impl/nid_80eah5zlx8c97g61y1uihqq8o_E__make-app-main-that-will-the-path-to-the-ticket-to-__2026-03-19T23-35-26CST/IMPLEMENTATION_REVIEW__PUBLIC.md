# Implementation Review: AppMain CLI Entry Point

## Summary

This PR implements the CLI entry point (`shepherd run --workflow <name> --ticket <path> --iteration-max <N>`) using picocli, and introduces `ShepherdInitializer` to orchestrate the 5-step startup sequence. The overall design is solid: clean separation between CLI parsing (AppMain) and orchestration (ShepherdInitializer), constructor injection throughout, testability seams via `ServerStarter`/`StoppableServer`/`TicketShepherdCreatorFactory`, and anchor points preserved. Tests pass, including detekt.

**Overall assessment: Good implementation. A few important issues to address, mainly around `iterationMax` being silently dropped and a test that claims to verify cleanup but does not.**

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. `iterationMax` from CliParams is silently dropped -- never reaches downstream

`CliParams.iterationMax` is parsed from CLI and stored in the data class, but `ShepherdInitializer.run()` never passes it anywhere. The `TicketShepherdCreator.create()` signature does not accept it:

```kotlin
// ShepherdInitializer.kt line 68-74
val ticketShepherd = ticketShepherdCreatorFactory
    .create(outFactory)
    .create(
        shepherdContext = shepherdContext,
        ticketPath = cliParams.ticketPath,
        workflowName = cliParams.workflowName,
        // iterationMax is NOT passed
    )
```

Per the spec (doc/high-level.md line 94):
> `--iteration-max` **(required)**: default iteration budget for reviewer sub-parts. The planner uses this value when generating `plan_flow.json`. For `straightforward` workflows, this overrides `iteration.max` in the static workflow JSON.

This is a **required** CLI parameter that affects runtime behavior. Silently accepting and discarding it is misleading. The value needs to reach `TicketShepherdCreator.create()` or the workflow resolution layer.

**Recommendation:** Either:
- (a) Add `iterationMax: Int` to `TicketShepherdCreator.create()` signature and thread it through, OR
- (b) If downstream wiring is deferred, add an explicit `TODO("iterationMax not yet consumed -- see ref.ap.XXX")` with a comment in `ShepherdInitializer.run()` so it is not silently lost.

#QUESTION_FOR_HUMAN: Is it intentional to defer `iterationMax` consumption to a follow-up ticket? If so, option (b) with an explicit TODO is appropriate.

### 2. Test claims to verify `ShepherdContext.close()` is called on server failure, but does not actually verify it

File: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializerTest.kt`, lines 297-325.

```kotlin
describe("GIVEN server starter that throws") {
    // ...
    it("THEN ShepherdContext.close() is still called (cleanup in reverse)") {
        // ShepherdContext.close() delegates to Infra which closes OutFactory.
        // We verify the exception propagates correctly.
        val exception = shouldThrow<IllegalStateException> {
            initializer.run(DEFAULT_CLI_PARAMS)
        }
        exception.message shouldContain "Server failed to start"
    }
}
```

The test description says "ShepherdContext.close() is still called" but the assertion only checks that the exception propagates. It does **not** verify that `close()` was invoked. This is a misleading test -- the name overpromises relative to what is asserted.

**Recommendation:** Create a `FakeShepherdContext` (or a `closeCalled` tracking wrapper) that records whether `close()` was invoked, then assert on that. Alternatively, rename the test to accurately reflect what it verifies (e.g., "THEN exception from server starter propagates").

### 3. Last test ("GIVEN TICKET_SHEPHERD_SERVER_PORT env var is not set") is a no-op

File: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializerTest.kt`, lines 362-381.

```kotlin
it("THEN the default serverPortReader is the readServerPortFromEnv function") {
    val initializer = ShepherdInitializer(...)
    initializer shouldBe initializer // trivially true
}
```

This test asserts `initializer shouldBe initializer` which is always true. It verifies nothing. Either remove it or replace it with a meaningful test (e.g., verify that `readServerPortFromEnv()` throws `IllegalStateException` when the env var is absent -- this can be done by calling the companion function directly in a test environment where the var is not set).

### 4. `--workflow` is marked `required = true` in picocli but spec says it is NOT required

From `doc/high-level.md` line 93:
> `--workflow`: workflow definition name (e.g., `straightforward`, `with-planning`)

Only `--ticket` and `--iteration-max` are explicitly marked as **(required)**. `--workflow` is listed without the required tag, suggesting it may have a default value.

In `RunSubcommand`:
```kotlin
@Option(names = ["--workflow"], required = true, ...)
lateinit var workflowName: String
```

#QUESTION_FOR_HUMAN: Should `--workflow` have a default value (e.g., `"straightforward"`)? The spec does not mark it as required, unlike the other two parameters.

---

## Suggestions

### 1. Error handling gap: `RunSubcommand.call()` always returns 0

If `ShepherdInitializer.run()` throws an exception (which it will on any startup failure), the exception propagates out of `runBlocking` uncaught, and picocli will handle it with a stack trace. Consider wrapping in a try-catch that logs the error cleanly and returns a non-zero exit code:

```kotlin
override fun call(): Int {
    // ...
    return try {
        runBlocking { initializer.run(cliParams) }
        0
    } catch (e: Exception) {
        System.err.println("Startup failed: ${e.message}")
        1
    }
}
```

### 2. Consider reducing test setup duplication

The test file has 6 `describe` blocks that each independently construct `NoOpOutFactory()`, `createTestShepherdContext()`, `FakeServerStarter()`, etc. Consider using a shared setup at the top `describe` level. The `createTestShepherdContext()` helper is already a good step, but the `ShepherdInitializer` construction is repeated nearly identically in multiple blocks with only minor variations. A builder or factory helper could DRY this up.

### 3. `main()` function signature changed from `main()` to `main(args: Array<String>)`

This is correct and expected for picocli integration. Just noting it was a change from the previous version. The anchor point `ap.4JVSSyLwZXop6hWiJNYevFQX.E` is preserved -- good.

### 4. Minor: `@Suppress("SpreadOperator")` in main()

The spread operator suppression is fine for the CLI entry point where it runs exactly once. No concern here.

---

## Documentation Updates Needed

None required. The CLAUDE.md is generated and does not need updates for this change. The anchor points are properly maintained.

---

## Files Reviewed

| File | Verdict |
|------|---------|
| `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/cli/AppMain.kt` | Good -- clean CLI layer |
| `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializer.kt` | Good -- clean orchestration, proper reverse-order cleanup |
| `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializerTest.kt` | Needs fixes (issues #2, #3 above) |
| `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/gradle/libs.versions.toml` | Good |
| `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/build.gradle.kts` | Good |
