---
id: nid_o9j8yo1yf76iwrj1x12u19t0z_E
title: "Implement FailedToConvergeUseCase"
status: in_progress
deps: [nid_smb6zudqraf0hkp3u9kjx855e_E]
links: []
created_iso: 2026-03-18T17:36:44Z
status_updated_iso: 2026-03-19T16:58:30Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [health-monitoring, use-case]
---

## Context

Spec: `doc/use-case/HealthMonitoring.md` (ref.ap.RJWVLgUGjO5zAwupNLhA0.E), section "FailedToConvergeUseCase Detail" (lines 216-232).

When the reviewer sends `needs_iteration` but the iteration counter exceeds `iteration.max`, the harness must ask the operator whether to continue or abort.

## What to Implement

### 1. FailedToConvergeUseCase interface + implementation
```kotlin
interface FailedToConvergeUseCase {
    /**
     * Returns true if the user granted more iterations, false if they chose to abort.
     */
    suspend fun askForMoreIterations(currentMax: Int, iterationsUsed: Int): Boolean
}
```

### 2. Behavior
- Display binary y/N prompt to the user:
  ```
  Iteration budget exhausted (N/M). Grant 2 more iterations? [y/N]
  ```
- User input:
  - `y` → return true (caller increments `iteration.max += failedToConvergeIterationIncrement`)
  - `N` or timeout → return false (caller returns `PartResult.FailedToConverge`)
- `failedToConvergeIterationIncrement` (default: 2) is a named constant in `HarnessTimeoutConfig`
  - NOTE: This constant does NOT currently exist in HarnessTimeoutConfig — add it.

### 3. Design Rationale (from spec)
- Fixed increment (not variable input) eliminates parsing edge cases
- Binary choice is faster under pressure than open-ended input
- Fixed increment prevents runaway resource consumption

### 4. Edge Case: FailedToConvergeUseCase and Health Loop
- When the executor presents convergence failure to user and waits for input, the health-aware await loop is NOT running
- The executor is in a synchronous decision path — no spurious pings or crash declarations

### 5. Unit Tests (BDD/DescribeSpec)
- GIVEN iteration budget exhausted WHEN user enters y THEN returns true
- GIVEN iteration budget exhausted WHEN user enters N THEN returns false
- GIVEN iteration budget exhausted WHEN user input times out THEN returns false
- GIVEN iteration budget exhausted THEN prompt displays correct iteration counts

## Package
`com.glassthought.shepherd.usecase.healthmonitoring`

## Dependencies
- `HarnessTimeoutConfig.failedToConvergeIterationIncrement` (new field, default: 2)
- Console I/O for y/N prompt (should be injectable for testing)

## Acceptance Criteria
- FailedToConvergeUseCase interface + implementation
- failedToConvergeIterationIncrement added to HarnessTimeoutConfig
- Binary y/N prompt with timeout behavior
- Unit tests for all branches (y, N, timeout)

