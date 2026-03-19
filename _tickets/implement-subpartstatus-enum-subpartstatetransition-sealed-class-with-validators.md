---
id: nid_smb6zudqraf0hkp3u9kjx855e_E
title: "Implement SubPartStatus enum + SubPartStateTransition sealed class with validators"
status: in_progress
deps: []
links: []
created_iso: 2026-03-18T18:02:12Z
status_updated_iso: 2026-03-19T01:37:17Z
type: task
priority: 1
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [plan-current-state, state-machine, foundational]
---

Implement the foundational state machine types from the plan-and-current-state spec (ref.ap.56azZbk7lAMll0D4Ot2G0.E).

## What to implement

### 1. SubPartStatus enum (spec lines 59-68)
```kotlin
enum class SubPartStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
}
```
Jackson `@JsonProperty` annotations for JSON serialization (values are upper-case in spec JSON examples: "NOT_STARTED", "IN_PROGRESS", etc.).

### 2. SubPartStateTransition sealed class (spec lines 89-135, ap.EHY557yZ39aJ0lV00gPGF.E)
Four entries: Spawn, Complete, Fail, IterateContinue. Each with KDoc from spec.

### 3. Validator extension functions (spec lines 137-183)
- `SubPartStatus.transitionTo(signal: AgentSignal): SubPartStateTransition` — maps (status, signal) to transition. Throws IllegalStateException on invalid pairs.
- `SubPartStatus.validateCanSpawn(): SubPartStateTransition.Spawn` — checks status == NOT_STARTED.

**IMPORTANT — AgentSignal canonical implementation:** This ticket MUST implement the canonical `AgentSignal` and `DoneResult` types. These are authoritatively specified in `doc/core/PartExecutor.md` (ref.ap.fFr7GUmCYQEV5SJi8p6AS.E) — read that spec and implement accordingly. The types live in the PartExecutor's package but are needed here for the validator functions. If the PartExecutor spec defines additional signal variants or fields beyond the below, include them.

Minimum shape (from plan-and-current-state spec):
```kotlin
sealed class AgentSignal {
    data class Done(val result: DoneResult) : AgentSignal()
    data class FailWorkflow(val reason: String) : AgentSignal()
    data class Crashed(val details: String) : AgentSignal()
    object SelfCompacted : AgentSignal()
}

enum class DoneResult { COMPLETED, PASS, NEEDS_ITERATION }
```
Check if AgentSignal already exists in codebase before creating.

### 4. Tests (BDD with Kotest DescribeSpec)
- Test every valid transition (4 entries)
- Test every invalid (status, signal) combination throws IllegalStateException
- Test validateCanSpawn success and failure cases
- Test that COMPLETED and FAILED are terminal (no further transitions)
- Data-driven tests for exhaustive coverage

## Package
`com.glassthought.shepherd.core.state` (new package for state machine types)

## Files to read
- `doc/schema/plan-and-current-state.md` (the authoritative spec)
- `doc/core/PartExecutor.md` (consumer of these types)
- `app/src/main/kotlin/com/glassthought/shepherd/core/data/` (existing data class patterns)
- `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/HandshakeGuid.kt` (anchor point pattern)


## Notes

**2026-03-19T00:58:01Z**

PartResult sealed class should be defined in this ticket alongside SubPartStatus and SubPartStateTransition. FailedToExecutePlanUseCase (nid_foubbnsh3vmk1fk34zm75zkg0_E) now depends on this ticket for PartResult.
