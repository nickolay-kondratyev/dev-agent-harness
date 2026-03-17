# DirectLLM — Tier-Scoped Interfaces / ap.hnbdrLkRtNSDFArDFd9I2.E

For harness-internal tasks (compress ticket title, suggest feature name, autonomous Q&A answers).
**Not used for iteration decisions** — the reviewer's `result` field is authoritative.

---

## Design: Interface-per-Tier

Each budget tier gets its own interface. The `ContextInitializer` (ref.ap.9zump9YISPSIcdnxEXZZX.E)
wires a concrete `DirectLLM` implementation to each tier interface — callers depend on the
tier interface, never on a specific model.

```kotlin
// Shared contract — all tiers implement this
interface DirectLLM {
    suspend fun call(request: ChatRequest): ChatResponse
}

// Tier interfaces — callers depend on these
interface DirectQuickCheapLLM : DirectLLM    // fast, low-cost tasks (title compression, slugification)
interface DirectBudgetHighLLM : DirectLLM    // expensive tasks (autonomous Q&A answers)
```

V1 has two tiers. A mid-tier interface can be added when a use case emerges (OCP —
add a new interface, no changes to existing callers).

---

## V1 Model Assignments

| Tier Interface | V1 Model | Provider | Typical Use |
|---|---|---|---|
| `DirectQuickCheapLLM` | **GLM-4.7-Flash** | Z.AI (GLM) | Title compression, feature name suggestion |
| `DirectBudgetHighLLM` | **GLM-5** | Z.AI (GLM) | `LlmUserQuestionHandler` (ref.ap.NE4puAzULta4xlOLh5kfD.E) autonomous Q&A answers |

Model assignments are configuration — changing the model behind a tier requires no code changes
outside the `ContextInitializer` (ref.ap.9zump9YISPSIcdnxEXZZX.E).

---

## JSON Serialization: `kotlinx.serialization`

`GlmAnthropicCompatibleApi` uses **`kotlinx.serialization`** with `@Serializable` data classes
for request/response JSON — not `org.json.JSONObject`.

**Why:** Compile-time field safety, idiomatic Kotlin, flat/readable structure, easy to extend
when the API schema evolves (add `system`, `temperature`, etc.).

### Request Body

```kotlin
@Serializable
data class AnthropicChatRequestBody(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<AnthropicMessage>,
)

@Serializable
data class AnthropicMessage(val role: String, val content: String)
```

Serialized via `Json.encodeToString(...)`. V1: single `user` message, no streaming.

### Response Parsing

```kotlin
@Serializable
data class AnthropicChatResponseBody(
    val content: List<AnthropicContentBlock>,
)

@Serializable
data class AnthropicContentBlock(val type: String, val text: String)
```

Deserialized via `Json { ignoreUnknownKeys = true }.decodeFromString(...)`.
`ignoreUnknownKeys` ensures forward compatibility when the API adds fields.

Validation: content array must be non-empty and first block must have `type == "text"`.

### Scope

These data classes are `internal` to the `glm` package — they model the Anthropic-compatible
wire format, not a shared domain concept. If a second provider appears with the same wire
format, the classes can be promoted to the `directLLMApi` package.
