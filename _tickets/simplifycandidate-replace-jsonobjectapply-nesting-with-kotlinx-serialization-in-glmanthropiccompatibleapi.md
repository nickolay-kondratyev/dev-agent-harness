---
closed_iso: 2026-03-17T23:04:53Z
id: nid_ijwf9bbptrogesnrp1h2kbkhq_E
title: "SIMPLIFY_CANDIDATE: Replace JSONObject.apply nesting with Kotlinx Serialization in GlmAnthropicCompatibleApi"
status: closed
deps: []
links: []
created_iso: 2026-03-17T22:46:49Z
status_updated_iso: 2026-03-17T23:04:53Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, robustness, llm, serialization]
---

## Problem
`GlmAnthropicCompatibleApi.buildRequestBody()` at `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/directLLMApi/glm/GlmAnthropicCompatibleApi.kt` uses deeply nested JSONObject.apply{} chains:

```kotlin
return JSONObject().apply {
    put("model", modelName)
    put("max_tokens", maxTokens)
    put("messages", JSONArray().apply {
        put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })
    })
}.toString()
```

Problems:
- No compile-time safety (typos in field names are runtime bugs)
- Nesting is hard to read and fragile if API schema evolves
- JSONObject is not idiomatic Kotlin

## Proposed Simplification
Use `@Serializable` data classes + `kotlinx.serialization.json`:

```kotlin
@Serializable
data class ChatRequestBody(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<Message>
)

@Serializable
data class Message(val role: String, val content: String)

private fun buildRequestBody(prompt: String): String =
    Json.encodeToString(ChatRequestBody(modelName, maxTokens, listOf(Message("user", prompt))))
```

## Why This Improves Both
- **Simpler**: Flat, readable, idiomatic
- **More robust**: Compile-time field names; easy to extend when API adds fields (system prompt, temperature, etc.)
- **Consistent**: Matches how `kotlinx.serialization` is typically used in Kotlin projects

## Acceptance Criteria

- JSONObject.apply nesting replaced with @Serializable data classes
- Serialized output is functionally identical (verified by contract test or snapshot)
- No behavior change in API calls

---

## Resolution

**Spec updated** in `doc/core/DirectLLM.md`. Added new section "JSON Serialization: `kotlinx.serialization`" specifying:

1. **Request body** — `AnthropicChatRequestBody` + `AnthropicMessage` data classes with `@Serializable`, serialized via `Json.encodeToString(...)`.
2. **Response parsing** — `AnthropicChatResponseBody` + `AnthropicContentBlock` data classes, deserialized via `Json { ignoreUnknownKeys = true }.decodeFromString(...)`. Validation preserved (non-empty content, `type == "text"`).
3. **Scope** — classes are `internal` to the `glm` package.

Code implementation deferred to a separate follow-up (this ticket was spec-only per task instructions).
