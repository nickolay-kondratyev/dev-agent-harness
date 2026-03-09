# Implementation Plan: Add GLM5 API Caller

## 1. Problem Understanding

**Goal**: Add a direct LLM API calling capability to the agent harness, starting with GLM-5 (Z.AI's highest-tier model). This is a simple V1 with no streaming -- just send a prompt string, get a response string.

**Key Constraints**:
- Constructor injection only, no DI framework
- Out/OutFactory for all logging, snake_case messages, Val/ValType
- Suspend functions for all I/O
- Tests use AsgardDescribeSpec with BDD GIVEN/WHEN/THEN
- Initializer must become the root of the dependency graph
- OkHttp as HTTP client
- KISS / Pareto -- simple V1

**Assumptions**:
- The GLM API follows the OpenAI-compatible chat completions format (messages array with role/content)
- For V1, we always use `role: "user"` with a single message
- No retry logic in V1 (follow-up ticket if needed)
- OkHttp is acceptable as a direct dependency (no need for it in the version catalog since this is an application, not a library)

---

## 2. High-Level Architecture

```
App.kt (main)
  |
  v
Initializer  <-- root of all DI wiring
  |-- creates OutFactory (SimpleConsoleOutFactory)
  |-- reads Config from Constants
  |-- reads Z_AI_GLM_API_TOKEN from env
  |-- creates OkHttpClient
  |-- creates GLMHighestTierApi(outFactory, okHttpClient, config, apiToken)
  |-- creates TmuxCommandRunner, TmuxCommunicator, TmuxSessionManager
  |
  v
DirectLLM (interface)
  ^
  |
GLMHighestTierApi (implementation)
  |-- OkHttpClient for HTTP calls
  |-- Config for model name
  |-- API token for auth
```

**Data Flow**:
1. Caller creates `ChatRequest("What is 2+2?")`
2. Calls `directLLM.call(request)` (suspend)
3. `GLMHighestTierApi` builds JSON request body, sends POST to Z.AI endpoint
4. Parses JSON response, extracts `choices[0].message.content`
5. Returns `ChatResponse("4")`

---

## 3. Implementation Phases

### Phase 1: Add OkHttp Dependency

**Goal**: Make OkHttp available to the project.

**File**: `app/build.gradle.kts`

**Key Steps**:
1. Add OkHttp implementation dependency: `implementation("com.squareup.okhttp3:okhttp:4.12.0")`
2. Add OkHttp MockWebServer test dependency: `testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")`
3. Verify build compiles

**Verification**: `./gradlew :app:compileKotlin` succeeds

---

### Phase 2: Create DirectLLM Interface and Data Classes

**Goal**: Define the API-agnostic interface for calling LLMs directly.

**File**: `app/src/main/kotlin/com/glassthought/directLLMApi/DirectLLM.kt`

**What to create**:

```kotlin
package com.glassthought.directLLMApi

/**
 * Request to a direct LLM API call.
 * V1: just a prompt string.
 */
data class ChatRequest(val prompt: String)

/**
 * Response from a direct LLM API call.
 * V1: just the response text.
 */
data class ChatResponse(val text: String)

/**
 * API-agnostic interface for calling LLMs directly (not via agents).
 *
 * Implementations handle the specific API protocol (authentication, request
 * format, response parsing) for a given LLM provider.
 */
interface DirectLLM {
    suspend fun call(request: ChatRequest): ChatResponse
}
```

**Key Decisions**:
- `ChatRequest` and `ChatResponse` are simple data classes with a single field each (KISS / V1)
- Interface is `suspend` because all implementations will do network I/O
- Interface, data classes, and contract all live in the same file (small surface, cohesive)

**Verification**: Build compiles, no tests needed yet for pure interface/data classes

---

### Phase 3: Adjust Constants for Direct API Clarity

**Goal**: Make it clear that the model name in config is for direct API calls, and store the API-wire value (`"glm-5"` lowercase) rather than the display name (`"GLM-5"`).

**File**: `app/src/main/kotlin/com/glassthought/Constants.kt`

**Changes**:
1. Rename `LLM_MODEL_NAME` to `DIRECT_LLM_API_MODEL_NAME` to clarify these are for direct API calls
2. Change the value from `"GLM-5"` (display name) to `"glm-5"` (API wire name)
3. Add a doc comment clarifying this is the model identifier sent to the API

**Updated structure**:
```kotlin
object Constants {
    /** Model identifiers as sent to the provider's API (wire format). */
    object DIRECT_LLM_API_MODEL_NAME {
        /** GLM highest-tier model identifier for the Z.AI chat completions API. */
        const val GLM_HIGHEST_TIER = "glm-5"
    }
    // ... rest unchanged
}
```

Also add the API endpoint as a constant:
```kotlin
object Z_AI_API {
    const val CHAT_COMPLETIONS_ENDPOINT = "https://api.z.ai/api/paas/v4/chat/completions"
    const val API_TOKEN_ENV_VAR = "Z_AI_GLM_API_TOKEN"
}
```

**Note**: `val` should become `const val` since these are compile-time string constants.

**Verification**: Build compiles, existing tests still pass

---

### Phase 4: Implement GLMHighestTierApi

**Goal**: Implement the DirectLLM interface for Z.AI's GLM model using OkHttp.

**File**: `app/src/main/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApi.kt`

**Constructor parameters**:
```kotlin
class GLMHighestTierApi(
    outFactory: OutFactory,
    private val httpClient: OkHttpClient,
    private val modelName: String,
    private val apiEndpoint: String,
    private val apiToken: String,
) : DirectLLM
```

**Key Implementation Details**:

1. **JSON Construction** -- Use manual string building. The request JSON is trivially simple (no nested dynamic structures beyond a single-element messages array). Adding kotlinx.serialization for this would be over-engineering.

   Request body structure:
   ```json
   {
     "model": "glm-5",
     "messages": [
       {"role": "user", "content": "<prompt>"}
     ]
   }
   ```

   **IMPORTANT**: The prompt string must be JSON-escaped. Use a simple escape utility method (handle `"`, `\`, newlines, tabs, and other control characters). This is the one spot where manual JSON is tricky -- do NOT skip this.

2. **HTTP Request**:
   - POST to `apiEndpoint`
   - Headers: `Authorization: Bearer $apiToken`, `Content-Type: application/json`
   - Use `withContext(Dispatchers.IO)` for the blocking OkHttp `execute()` call

3. **Response Parsing** -- Use `org.json.JSONObject` (comes with Android/JVM) or, better, since we want to stay minimal: parse the response JSON manually to extract `choices[0].message.content`.

   **Decision**: Use OkHttp's built-in approach. Since OkHttp does not include a JSON library, and we want to avoid adding another dependency for V1, use a focused JSON extraction utility. The response structure is predictable:
   ```json
   {
     "choices": [
       {
         "message": {
           "content": "response text here"
         }
       }
     ]
   }
   ```

   **Recommendation**: Add `org.json:json:20240303` as a lightweight dependency. It is a small, well-known library (the reference JSON implementation) that avoids the complexity of kotlinx.serialization while being safer than manual regex parsing. This is the 80/20 choice.

4. **Error Handling**:
   - Non-2xx status codes: throw a descriptive exception with status code and response body snippet
   - Missing `choices` or empty array: throw with the raw response for debugging
   - OkHttp network exceptions will naturally bubble up (do NOT catch-and-log per standards)

5. **Logging**:
   - `out.info("calling_direct_llm_api", ...)` before the call with model name
   - `out.info("direct_llm_api_response_received", ...)` after with response status/timing
   - `out.debug(...)` with request/response bodies (lazy, as these can be large)
   - Need new `ValType` entries: `LLM_MODEL_NAME`, `LLM_API_ENDPOINT`, `HTTP_STATUS_CODE`, `LLM_PROMPT` (user-specific), `LLM_RESPONSE_TEXT` (user-specific)

   **Note on ValType**: Since `ValType` is in `asgardCore` (a shared library in the thorg-root submodule), and we should NOT modify that submodule for this project, use existing ValTypes that fit:
   - Model name: `STRING_USER_AGNOSTIC`
   - API endpoint: `SERVER_URL_USER_AGNOSTIC`
   - Prompt: use `QUERY` (user-specific string, semantically close)
   - Response text: use `SERVER_RESPONSE_BODY`
   - HTTP status: `STRING_USER_AGNOSTIC` (status code as string)

6. **JSON Escape Utility**: Create a private companion object method or a small utility for escaping strings for JSON. This is critical for correctness.

**Verification**: Unit tests pass (Phase 6)

---

### Phase 5: Refactor Initializer and App.kt

**Goal**: Make Initializer the root of all dependency wiring. App.kt delegates to Initializer.

**Files**:
- `app/src/main/kotlin/com/glassthought/initializer/Initializer.kt`
- `app/src/main/kotlin/org/example/App.kt`

**Initializer Design**:

```kotlin
class Initializer {
    /**
     * Encapsulates all application-level dependencies created during initialization.
     * Created by [Initializer.initialize] and must be used within an [OutFactory.use] block.
     */
    data class AppDependencies(
        val outFactory: OutFactory,  // caller is responsible for .use{} lifecycle
        val directLLM: DirectLLM,
        val tmuxCommandRunner: TmuxCommandRunner,
        val tmuxCommunicator: TmuxCommunicator,
        val tmuxSessionManager: TmuxSessionManager,
    )

    /**
     * Creates all application dependencies.
     * The returned [AppDependencies.outFactory] must be closed by the caller via .use{}.
     *
     * @throws IllegalStateException if required environment variables are missing.
     */
    fun initialize(): AppDependencies {
        val outFactory = SimpleConsoleOutFactory.standard()
        val config = Constants.getConfigurationObject()

        val apiToken = System.getenv(Constants.Z_AI_API.API_TOKEN_ENV_VAR)
            ?: throw IllegalStateException(
                "Required environment variable [${Constants.Z_AI_API.API_TOKEN_ENV_VAR}] is not set"
            )

        val httpClient = OkHttpClient()

        val directLLM = GLMHighestTierApi(
            outFactory = outFactory,
            httpClient = httpClient,
            modelName = config.zAiGlmConfig.highestTier,
            apiEndpoint = Constants.Z_AI_API.CHAT_COMPLETIONS_ENDPOINT,
            apiToken = apiToken,
        )

        val commandRunner = TmuxCommandRunner()
        val communicator = TmuxCommunicatorImpl(outFactory, commandRunner)
        val sessionManager = TmuxSessionManager(outFactory, commandRunner, communicator)

        return AppDependencies(
            outFactory = outFactory,
            directLLM = directLLM,
            tmuxCommandRunner = commandRunner,
            tmuxCommunicator = communicator,
            tmuxSessionManager = sessionManager,
        )
    }
}
```

**App.kt Changes**:
- Remove direct construction of OutFactory, TmuxCommandRunner, etc.
- Delegate to `Initializer().initialize()`
- Use the returned `AppDependencies`

```kotlin
fun main() {
    val deps = Initializer().initialize()
    runBlocking {
        deps.outFactory.use { _ ->
            // ... existing tmux session logic using deps.tmuxSessionManager
        }
    }
}
```

**Key Decision**: `Initializer.initialize()` is NOT a suspend function. It performs no I/O -- it just wires constructors. The `OutFactory.use{}` lifecycle is managed by the caller (App.kt's main). This keeps Initializer testable without coroutines.

**Edge Case**: If `Z_AI_GLM_API_TOKEN` is not set, initialization fails fast with a clear message. This is correct for a CLI tool -- we do NOT silently fall back to a no-op.

**WAIT -- Reconsider**: The current App.kt does not use DirectLLM at all. It only uses tmux. If we require `Z_AI_GLM_API_TOKEN` at startup, the existing tmux workflow breaks for anyone who has not set the env var.

**Resolution**: Make the API token optional at initialization. If not set, `directLLM` is `null` in `AppDependencies` (or use a lazy initialization pattern). The caller who needs DirectLLM will fail at call time, not at initialization.

**Revised approach**:
- `AppDependencies.directLLM` should be typed as `DirectLLM?` (nullable), OR
- Better: keep `Initializer.initialize()` creating only the always-needed deps, and add a separate factory method like `createDirectLLM()` that throws if token is missing

**Best approach (KISS)**: Make `directLLM` a `lazy` property that throws on first access if the env var is missing. But data classes cannot have lazy properties.

**Final approach**: Use a separate `DirectLLMFactory` that Initializer includes, and call it when you need it. But this feels over-engineered for V1.

**Simplest viable approach**: Since App.kt does not currently use DirectLLM, and the ticket is about *adding* the capability (not integrating it into the tmux workflow), we should:
1. Make Initializer the root for OutFactory and tmux deps
2. Have Initializer provide a method to create DirectLLM that reads the token and throws if missing
3. App.kt only calls this when it needs DirectLLM (which is not yet -- that is a separate integration step)

```kotlin
class Initializer {
    data class AppDependencies(
        val outFactory: OutFactory,
        val tmuxCommandRunner: TmuxCommandRunner,
        val tmuxCommunicator: TmuxCommunicator,
        val tmuxSessionManager: TmuxSessionManager,
    )

    fun initialize(): AppDependencies { /* wire tmux deps */ }

    /**
     * Creates a DirectLLM instance configured for Z.AI GLM highest-tier model.
     *
     * @param outFactory The OutFactory for logging (must come from [AppDependencies]).
     * @throws IllegalStateException if Z_AI_GLM_API_TOKEN environment variable is not set.
     */
    fun createGLMDirectLLM(outFactory: OutFactory): DirectLLM {
        val config = Constants.getConfigurationObject()
        val apiToken = System.getenv(Constants.Z_AI_API.API_TOKEN_ENV_VAR)
            ?: throw IllegalStateException(
                "Required environment variable [${Constants.Z_AI_API.API_TOKEN_ENV_VAR}] is not set"
            )
        return GLMHighestTierApi(
            outFactory = outFactory,
            httpClient = OkHttpClient(),
            modelName = config.zAiGlmConfig.highestTier,
            apiEndpoint = Constants.Z_AI_API.CHAT_COMPLETIONS_ENDPOINT,
            apiToken = apiToken,
        )
    }
}
```

**Verification**: Build compiles, existing tests pass, App.kt still runs the tmux workflow correctly

---

### Phase 6: Write Unit Tests for GLMHighestTierApi

**Goal**: Test the HTTP request construction and response parsing using MockWebServer.

**File**: `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiTest.kt`

**Test Structure** (BDD, one assert per `it`):

```
describe("GIVEN GLMHighestTierApi with MockWebServer") {
    // Setup: MockWebServer, OkHttpClient pointing to mock, GLMHighestTierApi instance

    describe("WHEN call is made with a simple prompt") {
        // Enqueue a successful mock response

        it("THEN request method is POST") { ... }
        it("THEN request has correct Authorization header") { ... }
        it("THEN request has correct Content-Type header") { ... }
        it("THEN request body contains the model name") { ... }
        it("THEN request body contains the prompt text") { ... }
        it("THEN response text matches the mock response content") { ... }
    }

    describe("WHEN prompt contains special characters needing JSON escaping") {
        // Prompt with quotes, newlines, backslashes

        it("THEN request body is valid JSON") { ... }
        it("THEN response is returned successfully") { ... }
    }

    describe("WHEN API returns non-2xx status") {
        // Enqueue 500 response

        it("THEN throws exception with status code information") { ... }
    }

    describe("WHEN API returns malformed JSON") {
        // Enqueue 200 with bad body

        it("THEN throws exception indicating parse failure") { ... }
    }
}
```

**Key Details**:
- Use `okhttp3.mockwebserver.MockWebServer` for testing
- MockWebServer provides a local HTTP server -- no real network calls
- `outFactory` is inherited from `AsgardDescribeSpec`
- Parse the recorded request to verify headers and body
- For the JSON escape test, include a prompt like: `He said "hello\nworld"`

---

### Phase 7: Write Integration Test for Real API Call

**Goal**: Verify the real API works end-to-end (guarded by `isIntegTestEnabled()`).

**File**: `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiIntegTest.kt`

**Test Structure**:
```
@OptIn(ExperimentalKotest::class)
class GLMHighestTierApiIntegTest : AsgardDescribeSpec({
    describe("GIVEN GLMHighestTierApi with real API").config(isIntegTestEnabled()) {
        // Setup: read token from env, create real OkHttpClient, real GLMHighestTierApi

        describe("WHEN calling with a simple math question") {
            it("THEN response is non-empty") { ... }
        }
    }
})
```

**Key Details**:
- Guarded by `isIntegTestEnabled()` so it only runs with `-PrunIntegTests=true`
- Use a deterministic prompt like "What is 2+2? Reply with just the number."
- Assert response is non-blank (do NOT assert exact content -- LLMs are non-deterministic)
- Read API token from `System.getenv("Z_AI_GLM_API_TOKEN")` -- fail explicitly if missing (this is an integ test, env must be set)

---

### Phase 8: Write Initializer Unit Test

**Goal**: Verify Initializer correctly wires dependencies.

**File**: `app/src/test/kotlin/com/glassthought/initializer/InitializerTest.kt`

**Test Structure**:
```
describe("GIVEN Initializer") {
    describe("WHEN initialize is called") {
        it("THEN AppDependencies is created with non-null outFactory") { ... }
        it("THEN AppDependencies contains tmuxSessionManager") { ... }
    }
}
```

**Verification**: Tests pass with `./gradlew :app:test`

---

## 4. Technical Considerations

### JSON Handling Decision

**Recommendation**: Use `org.json:json:20240303` (the JSON.org reference implementation).

**Rationale**:
- Manual string concatenation for JSON is error-prone (escaping!)
- kotlinx.serialization requires a Gradle plugin and annotation processing -- overkill for V1
- `org.json` is tiny (~80KB), has zero transitive deps, is well-known, and handles escaping correctly
- It provides `JSONObject` for both building requests and parsing responses

**Alternative considered**: Manual JSON with a custom escape function. Rejected because:
- Easy to get wrong with edge cases (Unicode, control characters)
- No value in reinventing what `org.json` does in one line

**Usage pattern**:
```kotlin
// Build request
val body = JSONObject().apply {
    put("model", modelName)
    put("messages", JSONArray().apply {
        put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)  // JSONObject handles escaping
        })
    })
}.toString()

