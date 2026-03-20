# Implementation Review: Pass Direct Callback Executable Path

## Summary

This change threads `CallbackScriptsDir` (which holds the validated temp directory path for callback scripts) through the instruction assembly pipeline so that all agent-facing instruction text now uses **full absolute paths** (e.g., `/tmp/shepherd-callback-scripts-XXX/callback_shepherd.signal.sh`) instead of bare script names that relied on PATH resolution. This fixes tmux sessions where PATH modifications are not inherited by agent subprocesses.

**Overall assessment**: The core change is correct, well-structured, and properly tested. The threading of `CallbackScriptsDir` through the dependency graph is clean. Tests pass (all 1482). There are two **IMPORTANT** missed spots in production code that still emit bare script names into agent-visible text, which undermines the purpose of this change.

---

## IMPORTANT Issues

### 1. MISSED: `AckedPayloadSender.wrapPayload()` still uses bare script name

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/server/AckedPayloadSender.kt` (lines 135-144)

```kotlin
fun wrapPayload(payloadId: PayloadId, payloadContent: String): String {
    val tag = ProtocolVocabulary.PAYLOAD_ACK_TAG
    val script = ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT  // <-- bare name "callback_shepherd.signal.sh"
    val signal = ProtocolVocabulary.Signal.ACK_PAYLOAD
    val ackCommand = "$script $signal $payloadId"
    // ...
}
```

This method constructs the `MUST_ACK_BEFORE_PROCEEDING` XML attribute that the agent receives and must execute verbatim. The attribute value will contain `callback_shepherd.signal.sh ack-payload <id>` -- the bare name without the absolute path. This is the **exact same PATH-resolution problem** this ticket is fixing.

Every payload delivery (work instructions, Q&A answers, iteration feedback, health pings) goes through this method. If PATH is not available, agents cannot acknowledge payloads, causing `PayloadAckTimeoutException` and session death.

**Fix**: Thread `callbackSignalScriptPath` into `AckedPayloadSenderImpl` (or make `wrapPayload` accept the path as a parameter) and use the full path in the ack command.

### 2. MISSED: `SubPartConfigBuilder.BOOTSTRAP_MESSAGE` still uses bare script name

**File**: `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/main/kotlin/com/glassthought/shepherd/core/executor/SubPartConfigBuilder.kt` (lines 132-136)

```kotlin
companion object {
    private const val BOOTSTRAP_MESSAGE =
        "Your FIRST action must be to call `callback_shepherd.signal.sh started` " +
        "using the Bash tool. This is CRITICAL -- do it immediately before anything else. " +
        "After that, wait for further instructions via payload delivery."
}
```

This bootstrap message is the **very first instruction** the agent receives. It tells the agent to call the bare script name `callback_shepherd.signal.sh started`. If PATH is not set up, the agent's first action fails and the session cannot complete the handshake.

**Fix**: Change `BOOTSTRAP_MESSAGE` from a `const` to a function/property that accepts the full signal script path, or thread `CallbackScriptsDir` into `SubPartConfigBuilder`.

### 3. DRY violation: Hardcoded test paths instead of using `ContextTestFixtures`

Multiple test files hardcode `"/tmp/test-callback-scripts/callback_shepherd.signal.sh"` and `CallbackScriptsDir.unvalidated("/tmp/test-callback-scripts")` instead of using the already-created `ContextTestFixtures.TEST_SIGNAL_SCRIPT_PATH` and `ContextTestFixtures.TEST_CALLBACK_SCRIPTS_DIR`.

Affected files:
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/compaction/SelfCompactionInstructionBuilderTest.kt` (line 15)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/executor/InnerFeedbackLoopTest.kt` (lines 170, 753, 792, 852)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorImplTest.kt` (lines 177, 998, 1013, 1078, 1092)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/executor/PartExecutorFactoryCreatorTest.kt` (line 90)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/usecase/planning/ProductionPlanningPartExecutorFactoryTest.kt` (line 82)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/TicketShepherdCreatorTest.kt` (line 245)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/creator/TicketShepherdCreatorTest.kt` (line 170)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/core/initializer/ShepherdInitializerTest.kt` (line 169)
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/src/test/kotlin/com/glassthought/shepherd/integtest/feedback/GranularFeedbackLoopIntegTest.kt` (line 222)

