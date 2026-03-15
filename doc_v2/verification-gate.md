# Verification Gate (Build/Test Reality Feedback Loop) — V2 / ap.y9S0ya1CmFFYCD0ysvDCH.E

> **V2 feature** — not implemented in V1. V1 relies solely on AI↔AI feedback (doer↔reviewer).
> The verification gate adds an AI↔Reality feedback loop: automated build/test/lint execution
> between doer completion and reviewer start.

## Motivation

V1's quality feedback is entirely AI-based: the doer produces code, the reviewer reviews it.
Neither step verifies whether the code **actually compiles or passes tests**. This means:

- Reviewer iterations are wasted on mechanically detectable issues (compile errors, type mismatches, broken tests).
- The system's quality floor depends entirely on probabilistic AI judgment — no deterministic checks.
- Trust in output is "AI says it's done" rather than "AI says it's done AND reality confirms it."

The verification gate closes this gap by running configurable verification commands after every
doer `done` signal, creating a **hard quality floor** that no amount of AI hallucination can subvert.

## High-Level Flow

```
Doer completes (done signal)
  → PUBLIC.md validation (existing, ref.ap.THDW9SHzs1x2JN9YP9OYU.E)
  → Git commit (existing, ref.ap.BvNCIzjdHS2iAP4gAQZQf.E)
  → VerificationGateUseCase.execute()
      ├─ Run configurable commands (build, test, lint, typecheck)
      ├─ ALL pass → advance to Reviewer
      ├─ FAIL → structured feedback → re-instruct doer (mini-loop, budget: verification.max)
      └─ Budget exhausted → PartResult.FailedVerification(details)
  → Reviewer starts (only after green gate)
```

## Design Points

### Configurable Per-Workflow

Verification commands defined in workflow JSON (or `plan.json` for with-planning workflows).
Different projects specify their own build/test/lint commands:

```json
{
  "verification": {
    "commands": [
      { "name": "build", "command": "./gradlew :app:build" },
      { "name": "test",  "command": "./gradlew :app:test" },
      { "name": "lint",  "command": "./gradlew :app:ktlintCheck" }
    ],
    "max_attempts": 3
  }
}
```

### Structured Failure Feedback

Raw build logs are not useful to agents. The gate parses output into structured items
(file, line, error message) that the doer can act on surgically. This is analogous to how
the granular feedback loop (ref.ap.5Y5s8gqykzGN1TVK5MZdS.E) structures reviewer feedback.

### Separate Iteration Budget

Verification retries do **not** consume the doer↔reviewer `iteration.max` budget.
A doer that takes 3 tries to get tests green but passes review on first try is a good outcome —
it saved expensive reviewer cycles. The verification budget (`verification.max`) is independent.

### Reviewer Gets Richer Context

When the reviewer starts, its instructions include verification results:
"verification gate passed: build ✅, 847 tests ✅, lint ✅". This lets the reviewer focus on
**design, logic, and maintainability** rather than mechanical correctness.

### Fits Existing Architecture

The gate is a step inside `DoerReviewerPartExecutor.execute()`
(ref.ap.mxIc5IOj6qYI7vgLcpQn5.E) between doer-done and reviewer-spawn.
No new communication channels, no architectural disruption. Uses existing `send-keys`
re-instruction for doer retries.

## Why V2 (Not V1)

- V1 focuses on establishing the core agent coordination loop. Adding execution-environment
  concerns (build tools, test runners, output parsing) significantly increases scope.
- Structured output parsing is project-specific and needs a plugin/adapter model to be useful
  across different tech stacks — that design work is better done after V1 stabilizes.
- V1's `PartResult` sealed class would need a new variant (`FailedVerification`), and the
  doer re-instruction path needs to handle a non-reviewer feedback source — both tractable
  but add complexity to the core loop that should stabilize first.

## Open Questions

- Should verification run after **every** doer iteration, or only after the first `done` signal
  (before reviewer starts)? Running every iteration catches regressions early but adds latency.
- Should the gate support **partial pass** (e.g., build passes but one test fails) with
  structured feedback, or is it all-or-nothing?
- How to handle flaky tests? A test that fails non-deterministically shouldn't block the doer
  indefinitely. Possible: configurable retry count per command, or a "known flaky" allowlist.
- Should verification results be persisted in `.ai_out/` for cross-try learning
  (ref.ap.cI3odkAZACqDst82HtxKa.E)?
