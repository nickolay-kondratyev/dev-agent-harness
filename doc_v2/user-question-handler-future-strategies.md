# UserQuestionHandler — Future Strategies (V2+)

> Extracted from `doc/core/UserQuestionHandler.md` (ref.ap.NE4puAzULta4xlOLh5kfD.E).
> V1 uses `StdinUserQuestionHandler` only. These are V2+ design candidates.

| Strategy | Description |
|----------|-------------|
| `StdinUserQuestionHandler` | V1 — human at terminal |
| `LlmUserQuestionHandler` | Route to a `DirectLLM` ([`doc_v2/DirectLLM.md`](DirectLLM.md), ref.ap.hnbdrLkRtNSDFArDFd9I2.E) configured with an expensive model for autonomous answers |
| `SlackUserQuestionHandler` | Post to Slack channel, wait for reply |
| `TimeoutWithFallbackHandler` | Wait N minutes for human, fall back to LLM |
