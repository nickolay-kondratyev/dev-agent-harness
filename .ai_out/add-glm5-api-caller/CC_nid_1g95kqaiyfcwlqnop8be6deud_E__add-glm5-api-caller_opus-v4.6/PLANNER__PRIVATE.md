# Planner Private Notes: Add GLM5 API Caller

## Key Observations from Codebase Exploration

### Pattern Adherence Checklist
- [x] Constructor injection only (see TmuxSessionManager, TmuxCommunicatorImpl patterns)
- [x] Out/OutFactory logging with Val/ValType (see TmuxCommunicatorImpl for example)
- [x] Interface + Impl in same file (see TmuxCommunicator.kt)
- [x] AsgardDescribeSpec for tests (see TmuxSessionManagerIntegTest, AppTest)
- [x] Integration tests gated by isIntegTestEnabled() on describe block (see TmuxCommunicatorIntegTest)
- [x] afterEach cleanup in integration tests (see TmuxCommunicatorIntegTest)
- [x] @OptIn(ExperimentalKotest::class) for integ tests
- [x] outFactory inherited from AsgardDescribeSpec (NOT constructed manually)
- [x] snake_case log messages
- [x] AnchorPoint annotations on important classes

### ValType Constraint
ValType enum lives in asgardCore (submodule). We CANNOT add new entries there from this project.
Must use existing ValTypes:
- STRING_USER_AGNOSTIC -- for model name, status codes
- SERVER_URL_USER_AGNOSTIC -- for API endpoint
- QUERY -- for prompt (user-specific string)
- SERVER_RESPONSE_BODY -- for LLM response text
- DURATION -- could be useful for call timing if we add it

### JSON Library Decision Rationale
Considered three approaches:
1. **Manual string concat**: Dangerous for JSON escaping. Prompt could contain quotes, newlines, backslashes, Unicode.
2. **kotlinx.serialization**: Requires kotlin serialization Gradle plugin, @Serializable annotations, adds compile-time complexity. Overkill for two simple JSON structures.
3. **org.json**: ~80KB JAR, zero deps, handles escaping, well-known. Perfect for V1.

Went with org.json. If the project later needs heavier JSON support, kotlinx.serialization can be adopted then.

### Initializer Design Tradeoff
The key tension was: should Initializer eagerly create DirectLLM (requiring the env var always) or lazily?

Chose: Initializer provides a factory method `createGLMDirectLLM(outFactory)` that is called on-demand.
Reason: App.kt currently only uses tmux. Requiring Z_AI_GLM_API_TOKEN at startup would break the existing workflow for anyone without the token set.

### OkHttpClient Lifecycle
OkHttp recommends one shared client instance. In our case, each call to `createGLMDirectLLM` creates a new OkHttpClient. This is fine for V1 (one DirectLLM instance per run). If we later have multiple API callers, the OkHttpClient should be created once in Initializer and shared.

### Test Organization
Put tests under `com.glassthought.directLLMApi.glm` package (matching the source) rather than under `org.example` where existing tests live. The existing tests under `org.example` seem to be a legacy of the Gradle init template. New tests should follow the source package structure.

### What I Intentionally Left Out of V1
- Retry logic (exponential backoff)
- Timeout configuration (OkHttp defaults: 10s connect, 10s read, 10s write)
- Streaming support
- Multi-message conversations (only single user message)
- Token counting / rate limiting
- Response metadata (usage stats, finish_reason)

These are all follow-up items that can be added when needed.

### Risk: OkHttp Default Timeouts
OkHttp default timeouts are 10 seconds for connect/read/write. LLM API calls can take 30-60+ seconds for long responses. The implementor should consider setting a longer read timeout:
```kotlin
OkHttpClient.Builder()
    .readTimeout(120, TimeUnit.SECONDS)
    .build()
```
This should be called out in the implementation, possibly as a named constant.

### Build Verification
Confirmed current build compiles clean before starting the plan. Exit code 0 for `./gradlew :app:compileKotlin`.
