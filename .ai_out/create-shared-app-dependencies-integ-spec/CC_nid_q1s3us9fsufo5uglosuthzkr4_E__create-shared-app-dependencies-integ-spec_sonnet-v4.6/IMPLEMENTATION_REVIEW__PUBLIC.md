# Implementation Review: Shared App Dependencies Integration Spec

Ticket: `nid_q1s3us9fsufo5uglosuthzkr4_E`
Date: 2026-03-11
Reviewer: Code Reviewer Agent

---

## Summary

The implementation introduces shared integration test infrastructure: `SharedAppDepIntegFactory` (process-scoped singleton), `SharedAppDepDescribeSpec` (abstract base class), and `SharedAppDepSpecConfig`. It also refactors `Initializer.initialize()` to accept `OutFactory` as a parameter instead of creating `SimpleConsoleOutFactory` internally, and migrates `SpawnTmuxAgentSessionUseCaseIntegTest` to use the new base class.

All unit tests pass (`./test.sh`, `sanity_check.sh`). The build is clean. The overall implementation is structurally sound and well-documented. The three issues below require attention before this is considered done.

---

## CRITICAL Issues

None.

---

## IMPORTANT Issues

### 1. Two Remaining Integration Tests Were Not Migrated

The ticket goal is to eliminate inline dependency construction from integration tests. The plan's Phase 4 explicitly calls out `SpawnTmuxAgentSessionUseCaseIntegTest` as the one test to migrate in this ticket, and the other two (`TmuxCommunicatorIntegTest`, `TmuxSessionManagerIntegTest`) are left for later. However, those two tests still contain the exact inline construction pattern that `SharedAppDepDescribeSpec` was created to eliminate:

`app/src/test/kotlin/org/example/TmuxCommunicatorIntegTest.kt` lines 25-27:
```kotlin
val commandRunner = TmuxCommandRunner()
val communicator = TmuxCommunicatorImpl(outFactory, commandRunner)
val sessionManager = TmuxSessionManager(outFactory, commandRunner, communicator)
```

`app/src/test/kotlin/org/example/TmuxSessionManagerIntegTest.kt` lines 21-23:
```kotlin
val commandRunner = TmuxCommandRunner()
val communicator = TmuxCommunicatorImpl(outFactory, commandRunner)
val sessionManager = TmuxSessionManager(outFactory, commandRunner, communicator)
```

These tests now create their own isolated `TmuxCommandRunner`/`TmuxCommunicatorImpl`/`TmuxSessionManager` instances which are separate from the `appDependencies` singleton in `SharedAppDepIntegFactory`. This is an inconsistency and a DRY violation. The now-existing infrastructure means these tests have no excuse for not using `appDependencies.tmuxSessionManager`.

**Recommendation:** Migrate `TmuxCommunicatorIntegTest` and `TmuxSessionManagerIntegTest` to extend `SharedAppDepDescribeSpec` and use `appDependencies.tmuxSessionManager`. If this is intentionally deferred, a follow-up ticket should be created.

---

### 2. Unsafe Body Lambda Cast — Potential Runtime ClassCastException

`app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedAppDepDescribeSpec.kt` line 64:
```kotlin
@Suppress("UNCHECKED_CAST") (body as AsgardDescribeSpec.() -> Unit)
```

The comment says "safe cast: every SharedAppDepDescribeSpec IS an AsgardDescribeSpec." This reasoning is correct for the JVM object (the receiver that will be passed to the lambda), but the Kotlin type system's function type semantics make this more nuanced.

On the JVM, function types with receivers (`A.() -> Unit`) are compiled to `Function1<A, Unit>`. A cast from `Function1<SharedAppDepDescribeSpec, Unit>` to `Function1<AsgardDescribeSpec, Unit>` is an unchecked cast. At runtime, when the lambda body runs with an `AsgardDescribeSpec` receiver, the invocation site expects a `Function1<AsgardDescribeSpec, Unit>`. Because of JVM type erasure, the cast itself succeeds silently (no `ClassCastException` at the cast site). But if the compiler-generated bridge method or a reflective invocation checks the actual receiver type against `SharedAppDepDescribeSpec`, a `ClassCastException` can be thrown at runtime.

