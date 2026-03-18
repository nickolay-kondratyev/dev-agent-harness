---
id: nid_ii9c7haj2romerfy785dlob8a_E
title: "SIMPLIFY_CANDIDATE: Flatten DirectLLM tier interfaces into single DirectLLM with model config"
status: in_progress
deps: []
links: []
created_iso: 2026-03-18T00:06:22Z
status_updated_iso: 2026-03-18T13:43:42Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplify, spec-change]
---

## Current Design (ref.ap.hnbdrLkRtNSDFArDFd9I2.E)

Two marker interfaces extending `DirectLLM`:
- `DirectQuickCheapLLM` (GLM-4.7-Flash for title compression, slugification)
- `DirectBudgetHighLLM` (GLM-5 for autonomous Q&A answers)

Callers depend on tier interfaces, `ContextInitializer` wires concrete impls.

## Problem

Premature abstraction. V1 has exactly 2 concrete uses, both in harness-internal utilities. The tier concept assumes future mid-tier additions that may never materialize. Adding a new tier requires a new interface + new wiring — more ceremony than benefit.

## Proposed Simplification

Single `DirectLLM` interface. Callers receive the appropriately-configured instance via constructor injection (DIP preserved). Model selection is a constructor parameter of the implementation, not a type-level distinction.

```kotlin
// Before: 3 interfaces
interface DirectLLM { suspend fun call(request: ChatRequest): ChatResponse }
interface DirectQuickCheapLLM : DirectLLM
interface DirectBudgetHighLLM : DirectLLM

// After: 1 interface, configuration at wiring time
interface DirectLLM { suspend fun call(request: ChatRequest): ChatResponse }
// Callers get the right DirectLLM via named constructor params
class SomeUseCase(private val llm: DirectLLM)
```

## Why This Is Both Simpler AND More Robust

- Fewer types to maintain (1 vs 3)
- No risk of callers accidentally depending on wrong tier interface
- OCP preserved: adding a new model config = adding a new wiring binding, not a new interface
- Zero behavioral change — same models, same call semantics

## Spec Files to Update

- `doc/core/DirectLLM.md`
- `doc/high-level.md` (DirectLLM section)
- `doc/core/UserQuestionHandler.md` (LlmUserQuestionHandler reference)
- `doc/core/git.md` (DirectQuickCheapLLM reference in startup critical path note)

