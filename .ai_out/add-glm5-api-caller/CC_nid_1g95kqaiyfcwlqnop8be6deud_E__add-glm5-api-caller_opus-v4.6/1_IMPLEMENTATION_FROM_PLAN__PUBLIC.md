# Implementation Results: Add GLM5 API Caller

## Summary

Successfully implemented the GLM-5 direct LLM API caller feature following the approved plan. All unit tests pass. The integration test is gated by `-PrunIntegTests=true` and requires the `Z_AI_GLM_API_TOKEN` environment variable.

## Files Created

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/directLLMApi/DirectLLM.kt` | `DirectLLM` interface, `ChatRequest`, `ChatResponse` data classes |
| `app/src/main/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApi.kt` | `GLMHighestTierApi` implements `DirectLLM` using OkHttp + org.json |
| `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiTest.kt` | Unit tests with MockWebServer (10 test cases) |
| `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiIntegTest.kt` | Integration test for real API (gated) |

## Files Modified

| File | Changes |
|------|---------|
| `app/build.gradle.kts` | Added OkHttp (`4.12.0`), org.json (`20240303`), MockWebServer dependencies |
| `app/src/main/kotlin/com/glassthought/Constants.kt` | Renamed `LLM_MODEL_NAME` to `DIRECT_LLM_API_MODEL_NAME`, changed value to `"glm-5"` (wire format), added `Z_AI_API` object, all constants use `const val` |
| `app/src/main/kotlin/com/glassthought/initializer/Initializer.kt` | Root of dependency wiring with `AppDependencies`, `initialize()`, and `createGLMDirectLLM()` |
| `app/src/main/kotlin/org/example/App.kt` | Delegates to `Initializer().initialize()` instead of constructing deps directly |

## Test Results

- **Unit tests**: 10 passed (GLMHighestTierApiTest), 1 passed (AppTest) = 11 passed
- **Integration tests**: 3 skipped (gated by `isIntegTestEnabled()`) -- TmuxSessionManagerIntegTest (2) + GLMHighestTierApiIntegTest (1)
- **Build**: clean success

## Reviewer Feedback Incorporated

1. **OkHttp read timeout >= 60s** -- Configured `readTimeout(60, TimeUnit.SECONDS)` in `Initializer.createGLMDirectLLM()`. Documented the constant with a comment explaining why.

2. **`response.use {}`** -- Used `response.use { }` block in `GLMHighestTierApi.call()` to close the response body and prevent connection leaks.

3. **Drop Initializer unit test** -- Dropped Phase 8. `Initializer.initialize()` is pure constructor wiring -- compiler catches type mismatches, and the GLMHighestTierApi unit tests validate the actual behavior.

4. **DispatcherProvider availability** -- `DispatcherProvider` in asgardCore requires `AppRuntime` which adds complexity not appropriate for V1. Used `Dispatchers.IO` directly with a comment noting that `DispatcherProvider` injection can be added if needed.

5. **AsgardBaseException** -- `AsgardBaseException` is available in asgardCore but uses structured `Val` values in constructors and ties into the full asgard logging ecosystem. For V1 simplicity, used `IllegalStateException` with descriptive messages. This is consistent with the existing tmux code patterns in this project.

## Implementation Decisions

1. **TestFixture pattern for MockWebServer**: Each `it` block creates a fresh `MockWebServer` via `createFixture()` because MockWebServer instances cannot be restarted after `shutdown()`. This ensures test isolation.

2. **`createGLMDirectLLM()` as separate method on Initializer**: The API token is only required when DirectLLM is actually used, not at startup. This preserves the existing tmux workflow for users who haven't set `Z_AI_GLM_API_TOKEN`.

3. **JSON handling with org.json**: Used `JSONObject`/`JSONArray` for both request construction and response parsing. Handles escaping correctly (quotes, newlines, tabs, backslashes, Unicode) without manual string manipulation.

4. **ValType choices**: Used existing `ValType` entries from asgardCore that semantically fit -- `STRING_USER_AGNOSTIC` for model name, `SERVER_URL_USER_AGNOSTIC` for endpoint, `JSON_SERVER_REQUEST` for request body, `SERVER_RESPONSE_BODY` for response text. No new `ValType` entries needed.

## Deviations from Plan

1. **No Phase 8 (Initializer unit test)**: Dropped per reviewer recommendation -- pure wiring tests have zero value when types are non-nullable.

2. **Test structure uses TestFixture**: The plan suggested a single MockWebServer with `afterEach` shutdown. This does not work because MockWebServer cannot be restarted. Used per-test fixture creation instead.

## Concerns / Follow-up Items

None blocking. The following are noted for future consideration:

- **OkHttpClient shutdown**: For a CLI app the client is GC'd on process exit. If this code moves to a long-running context, proper `dispatcher().executorService.shutdown()` + `connectionPool().evictAll()` should be added.
- **Retry logic**: V1 has no retry. If transient failures become an issue, add exponential backoff.
- **Streaming**: V1 is request/response only. Streaming support would require a different interface shape.
