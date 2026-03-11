# Implementation Review: Add `model` Field to `ResumableAgentSessionId`

## Verdict: pass

## Summary

The implementation correctly adds `val model: String` as the third field to `ResumableAgentSessionId`, threads it via constructor injection into `ClaudeCodeAgentSessionIdResolver`, and wires the model extraction in `ClaudeCodeAgentStarterBundleFactory`. The `AgentSessionIdResolver` interface is correctly left unchanged. All tests pass (`sanity_check.sh` green). The architecture decision (constructor injection rather than interface change) is honored.

## CRITICAL Issues

None.

## IMPORTANT Issues

### 1. `AgentSessionIdResolver` interface KDoc is now stale (non-blocking)

File: `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/sessionresolver/AgentSessionIdResolver.kt` line 22.

The `@return` doc says:
```
@return A [ResumableAgentSessionId] containing the agent type and session ID
```

`ResumableAgentSessionId` now also carries `model`. The doc should say:
```
@return A [ResumableAgentSessionId] containing the agent type, session ID, and model.
```

Small gap but this is the public interface doc and should stay accurate.

### 2. Integration test has multiple assertions in one `it` block (non-blocking, pre-existing pattern)

File: `app/src/test/kotlin/com/glassthought/bucket/SpawnTmuxAgentSessionUseCaseIntegTest.kt` lines 55-58.

The test has a comment explaining the justification (spawning Claude is expensive). Adding `agentSession.resumableAgentSessionId.model.shouldNotBeBlank()` alongside the existing assertions follows the pre-existing pattern in this block, so this is consistent and acceptable given the cost constraint. However, the comment on line 44-45 should be updated to cover the `model` assertion as well - currently it reads "Multiple assertions verify different facets of the same result" which does implicitly cover it, so this is a very minor nit.

### 3. `shouldNotBeBlank()` for model in the integration test is weak (non-blocking)

File: `app/src/test/kotlin/com/glassthought/bucket/SpawnTmuxAgentSessionUseCaseIntegTest.kt` line 58.

```kotlin
agentSession.resumableAgentSessionId.model.shouldNotBeBlank()
```

This only verifies the model is not blank, but does not verify it equals `"sonnet"` (the value set by the factory for test environments). A stronger assertion would be:
```kotlin
agentSession.resumableAgentSessionId.model shouldBe "sonnet"
```

That said, this requires knowing the expected model string from the integration test context. If the test inherits the test environment (`environment.isTest == true`), then `TEST_MODEL = "sonnet"` is the expected value. Using `shouldNotBeBlank()` is a valid soft assertion, but it would pass even for an empty whitespace string in theory (though `shouldNotBeBlank` correctly catches that). The assertion is sufficient for the ticket's stated goal.

## Suggestions

### A. `SpawnTmuxAgentSessionUseCase` logging does not include `model` after spawn

File: `app/src/main/kotlin/com/glassthought/ticketShepherd/core/useCase/SpawnTmuxAgentSessionUseCase.kt` lines 93-97.

The `agent_session_spawned` log line logs `sessionId.sessionId` and `sessionId.agentType.name` but skips `sessionId.model`. For observability, including the model would be useful - especially for future resume support. This is pre-existing structure (model field didn't exist before) and is a nice-to-have follow-up.

```kotlin
out.info(
    "agent_session_spawned",
    Val(sessionId.sessionId, ValType.STRING_USER_AGNOSTIC),
    Val(sessionId.agentType.name, ValType.STRING_USER_AGNOSTIC),
    Val(sessionId.model, ValType.STRING_USER_AGNOSTIC),  // add this
)
```

### B. `TEST_MODEL` and `PRODUCTION_MODEL` are identical (pre-existing, not in scope)

File: `app/src/main/kotlin/com/glassthought/ticketShepherd/core/agent/impl/ClaudeCodeAgentStarterBundleFactory.kt` lines 75-78.

```kotlin
private const val TEST_MODEL = "sonnet"
private const val PRODUCTION_MODEL = "sonnet"
```

Both constants are `"sonnet"`. When these diverge in the future, the model field will correctly carry the distinct value. No action needed now, but worth noting that the two-constant setup is ready for that case.

## Positives

- Architecture decision followed correctly: `AgentSessionIdResolver` interface unchanged, model injected via constructor - clean and consistent with codebase DI style.
- `ClaudeCodeAgentStarterBundleFactory` now cleanly extracts `val model` and reuses it for both `ClaudeCodeAgentStarter` and `ClaudeCodeAgentSessionIdResolver` - eliminates the risk of the two diverging.
- `ResumableAgentSessionId` KDoc updated with clear usage example (`claude --resume <sessionId> --model <model>`).
- `ClaudeCodeAgentSessionIdResolver` KDoc updated with `@param model` documentation.
- All 12 test constructor instantiations updated with `model = TEST_MODEL` using a distinct constant `"test-model-sonnet"` (not `"sonnet"`), which means unit test assertions are tight and would catch model field not being threaded correctly.
- `CountingFakeGuidScanner` and `withTempDir` helper remain focused and unmodified.
- Tests are well-structured with BDD GIVEN/WHEN/THEN style, one assert per `it` block in unit tests.