The fixture was created (`ContextTestFixtures.TEST_CALLBACK_SCRIPTS_DIR` and `TEST_SIGNAL_SCRIPT_PATH`) but only used in the context-layer tests. All executor-layer and other test files still inline the same string. If the test path ever needs to change, it must be updated in 15+ places.

**Fix**: Replace all hardcoded instances with `ContextTestFixtures.TEST_CALLBACK_SCRIPTS_DIR` and `ContextTestFixtures.TEST_SIGNAL_SCRIPT_PATH`.

### 4. Duplicated script name constants between `CallbackScriptsDir` and `ProtocolVocabulary`

The string `"callback_shepherd.signal.sh"` is now defined as a `private const` in **both**:
- `CallbackScriptsDir.REQUIRED_SCRIPT` (line 28)
- `ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT` (line 83)

Similarly `"callback_shepherd.query.sh"`:
- `CallbackScriptsDir.QUERY_SCRIPT` (line 29)
- `ProtocolVocabulary.CALLBACK_QUERY_SCRIPT` (line 86)

Now that instruction sections use `CallbackScriptsDir.signalScriptPath`/`queryScriptPath` instead of the `ProtocolVocabulary` constants, the `ProtocolVocabulary` constants' remaining production consumers are:
1. `AckedPayloadSender.wrapPayload()` -- which is itself a missed spot (issue #1 above)
2. `SubPartConfigBuilder.BOOTSTRAP_MESSAGE` -- also a missed spot (issue #2 above)

Once those two are fixed (using `CallbackScriptsDir` paths), the `ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT` and `CALLBACK_QUERY_SCRIPT` constants will have **zero production consumers** and should be either removed or deduplicated. Consider making `CallbackScriptsDir`'s constants public, or having `ProtocolVocabulary` delegate to them.

---

## Suggestions

### 1. `callback_shepherd.query.sh` does not exist as a resource script

The `CallbackScriptsDir` now has a `QUERY_SCRIPT = "callback_shepherd.query.sh"` constant and a `queryScriptPath` property, but there is no `callback_shepherd.query.sh` in `app/src/main/resources/scripts/`. Only `callback_shepherd.signal.sh` exists on the classpath. The `resolveCallbackScriptsDir()` in `ContextInitializerImpl` only extracts the signal script. The `validated()` factory only checks for `REQUIRED_SCRIPT` (signal), not `QUERY_SCRIPT`.

This means in production, `callbackScriptsDir.queryScriptPath` will point to a path that may not exist on disk. Currently this works because the query script is handled through a different mechanism (or not yet implemented), but the `queryScriptPath` property on `CallbackScriptsDir` implies a contract that the file exists. Consider either:
- Extracting the query script from resources too (if it exists elsewhere)
- Adding validation for the query script in `CallbackScriptsDir.validated()`
- Documenting that `queryScriptPath` is a convention-based path, not a validated one

### 2. `buildGitCommitStrategy` extraction is a good cleanup

The extraction of `buildGitCommitStrategy()` into a private method in `ProductionPartExecutorFactoryCreator` is a clean refactoring that reduces `create()` method length. This is good.

---

## What Went Well

- **Correct core approach**: Adding `signalScriptPath`/`queryScriptPath` to `CallbackScriptsDir` is the right place to derive full paths. Clean, validated, single source of truth.
- **Defense in depth preserved**: The PATH export in `ClaudeCodeAdapter` was correctly kept as a fallback.
- **`callbackHelpSection()` helper**: Good DRY extraction in `ContextForAgentProviderImpl` to avoid repeating the script path threading at every call site.
- **Test fixture `ContextTestFixtures.standardProvider()`**: Good DRY improvement for context-layer tests.
- **No behavioral regressions**: All existing tests pass. No tests were removed.
- **All 1482 tests pass**, 0 failures.

---

## Documentation Updates Needed

None required for CLAUDE.md.
