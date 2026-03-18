---
id: nid_83wpq96se2x8p43xyufkmcmzd_E
title: "SIMPLIFY_CANDIDATE: Defer DirectLLM to V2 — no V1 consumers remain"
status: in_progress
deps: []
links: []
created_iso: 2026-03-18T14:58:56Z
status_updated_iso: 2026-03-18T15:31:29Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [SIMPLIFY_CANDIDATE, spec, direct-llm, v1-scope]
---

## Problem
DirectLLM (doc/core/DirectLLM.md, ref.ap.hnbdrLkRtNSDFArDFd9I2.E) is a harness-internal LLM interface for non-iteration decisions. Its V1 model assignments are:

1. Title compression (GLM-4.7-Flash) — ALREADY REMOVED per spec: "unused after deterministic slug truncation"
2. LlmUserQuestionHandler (GLM-5) — listed as FUTURE STRATEGY in doc/core/UserQuestionHandler.md

With title compression removed and LlmUserQuestionHandler explicitly marked as future (V1 uses StdinUserQuestionHandler only), DirectLLM has ZERO V1 consumers.

## Proposal
Defer DirectLLM entirely to V2. Remove from V1 scope.

## What Gets Eliminated
- DirectLLM interface
- Anthropic-compatible API data classes (kotlinx.serialization)
- LLM service wiring in TicketShepherdCreator
- LLM credentials requirement for V1 deployment
- LLM API error handling
- glm package internal data classes

## Why More Robust
- Fewer external dependencies = fewer failure points
- No LLM API credentials needed for V1 (simpler deployment)
- No network calls to LLM services during harness execution
- Eliminates an entire class of transient failures (LLM API rate limits, timeouts, model unavailability)

## Why This Is Safe
- Both consumers are confirmed eliminated/deferred:
  - Title compression: spec explicitly states "unused after deterministic slug truncation"
  - LlmUserQuestionHandler: doc/core/UserQuestionHandler.md lists it under "Future strategies" not "V1"
- No other spec references DirectLLM as a V1 dependency

## Affected Specs
- doc/core/DirectLLM.md (archive or mark V2-only)
- doc/core/TicketShepherdCreator.md (remove DirectLLM wiring)
- doc/core/UserQuestionHandler.md (confirm LlmUserQuestionHandler is V2-only)

--------------------------------------------------------------------------------
CAPTURE what we currently described move to V2 and reference from high level using AP.