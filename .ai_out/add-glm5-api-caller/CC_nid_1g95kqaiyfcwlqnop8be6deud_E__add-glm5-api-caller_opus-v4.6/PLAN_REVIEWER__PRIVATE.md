# Plan Reviewer Private Context

## Assessment: APPROVE_WITH_MINOR_ADJUSTMENTS

## Can PLAN_ITERATION Be Skipped?

**YES.** All feedback items are minor and can be incorporated by the implementor directly. No architectural changes needed.

## Key Implementation Notes for Implementor

### Must-Do (Non-Negotiable)

1. **OkHttp read timeout**: Default 10s will cause failures with LLM APIs. Set to at least 60s.
   ```kotlin
   OkHttpClient.Builder()
       .readTimeout(60, TimeUnit.SECONDS)
       .build()
   ```

2. **Close OkHttp responses**: Use `response.use { }` or `response.body?.use { }`. Failing to do this leaks connections.

3. **`const val`** on all string constants in `Constants.kt` -- both existing and new.

### Should-Do (Recommended)

4. **Skip Phase 8** (Initializer unit test). Pure constructor wiring tested by compiler. Zero value.

5. **Check for `DispatcherProvider`** in asgardCore before hardcoding `Dispatchers.IO`. If available, inject it.

6. **Check for `AsgardBaseException`** in asgardCore. If accessible, use it for `DirectLLMApiException` instead of `IllegalStateException`.

7. **Document OkHttpClient lifecycle**: Comment that process lifetime manages shutdown for CLI use case.

### Nice-to-Have

8. Grep for existing usages of `GLM_HIGHEST_TIER` and `Config.zAiGlmConfig` before renaming to confirm no breakage.

## Risk Assessment

- **Low risk**: This is additive -- new files, minimal changes to existing code. App.kt change is a refactor that does not change behavior.
- **Medium risk area**: JSON escaping correctness. The plan's choice of `org.json` mitigates this. MockWebServer tests with special characters will validate.
- **Integration test dependency**: Real API test needs `Z_AI_GLM_API_TOKEN` env var and network access. Properly gated.

## What I Verified

- Existing test patterns (`TmuxCommunicatorIntegTest`) match what the plan proposes
- Constructor injection pattern (`TmuxSessionManager`, `TmuxCommunicatorImpl`) matches plan's `GLMHighestTierApi`
- `outFactory.getOutForClass(X::class)` pattern is established and plan follows it
- `isIntegTestEnabled()` utility exists and plan uses it correctly
- Current `Constants.kt` uses `val` not `const val` -- plan correctly identifies this for improvement
- `Initializer.kt` is currently empty -- plan fills it appropriately
- No `AsgardCloseable` usage found in app module main sources -- confirms OkHttpClient cleanup is not an established pattern here
- `ValType.STRING_USER_AGNOSTIC`, `ValType.SHELL_COMMAND` are the established ValTypes in use -- plan's ValType choices are reasonable
