# Implementation Review: AgentFacadeImplIntegTest

## Summary

New file: `app/src/test/kotlin/com/glassthought/shepherd/integtest/AgentFacadeImplIntegTest.kt` (407 lines).

End-to-end integration test for `AgentFacadeImpl` covering: spawn with HandshakeGuid/session ID validation, payload delivery with done signal, kill session, and context window state read. The test wires a real Ktor CIO server, real tmux session manager, and a GLM-backed Claude Code agent. A `ServerPortInjectingAdapter` decorator injects the dynamic server port and callback script PATH into the tmux command.

**Overall assessment**: Solid implementation. The test correctly exercises the full E2E chain, follows the existing `ClaudeCodeAdapterSpawnIntegTest` pattern, and is properly gated behind `isIntegTestEnabled()`. There are a few important issues and several suggestions below.

---

## CRITICAL Issues

None found.

---

## IMPORTANT Issues

### 1. SpawnAgentConfig duplication spawns a new agent per `it` block in the first `describe`

**File**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/AgentFacadeImplIntegTest.kt`, lines 147-172

The first `describe("WHEN spawnAgent is called with a valid config")` declares `spawnConfig` once, but each `it` block calls `facade.spawnAgent(spawnConfig)` independently. This means:
- Two separate GLM agents are spawned (costly, slow, flaky)
- The two `it` blocks test the **same** `spawnAgent` call but each spawns its own agent

Since these two assertions (`guid.value.shouldNotBeEmpty()` and `sessionId.sessionId.shouldNotBeEmpty()`) are testing the same operation, they should share a single spawn. The "one assert per test" principle does not require spawning a new real agent for each assertion in an integration test -- that is a unit test concern. For integration tests with expensive infrastructure, sharing a single spawn across related assertions is the pragmatic choice.

**Suggestion**: Either combine into one `it` block with two assertions (acceptable for integ tests), or use a `lateinit var` / lazy spawn shared across the two `it` blocks.

```kotlin
describe("WHEN spawnAgent is called with a valid config") {
    val spawnConfig = SpawnAgentConfig(...)

    lateinit var handle: SpawnedAgentHandle

    beforeEach {
        handle = facade.spawnAgent(spawnConfig)
        spawnedHandles.add(handle)
    }

    it("THEN the returned handle has a valid HandshakeGuid") {
        handle.guid.value.shouldNotBeEmpty()
    }

    it("THEN the returned handle has a resolved session ID") {
        handle.sessionId.sessionId.shouldNotBeEmpty()
    }
}
```

Actually, `beforeEach` would still spawn twice. The simplest fix is to combine both assertions into one `it` block since this is an integ test and the cost of spawning a real agent is high.

### 2. SpawnAgentConfig is duplicated 5 times with only `partName` differing

**File**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/AgentFacadeImplIntegTest.kt`, lines 148-157, 179-188, 211-220, 239-248

Four nearly identical `SpawnAgentConfig` instances are constructed. This violates DRY. While some duplication in tests is acceptable per CLAUDE.md, here the duplication is in *infrastructure boilerplate*, not in the *assertions* -- so it should be extracted.

**Suggestion**: Create a helper function at the `describe` scope:

```kotlin
fun buildSpawnConfig(partName: String) = SpawnAgentConfig(
    partName = partName,
    subPartName = "doer",
    subPartIndex = 0,
    agentType = AgentType.CLAUDE_CODE,
    model = "sonnet",
    role = "DOER",
    systemPromptPath = systemPromptFile.toPath(),
    bootstrapMessage = buildBootstrapMessage(),
)
```

### 3. Instruction file in payload test is not cleaned up on failure

**File**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/AgentFacadeImplIntegTest.kt`, lines 194-203

```kotlin
val instructionFile = createDoneInstructionFile()
val payload = AgentPayload(instructionFilePath = instructionFile.toPath())
val signal = facade.sendPayloadAndAwaitSignal(handle, payload)
signal.shouldBeInstanceOf<AgentSignal.Done>()
(signal as AgentSignal.Done).result shouldBe DoneResult.COMPLETED
instructionFile.delete()  // <-- never reached if assertion fails
```

If `sendPayloadAndAwaitSignal` times out or the assertion fails, `instructionFile.delete()` is never called. Use a `try/finally` or move the cleanup to `afterEach`.

### 4. Context window state test has a trivially-passing assertion

**File**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/AgentFacadeImplIntegTest.kt`, lines 235-257

The test uses a `stubContextWindowReader` that returns a hardcoded `ContextWindowState(remainingPercentage = null)`. Then the assertion is `state shouldNotBe null`. Since `readContextWindowState` returns a non-nullable `ContextWindowState`, this assertion can **never fail** -- it is tautological.

From the ticket requirements: "call readContextWindowState. Verify returns ContextWindowState." -- This is technically met, but the test adds no real value. It does not test the real `ContextWindowStateReader` and the assertion is vacuous.

**Suggestion**: Either use the real `ClaudeCodeContextWindowStateReader` (making this a real E2E test of context window reading), or if keeping the stub, at minimum assert on the returned value:

```kotlin
state.remainingPercentage shouldBe null  // explicitly documents stub behavior
```

### 5. Free-floating helper functions violate CLAUDE.md standards

**File**: `app/src/test/kotlin/com/glassthought/shepherd/integtest/AgentFacadeImplIntegTest.kt`, lines 261-407

CLAUDE.md states: "Disfavor non-private free-floating functions. Favor cohesive classes; for stateless utilities, use a static class."

The helper functions (`resolveCallbackScriptsDir`, `createIntegTestSystemPromptFile`, `buildBootstrapMessage`, `createDoneInstructionFile`) are `private` top-level functions. While `private` limits their scope to the file, grouping them in a companion object or a utility class would be more aligned with the standard. This is a minor point given they are `private`.

---

## Suggestions

### 1. Two assertions in the payload test can be combined

Lines 199-200:
```kotlin
signal.shouldBeInstanceOf<AgentSignal.Done>()
(signal as AgentSignal.Done).result shouldBe DoneResult.COMPLETED
```

The `shouldBeInstanceOf` is a Kotest matcher that already returns the cast type. Cleaner:
```kotlin
signal.shouldBeInstanceOf<AgentSignal.Done>().result shouldBe DoneResult.COMPLETED
```

### 2. Consider the race condition between port allocation and server start

Line 72:
```kotlin
val serverPort = ServerSocket(0).use { it.localPort }
```

The port is freed after `use {}` closes the `ServerSocket`. Another process could grab this port before `embeddedServer` binds to it. This is a known TOCTOU race. In practice this is unlikely in a test environment, but worth documenting with a comment.

### 3. `ServerPortInjectingAdapter` string manipulation is fragile

Lines 276-298: The adapter uses `indexOf("bash -c '")` to find the injection point. If `ClaudeCodeAdapter` ever changes its command format (e.g., uses double quotes or a different shell invocation), this will fail silently or produce a broken command. The `check` guards against the `indexOf` returning -1, which is good, but consider adding a brief comment about this coupling.

### 4. `afterSpec` cleanup could also clean up leftover handles

The `afterSpec` block (lines 140-143) stops the Ktor server and deletes the system prompt file, but does not iterate `spawnedHandles`. If the last test in the spec fails before `afterEach` runs for it, the handles may not be cleaned up. `afterEach` should cover it, but defensively killing remaining sessions in `afterSpec` would be more robust.

---

## Documentation Updates Needed

None required. The test file is self-documenting and the PUBLIC.md implementation notes are adequate.