In practice, Kotest invokes the body lambda via its `DescribeSpec` superclass which passes `this` (a `SharedAppDepDescribeSpec` instance, which IS an `AsgardDescribeSpec`) as the receiver. Since `SharedAppDepDescribeSpec` extends `AsgardDescribeSpec`, the actual runtime receiver satisfies both types. So the cast does not blow up in practice.

However, the `@Suppress("UNCHECKED_CAST")` annotation signals that the compiler itself has concerns. A safer and idiomatic alternative that avoids the cast entirely is to accept `AsgardDescribeSpec.() -> Unit` as the parameter type and expose `appDependencies` via a different mechanism (e.g., a companion object accessor or a top-level `val` in the package). The plan document itself acknowledges the receiver type trick in Phase 3.

**Concrete alternative** — if `appDependencies` is needed in the lambda body without `SharedAppDepIntegFactory.appDependencies` qualification, a cleaner approach is:

```kotlin
// In the lambda body, appDependencies is just a property reference.
// Since AsgardDescribeSpec bodies are executed with `this` = the spec instance,
// and the spec instance is a SharedAppDepDescribeSpec which has `appDependencies`,
// the lambda body can always cast `this` or call SharedAppDepIntegFactory directly.
```

The simplest correct fix that eliminates the suppress:

```kotlin
abstract class SharedAppDepDescribeSpec(
    body: AsgardDescribeSpec.() -> Unit,
    config: SharedAppDepSpecConfig = SharedAppDepSpecConfig(),
) : AsgardDescribeSpec(body, config.asgardConfig) {
    val appDependencies: AppDependencies = SharedAppDepIntegFactory.appDependencies
}
```

This removes the body receiver type trick entirely. The `appDependencies` property is still accessible in the lambda body because `this` at lambda invocation time is the `SharedAppDepDescribeSpec` instance (which carries the property), but Kotlin's type checker for the lambda won't auto-resolve it — callers would need to write `(this as SharedAppDepDescribeSpec).appDependencies` or use `SharedAppDepIntegFactory.appDependencies` directly.

The actual usage in `SpawnTmuxAgentSessionUseCaseIntegTest.kt` accesses `appDependencies` (line 33) without qualification, which works because the lambda receiver is declared as `SharedAppDepDescribeSpec.() -> Unit` — that's where the value of the current trick comes from. The actual lambda invocation flow is: Kotest calls `body(this)` where `this` is a `SharedAppDepDescribeSpec` instance; since the lambda was declared with `SharedAppDepDescribeSpec` receiver, the property resolves cleanly.

**Verdict:** The cast works at runtime. However, the `@Suppress("UNCHECKED_CAST")` is a code smell and signals that this is not the idiomatic Kotlin approach. Document this clearly or consider whether the convenience is worth the suppression. If the team prefers the current approach, the comment should be expanded to explain the invocation flow more precisely (i.e., that Kotest always calls `body(this)` where `this` is the spec instance, which is always a `SharedAppDepDescribeSpec`).

---

### 3. Logging Violation in `SpawnTmuxAgentSessionUseCaseIntegTest` — Embedded Values in Log String

`app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt` lines 83-86:
```kotlin
val message =
    "Spawned tmux session [${agentSession.tmuxSession.name}] with GUID [${agentSession.resumableAgentSessionId.sessionId}]"
println(message)
out.info(message)
```

Two violations here:

1. **Values embedded in message string.** Per `3_kotlin_standards.md` and `out_logging_patterns.md`, structured values must be passed via `Val(value, ValType.SPECIFIC_TYPE)` — never embedded in the message string. The correct form:
   ```kotlin
   out.info("spawned_tmux_session",
       Val(agentSession.tmuxSession.name, ValType.TMUX_SESSION_NAME),
       Val(agentSession.resumableAgentSessionId.sessionId, ValType.SESSION_ID),
   )
   ```

2. **`println` used for logging.** `println` is allowed only for user communication, not logging. The `out.info` call already logs this — the `println` call is redundant and violates the rule. If the intent is that this information also appears to the developer during local test runs, the `out` logging system already handles that via `TestOutManager`. Remove `println`.

Note: this violation existed before the current ticket. The migration to `SharedAppDepDescribeSpec` touched this file, making it in scope for cleanup. The plan explicitly noted "Leave the existing test structure intact. Do NOT split assertions unless specifically asked." — however, these are logging violations, not structural test issues. They should be fixed as part of touching the file.

---

## Suggestions

