# DirectLLM — Deferred to V2 / ap.hnbdrLkRtNSDFArDFd9I2.E

> **Deferred to V2.** DirectLLM has zero V1 consumers:
> - Title compression: replaced by deterministic slug truncation algorithm.
> - `LlmUserQuestionHandler`: V2+ strategy (ref.ap.NE4puAzULta4xlOLh5kfD.E), V1 uses
>   `StdinUserQuestionHandler` only.
>
> Deferring eliminates from V1: the DirectLLM interface, Anthropic-compatible API data classes
> (`kotlinx.serialization`), LLM service wiring in `ContextInitializer`, LLM credentials
> requirement, LLM API error handling, and the `glm` package.

**Full V2 spec:** [`doc_v2/DirectLLM.md`](../../doc_v2/DirectLLM.md)
