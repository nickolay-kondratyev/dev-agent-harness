# DirectLLM — Single Interface with Model Config / ap.hnbdrLkRtNSDFArDFd9I2.E

For harness-internal tasks (compress ticket title, suggest feature name, autonomous Q&A answers).
**Not used for iteration decisions** — the reviewer's `result` field is authoritative.

---

## Design: Single Interface, Configuration at Wiring Time

One interface. Callers receive the appropriately-configured instance via constructor injection
(DIP preserved). Model selection is a constructor parameter of the implementation — not a
type-level distinction.

```kotlin
interface DirectLLM {
    suspend fun call(request: ChatRequest): ChatResponse
}

// Callers get the right DirectLLM via named constructor params
class SomeUseCase(private val llm: DirectLLM)
```

The `ContextInitializer` (ref.ap.9zump9YISPSIcdnxEXZZX.E) wires concrete `DirectLLM`
implementations with the appropriate model config and injects each into its caller.
Adding a new model config = adding a new wiring binding, not a new interface (OCP).

---

## V1 Model Assignments

| Use case | V1 Model | Provider | Caller |
|---|---|---|---|
| Title compression, feature name suggestion | **GLM-4.7-Flash** | Z.AI (GLM) | *(not in active use after slug truncation moved to deterministic algorithm)* |
| Autonomous Q&A answers | **GLM-5** | Z.AI (GLM) | `LlmUserQuestionHandler` (ref.ap.NE4puAzULta4xlOLh5kfD.E) |

Model assignments are configuration — changing the model behind a use case requires no code
changes outside the `ContextInitializer` (ref.ap.9zump9YISPSIcdnxEXZZX.E).

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
