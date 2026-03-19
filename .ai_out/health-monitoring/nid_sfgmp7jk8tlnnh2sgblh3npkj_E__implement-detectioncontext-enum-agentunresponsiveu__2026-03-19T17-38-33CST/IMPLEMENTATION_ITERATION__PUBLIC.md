# Implementation Iteration: Review Feedback — DetectionContext + AgentUnresponsiveUseCase

**Date**: 2026-03-19

## Changes Made

### 1. Replaced `Pair` with `SendKeysCall` data class in test

**File**: `app/src/test/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AgentUnresponsiveUseCaseImplTest.kt`

Introduced `data class SendKeysCall(val paneTarget: String, val keys: String)` to replace `Pair<String, String>` in `SpyTmuxCommunicator`. Updated the assertion from `.first` to `.paneTarget` for clarity.

### 2. Changed `AgentUnresponsiveUseCase` to `fun interface`

**File**: `app/src/main/kotlin/com/glassthought/shepherd/usecase/healthmonitoring/AgentUnresponsiveUseCase.kt`

Changed `interface AgentUnresponsiveUseCase` to `fun interface AgentUnresponsiveUseCase` for consistency with peer single-method interfaces (`FailedToExecutePlanUseCase`, `SingleSessionKiller`, `AllSessionsKiller`).

### 3. Review item NOT addressed (intentionally): DRY in logging branches

The review flagged `NO_ACTIVITY_TIMEOUT` and `PING_TIMEOUT` logging branches as near-identical. The review itself concluded this is acceptable given the `when` exhaustiveness guarantee is more valuable than deduplicating two branches. No change made.

## Verification

- `./gradlew :app:test` passes (exit code 0).
