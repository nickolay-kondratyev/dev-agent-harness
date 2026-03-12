# DirectLLM — Tier-Scoped Interfaces / ap.hnbdrLkRtNSDFArDFd9I2.E

For harness-internal tasks (compress ticket title, suggest feature name, summarize convergence
failure state). **Not used for iteration decisions** — the reviewer's `result` field is
authoritative.

---

## Design: Interface-per-Tier

Each budget tier gets its own interface. The `Initializer` wires a concrete `DirectLLM`
implementation to each tier interface — callers depend on the tier interface, never on a
specific model.

```kotlin
// Shared contract — all tiers implement this
interface DirectLLM {
    suspend fun call(request: ChatRequest): ChatResponse
}

// Tier interfaces — callers depend on these
interface DirectQuickCheapLLM : DirectLLM    // fast, low-cost tasks (title compression, slugification)
interface DirectBudgetHighLLM : DirectLLM    // expensive tasks (convergence failure summarization)
```

V1 has two tiers. A mid-tier interface can be added when a use case emerges (OCP —
add a new interface, no changes to existing callers).

---

## V1 Model Assignments

| Tier Interface | V1 Model | Provider | Typical Use |
|---|---|---|---|
| `DirectQuickCheapLLM` | **GLM-4.7-Flash** | Z.AI (GLM) | Title compression, feature name suggestion |
| `DirectBudgetHighLLM` | **GLM-5** | Z.AI (GLM) | `FailedToConvergeUseCase` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E) state summarization |

Model assignments are configuration — changing the model behind a tier requires no code changes
outside the `Initializer`.
