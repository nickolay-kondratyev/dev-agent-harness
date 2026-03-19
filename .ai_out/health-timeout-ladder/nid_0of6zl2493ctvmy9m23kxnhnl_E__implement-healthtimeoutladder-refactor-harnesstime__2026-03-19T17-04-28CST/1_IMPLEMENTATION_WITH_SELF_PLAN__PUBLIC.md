# Implementation: HealthTimeoutLadder + HarnessTimeoutConfig Refactor

## What Was Done

1. **Created `HealthTimeoutLadder` data class** in `HarnessTimeoutConfig.kt` — groups the three health-check timeouts (startup, normalActivity, pingResponse) into a single cohesive type matching the spec at ref.ap.RJWVLgUGjO5zAwupNLhA0.E.

2. **Refactored `HarnessTimeoutConfig`** — replaced flat fields `startupAckTimeout`, `noActivityTimeout`, `pingTimeout` with a single `healthTimeouts: HealthTimeoutLadder` field. `healthCheckInterval` remains a separate field (polling interval, not a timeout ladder step).

3. **Updated `forTests()`** — uses `HealthTimeoutLadder(startup = 1.second, normalActivity = 5.seconds, pingResponse = 1.second)` as specified in the spec.

4. **Updated `HarnessTimeoutConfigTest`** — tests now access nested fields via `config.healthTimeouts.startup` etc. Added dedicated `HealthTimeoutLadder` default-values test group.

## Files Modified

- `app/src/main/kotlin/com/glassthought/shepherd/core/data/HarnessTimeoutConfig.kt` — added `HealthTimeoutLadder` data class, refactored `HarnessTimeoutConfig` to use it
- `app/src/test/kotlin/com/glassthought/shepherd/core/data/HarnessTimeoutConfigTest.kt` — updated all tests to use nested structure

## Test Results

All tests pass (`./gradlew :app:test` exit code 0).

## Notes

- No other callers referenced the flat fields — only the config definition and its test. Clean refactor.
- `forTests()` values updated to match spec: startup=1s, pingResponse=1s (previously 2s each).