### S1. `SharedAppDepIntegFactory` Properties Visibility — Should Be `private`

`app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedAppDepIntegFactory.kt` lines 30-31:
```kotlin
val testOutManager: TestOutManager = TestOutManager.standard()
val appDependencies: AppDependencies = runBlocking { ... }
```

Both `testOutManager` and `appDependencies` are public. The KDoc correctly guides callers to use `SharedAppDepDescribeSpec` instead of accessing the factory directly. Making these `private` would enforce this guidance at compile time. `buildDescribeSpecConfig()` and `appDependencies` are accessed from `SharedAppDepDescribeSpec` in the same package, so `internal` visibility would be the appropriate choice.

If `internal` or `private` is used, `SharedAppDepDescribeSpec` (same package) would still have access with `internal`. This would prevent tests from bypassing `SharedAppDepDescribeSpec` and accessing the factory directly.

**Suggested change:**
```kotlin
object SharedAppDepIntegFactory {
    internal val testOutManager: TestOutManager = TestOutManager.standard()
    internal val appDependencies: AppDependencies = runBlocking { ... }
    internal fun buildDescribeSpecConfig(): AsgardDescribeSpecConfig = ...
}
```

### S2. `CallGLMApiSandboxMain.kt` Resource Leak

`app/src/main/kotlin/com/glassthought/chainsaw/cli/sandbox/CallGLMApiSandboxMain.kt` line 8:
```kotlin
val llm = Initializer.standard().initialize(outFactory = SimpleConsoleOutFactory.standard()).glmDirectLLM
```

The `AppDependencies` instance is discarded immediately without calling `close()`. This was a pre-existing issue exposed by the Phase 1 refactor. For a sandbox/throwaway main, this is low-severity (JVM exit cleans up), but it is inconsistent with the `.use{}` pattern used in `AppMain.kt`. Consider:
```kotlin
Initializer.standard()
    .initialize(outFactory = SimpleConsoleOutFactory.standard())
    .use { deps ->
        deps.glmDirectLLM.call(...).also { ... }
    }
```

This is not introduced by the current ticket but was touched as part of it.

### S3. `appDependencies` as a `val` Field Stores the Same Reference as Factory

`SharedAppDepDescribeSpec` line 67:
```kotlin
val appDependencies: AppDependencies = SharedAppDepIntegFactory.appDependencies
```

This stores the same singleton reference in each test class instance. It is functionally equivalent to `get() = SharedAppDepIntegFactory.appDependencies` but slightly different in semantics — a `val` stores the reference at construction time, while a `get()` re-reads it each access. For a singleton that never changes, both are identical. The `val` approach is fine but the comment in the code could make this explicit. No action required.

---

## Requirements Checklist

| Requirement | Status |
|---|---|
| `SharedAppDepIntegFactory` exists as object singleton | PASS |
| `SharedAppDepDescribeSpec` extends `AsgardDescribeSpec` | PASS |
| `SharedAppDepSpecConfig` exists with defaults from factory | PASS |
| `Initializer.initialize()` takes `outFactory` as required parameter | PASS |
| `SpawnTmuxAgentSessionUseCaseIntegTest` uses `SharedAppDepDescribeSpec` | PASS |
| `ai_input` memory updated with reference to `SharedAppDepDescribeSpec` | PASS |
| Anchor point `ap.20lFzpGIVAbuIXO5tUTBg.E` on `SharedAppDepDescribeSpec` | PASS |
| All callers of `Initializer.initialize()` updated | PASS (`AppMain.kt`, `CallGLMApiSandboxMain.kt`) |
| Unit tests pass | PASS |
| `sanity_check.sh` passes | PASS |

---

## Files Reviewed

- `app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedAppDepIntegFactory.kt`
- `app/src/test/kotlin/com/glassthought/chainsaw/integtest/SharedAppDepDescribeSpec.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/Initializer.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/cli/AppMain.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/cli/sandbox/CallGLMApiSandboxMain.kt`
- `app/src/test/kotlin/org/example/SpawnTmuxAgentSessionUseCaseIntegTest.kt`
- `app/src/test/kotlin/org/example/TmuxCommunicatorIntegTest.kt`
- `app/src/test/kotlin/org/example/TmuxSessionManagerIntegTest.kt`
- `ai_input/memory/auto_load/4_testing_standards.md`