// Parse response
val json = JSONObject(responseBody)
val content = json.getJSONArray("choices")
    .getJSONObject(0)
    .getJSONObject("message")
    .getString("content")
```

### Error Handling Strategy

- Network errors (OkHttp): Let `IOException` bubble up naturally
- HTTP errors (4xx/5xx): Throw `IllegalStateException` with status code and truncated body
- Parse errors (malformed response): Throw `IllegalStateException` with raw response snippet
- Missing env var: Throw `IllegalStateException` at construction time in `createGLMDirectLLM`
- Do NOT log-and-throw (per project standards)

### Threading / Coroutines

- OkHttp's `execute()` is a blocking call
- Wrap in `withContext(Dispatchers.IO)` inside the `call()` suspend function
- Do NOT use OkHttp's async `enqueue()` -- the suspend function already handles the threading correctly

### Performance

- Single `OkHttpClient` instance shared (OkHttp best practice -- connection pooling)
- No performance concerns for V1 (single sequential calls)

---

## 5. Testing Strategy

### Unit Tests (always run)
| Scenario | What it verifies |
|----------|-----------------|
| Successful call with mock | Request headers, body format, response parsing |
| Special characters in prompt | JSON escaping correctness |
| Non-2xx response | Error handling |
| Malformed response body | Parse error handling |
| Initializer wiring | Dependencies created correctly |

### Integration Tests (gated by `-PrunIntegTests=true`)
| Scenario | What it verifies |
|----------|-----------------|
| Real API call with simple prompt | End-to-end connectivity, auth, response format |

### Edge Cases to Consider
- Empty prompt string (should work -- the API decides what to do)
- Very long prompt (OkHttp handles this, but worth a data-driven test)
- Prompt with Unicode characters (JSON escaping must handle correctly)
- API returns empty choices array (should throw, not NPE)

---

## 6. File Summary

### Files to CREATE

| File | What |
|------|------|
| `app/src/main/kotlin/com/glassthought/directLLMApi/DirectLLM.kt` | `DirectLLM` interface, `ChatRequest`, `ChatResponse` data classes |
| `app/src/main/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApi.kt` | `GLMHighestTierApi` implements `DirectLLM` using OkHttp |
| `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiTest.kt` | Unit tests with MockWebServer |
| `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiIntegTest.kt` | Integration test for real API |
| `app/src/test/kotlin/com/glassthought/initializer/InitializerTest.kt` | Initializer wiring test |

### Files to MODIFY

| File | What |
|------|------|
| `app/build.gradle.kts` | Add OkHttp + org.json + MockWebServer deps |
| `app/src/main/kotlin/com/glassthought/Constants.kt` | Rename to `DIRECT_LLM_API_MODEL_NAME`, change to `const val`, add `Z_AI_API` object |
| `app/src/main/kotlin/com/glassthought/initializer/Initializer.kt` | Wire OutFactory, tmux deps, add `createGLMDirectLLM()` |
| `app/src/main/kotlin/org/example/App.kt` | Refactor to use `Initializer().initialize()` |

---

## 7. Acceptance Criteria

1. `./gradlew :app:test` passes all unit tests (no integ test flag needed)
2. `./gradlew :app:test -PrunIntegTests=true` passes integration test (requires `Z_AI_GLM_API_TOKEN` env var)
3. `GLMHighestTierApi` correctly sends POST request with proper auth, content-type, model name, and message format
4. `GLMHighestTierApi` correctly parses the response and returns `ChatResponse` with the content text
5. Special characters in prompts are properly JSON-escaped
6. Non-2xx responses result in descriptive exceptions
7. Initializer is the root of dependency wiring; App.kt delegates to it
8. All logging uses Out/Val/ValType (no println for logging)
9. No `Z_AI_GLM_API_TOKEN` env var required for normal startup (only when `createGLMDirectLLM` is called)

---

## 8. Recommended Implementation Order

1. Phase 1: Add dependencies to `build.gradle.kts`
2. Phase 2: Create `DirectLLM.kt` interface and data classes
3. Phase 3: Adjust `Constants.kt`
4. Phase 4: Implement `GLMHighestTierApi.kt`
5. Phase 6: Write unit tests (do this before Phase 5 to validate the implementation)
6. Phase 5: Refactor Initializer and App.kt
7. Phase 8: Write Initializer test
8. Phase 7: Write integration test (last, since it requires real API access)

---

## 9. Open Questions / Decisions Needed

None -- all clarifications were resolved in the CLARIFICATION phase. The plan is ready for implementation.

---

## 10. Dependencies to Add (exact coordinates)

```kotlin
// build.gradle.kts additions:
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.json:json:20240303")
testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```
