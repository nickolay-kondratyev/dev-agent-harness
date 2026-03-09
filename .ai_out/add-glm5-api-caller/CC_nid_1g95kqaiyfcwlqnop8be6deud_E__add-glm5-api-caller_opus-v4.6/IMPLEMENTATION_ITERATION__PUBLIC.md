# Implementation Iteration 2 - Review Feedback Fixes

## Changes Made

### 1. [MAJOR] Split multi-assertion test block (DONE)
**File**: `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiTest.kt`

Split the single `it("THEN request body contains the prompt as user message")` block (which had 3 assertions) into 3 focused `it` blocks:

- `it("THEN request body has exactly one message")` -- asserts `messages.length() shouldBe 1`
- `it("THEN request body message role is user")` -- asserts `message.getString("role") shouldBe "user"`
- `it("THEN request body message content matches prompt")` -- asserts `message.getString("content") shouldBe prompt`

Each has its own MockWebServer setup via `withFixture`. Total unit tests in this class: 12 (was 10).

### 2. [MINOR] MockWebServer cleanup on test failure (DONE)
**File**: `app/src/test/kotlin/com/glassthought/directLLMApi/glm/GLMHighestTierApiTest.kt`

Introduced a `withFixture` helper function:

```kotlin
suspend fun withFixture(block: suspend (TestFixture) -> Unit) {
    val fixture = createFixture()
    try {
        block(fixture)
    } finally {
        fixture.server.shutdown()
    }
}
```

All 12 `it` blocks now use `withFixture { fixture -> ... }` instead of manual `createFixture()` + `fixture.server.shutdown()`. This guarantees MockWebServer shutdown even when assertions fail, preventing port leaks.

### 3. OkHttpClient per call (SKIPPED)
V1 acceptable per review. The KDoc already warns callers to reuse the returned `DirectLLM` instance.

### 4-5. Suggestions (SKIPPED)
Response body ordering and connectTimeout are both acknowledged as fine for V1 by the reviewer.

## Test Results

All tests pass:
- 12 unit tests in `GLMHighestTierApiTest` (all pass)
- 1 integration test in `GLMHighestTierApiIntegTest` (skipped -- requires `-PrunIntegTests=true`)
- 23 total test cases across all test classes

```
BUILD SUCCESSFUL
```

## Commit
- SHA: `3bc4fbb` on branch `CC_nid_1g95kqaiyfcwlqnop8be6deud_E__add-glm5-api-caller_opus-v4.6`
